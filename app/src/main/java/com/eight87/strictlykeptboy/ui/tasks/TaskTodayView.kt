package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import java.time.LocalDate

const val TestTagTodayView = "TodayView"
const val TestTagTodaySection = "TodaySection"

/** UI-J.2 — Today view with 3 sections: today/overdue, from-events, pinned. */
@Composable
fun TaskTodayView(
    tasks: List<TaskItem>,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onLongPress: (TaskItem) -> Unit = {},
    multiRepo: Boolean = false,
    activeRepoOwner: String = "",
) {
    val forToday = tasks.forToday(today)
    if (forToday.isEmpty()) {
        EmptyTasksState(
            primaryMessage = stringResource(R.string.tasks_empty_today_primary),
            neutralMessage = stringResource(R.string.tasks_empty_today_neutral),
            modifier = modifier.testTag(TestTagTodayView),
        )
        return
    }

    val dated = forToday.filter { !it.standing && it.source != TaskSource.FromEvents }
        .sortedForCombined(today)
    val fromEvents = forToday.filter { it.source == TaskSource.FromEvents }
        .sortedForCombined(today)
    val pinned = forToday.filter { it.standing && it.pinnedForToday }
        .sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.title.lowercase() })

    val labelToday = stringResource(R.string.tasks_today_section_today)
    val labelFromEvents = stringResource(R.string.tasks_today_section_from_events)
    val labelPinned = stringResource(R.string.tasks_today_section_pinned)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagTodayView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (dated.isNotEmpty()) {
            sectionHeader(labelToday)
            items(dated, key = { it.id }) { task ->
                TaskRow(
                    item = task,
                    onToggleDone = { onToggleDone(task) },
                    onClick = { onOpen(task) },
                    onLongClick = { onLongPress(task) },
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                )
            }
        }
        if (fromEvents.isNotEmpty()) {
            sectionHeader(labelFromEvents)
            items(fromEvents, key = { it.id }) { task ->
                TaskRow(
                    item = task,
                    onToggleDone = { onToggleDone(task) },
                    onClick = { onOpen(task) },
                    onLongClick = { onLongPress(task) },
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                )
            }
        }
        if (pinned.isNotEmpty()) {
            sectionHeader(labelPinned)
            items(pinned, key = { it.id }) { task ->
                TaskRow(
                    item = task,
                    onToggleDone = { onToggleDone(task) },
                    onClick = { onOpen(task) },
                    onLongClick = { onLongPress(task) },
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionHeader(label: String) {
    item {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("$TestTagTodaySection-$label"),
        )
    }
}
