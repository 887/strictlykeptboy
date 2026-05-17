package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.DayBand
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

const val TestTagUnifiedTodayView = "UnifiedTodayView"
const val TestTagUnifiedTodaySection = "UnifiedTodaySection"
const val TestTagUnifiedTodayNextHint = "UnifiedTodayNextHint"

/**
 * Round 2.26.B.4 + C.4 — unified day-of feed renderer.
 *
 * Walks the [items] produced by [buildUnifiedToday], dispatches
 * [TaskRow] for [UnifiedTodayItem.TaskEntry] and [TimeboxRow] for
 * [UnifiedTodayItem.TimeboxEntry]. Section headers are emitted above
 * each non-empty bucket; a "next: in N minutes" subtitle (D-2.26.e
 * inline Now/Next replacement) is rendered above the first
 * future-keyed entry's first appearance.
 */
@Composable
fun TaskUnifiedTodayView(
    items: List<UnifiedTodayItem>,
    onTaskToggleDone: (TaskItem) -> Unit,
    onTaskOpen: (TaskItem) -> Unit,
    onTimeboxTap: (DayBand) -> Unit,
    modifier: Modifier = Modifier,
    onTaskLongPress: (TaskItem) -> Unit = {},
    multiRepo: Boolean = false,
    activeRepoOwner: String = "",
    onStartTask: ((String) -> Unit)? = null,
    onTaskRespond: ((TaskItem) -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
    now: ZonedDateTime = ZonedDateTime.now(),
) {
    if (items.isEmpty()) {
        EmptyTasksState(
            primaryMessage = stringResource(R.string.tasks_empty_today_primary),
            neutralMessage = stringResource(R.string.tasks_empty_today_neutral),
            modifier = modifier.testTag(TestTagUnifiedTodayView),
        )
        return
    }

    val sections = items.bySection(today)
    val overdueCount = sections[UnifiedTodaySection.Overdue].orEmpty().size
    val labelOverdue = pluralStringResource(R.plurals.tasks_unified_section_overdue, overdueCount, overdueCount)
    val labelToday = stringResource(R.string.tasks_unified_section_today)
    val labelPinned = stringResource(R.string.tasks_unified_section_pinned_standing)

    // C.4 — find the next future item across the whole list for the
    // inline "next: in N minutes" hint. We pick the first item whose
    // sortKey is strictly after `now`.
    val nextHint: String? = run {
        val nowOff = now.toOffsetDateTime()
        val next = items.firstOrNull { it.sortKey != null && it.sortKey!!.isAfter(nowOff) }
        next?.let {
            val minutes = Duration.between(nowOff, it.sortKey).toMinutes()
            when {
                minutes < 1 -> stringResource(R.string.tasks_unified_next_now)
                minutes < 60 -> pluralStringResource(R.plurals.tasks_unified_next_in_minutes, minutes.toInt(), minutes.toInt())
                else -> stringResource(R.string.tasks_unified_next_in_hours, minutes / 60, minutes % 60)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagUnifiedTodayView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        var renderedHint = false
        listOf(
            UnifiedTodaySection.Overdue to labelOverdue,
            UnifiedTodaySection.Today to labelToday,
            UnifiedTodaySection.PinnedStanding to labelPinned,
        ).forEach { (key, label) ->
            val bucket = sections[key] ?: return@forEach
            sectionHeader(label)
            if (!renderedHint && nextHint != null) {
                nextHintRow(nextHint)
                renderedHint = true
            }
            items(bucket.size, key = { idx -> stableKey(bucket[idx]) }) { idx ->
                when (val entry = bucket[idx]) {
                    is UnifiedTodayItem.TaskEntry -> TaskRow(
                        item = entry.task,
                        onToggleDone = { onTaskToggleDone(entry.task) },
                        onClick = { onTaskOpen(entry.task) },
                        onLongClick = { onTaskLongPress(entry.task) },
                        today = today,
                        multiRepo = multiRepo,
                        activeRepoOwner = activeRepoOwner,
                        onStartTask = onStartTask,
                        onRespond = onTaskRespond,
                    )
                    is UnifiedTodayItem.TimeboxEntry -> TimeboxRow(
                        band = entry.band,
                        onTap = { onTimeboxTap(entry.band) },
                    )
                }
            }
        }
    }
}

private fun stableKey(item: UnifiedTodayItem): String = when (item) {
    is UnifiedTodayItem.TaskEntry -> "task:${item.task.id}"
    is UnifiedTodayItem.TimeboxEntry -> "tb:${item.band.instance.instanceId}"
}

private fun LazyListScope.sectionHeader(label: String) {
    item(key = "section:$label") {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("$TestTagUnifiedTodaySection-$label"),
        )
    }
}

private fun LazyListScope.nextHintRow(hint: String) {
    item(key = "next-hint") {
        Text(
            text = hint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag(TestTagUnifiedTodayNextHint),
        )
    }
}

