package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.tasks.LocalNeutralMode
import com.eight87.strictlykeptboy.ui.tasks.LocalPraiseTerm
import java.time.LocalDate

const val TestTagTodayView = "TodayView"
const val TestTagTodaySection = "TodaySection"

/**
 * UI-J.2 — Today view with 4 sections: overdue, today, from-events, pinned.
 *
 * D-26 fix — Overdue tasks get their own section header with a count so a
 * wall of 15 overdue items has provenance. Non-empty sections all render their
 * header; empty ones are suppressed.
 *
 * D-27 fix — tasks in [animatingDoneIds] are kept visible (with done-state
 * strikethrough / dim) for the ~600ms beat window before they slide out of
 * Today. TaskRow already renders `done=true` with strikethrough; the beat
 * alpha fade here makes the departure visible.
 */
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
    onStartTask: ((String) -> Unit)? = null,
    /**
     * D-27 — task IDs currently in the completion beat animation window.
     * Defaults to [LocalAnimatingDoneIds] so [ExpandedNowPlayingTaskBody]
     * doesn't need to be modified to thread this value through.
     */
    animatingDoneIds: Set<String>? = null,
) {
    // D-27 — fall back to the CompositionLocal if the caller doesn't pass explicitly.
    val effectiveAnimatingIds = animatingDoneIds ?: LocalAnimatingDoneIds.current
    // D-33 — read identity copy from CompositionLocals.
    val praiseTerm = LocalPraiseTerm.current
    val neutralMode = LocalNeutralMode.current
    val forToday = tasks.forToday(today)
    // D-27 — tasks mid-beat are visually "done" but still present in Today.
    val beatTasks = tasks.filter { it.id in effectiveAnimatingIds && it.done }
    val visibleForToday = (forToday + beatTasks).distinctBy { it.id }

    if (visibleForToday.isEmpty()) {
        // U-1 — distinguish between "no Today tasks at all" vs "all Today tasks done".
        // If forToday has tasks (some are done, some beat-animated) but they're ALL done
        // (and no more beat tasks), show the "all done" delight state.
        // More precisely: if the full task list has any today-eligible tasks that are done,
        // it's the all-done case; otherwise it's the truly-empty case.
        val todayEligible = tasks.forToday(today) // unfiltered — includes done
        val hasDoneTodayTasks = todayEligible.any { it.done }
        if (hasDoneTodayTasks) {
            // All-done delight state: kink-positive, on-brand.
            val allDonePrimary = if (!neutralMode && praiseTerm.isNotEmpty()) {
                stringResource(R.string.tasks_today_all_done_primary, praiseTerm)
            } else {
                stringResource(R.string.tasks_today_all_done_primary, "good boy")
            }
            EmptyTasksState(
                primaryMessage = allDonePrimary,
                neutralMessage = stringResource(R.string.tasks_today_all_done_neutral),
                neutralOnly = neutralMode,
                modifier = modifier.testTag(TestTagTodayView),
            )
        } else {
            EmptyTasksState(
                primaryMessage = stringResource(R.string.tasks_empty_today_primary),
                neutralMessage = stringResource(R.string.tasks_empty_today_neutral),
                neutralOnly = neutralMode,
                modifier = modifier.testTag(TestTagTodayView),
            )
        }
        return
    }

    // D-26 fix — split dated into overdue vs due-today so each gets its own
    // labelled header with count. Previously both were merged under "Today".
    //
    // D-27 note — beat tasks (done=true, mid-animation) lose their `isOverdue`
    // flag because `TaskItem.isOverdue = !done && ...`. We restore the correct
    // section by treating a beat task as overdue if its due date is before today.
    // We also treat beat tasks as "active" (not done) for sort ordering so they
    // stay in their natural position rather than sorting to the bottom of the list.
    // D-27 — a beat task (done=true, id in animatingDoneIds) lost its `isOverdue`
    // flag because `TaskItem.isOverdue = !done && ...`. Restore correct section
    // membership by checking due-before-today independently. Pass `beatIds` to
    // `sortedForCombined` so beat tasks sort in their natural active position
    // rather than sinking to the bottom with other done items.
    val beatIds = effectiveAnimatingIds
    val overdue = visibleForToday.filter {
        !it.standing && it.source != TaskSource.FromEvents && it.source != TaskSource.KeeperPrompt &&
            (it.isOverdue ||
                (it.id in beatIds && it.done && it.due != null && it.due.isBefore(today)))
    }.sortedForCombined(today, beatSortAsActive = beatIds)
    val datedToday = visibleForToday.filter {
        !it.standing && it.source != TaskSource.FromEvents && it.source != TaskSource.KeeperPrompt &&
            !it.isOverdue &&
            // Exclude beat tasks that were overdue (they go to the overdue section instead).
            !(it.id in beatIds && it.done && it.due != null && it.due.isBefore(today))
    }.sortedForCombined(today, beatSortAsActive = beatIds)
    val fromEvents = visibleForToday.filter {
        // Round 2.27.B.1 — keeper-prompt rows ride in the FromEvents section
        // until Phase C gives them their own visual treatment.
        it.source == TaskSource.FromEvents || it.source == TaskSource.KeeperPrompt
    }.sortedForCombined(today)
    val pinned = visibleForToday.filter { it.standing && it.pinnedForToday }
        .sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.title.lowercase() })

    val labelOverdue = stringResource(R.string.tasks_today_section_overdue)
    val labelToday = stringResource(R.string.tasks_today_section_today)
    val labelFromEvents = stringResource(R.string.tasks_today_section_from_events)
    val labelPinned = stringResource(R.string.tasks_today_section_pinned)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagTodayView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // D-26 fix — Overdue section (separate from Today, with count).
        if (overdue.isNotEmpty()) {
            sectionHeader("$labelOverdue (${overdue.size})")
            items(overdue, key = { it.id }) { task ->
                BeatTaskRow(
                    task = task,
                    animatingDoneIds = effectiveAnimatingIds,
                    onToggleDone = onToggleDone,
                    onOpen = onOpen,
                    onLongPress = onLongPress,
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                    onStartTask = onStartTask,
                )
            }
        }
        if (datedToday.isNotEmpty()) {
            sectionHeader(if (overdue.isNotEmpty()) "$labelToday (${datedToday.size})" else labelToday)
            items(datedToday, key = { it.id }) { task ->
                BeatTaskRow(
                    task = task,
                    animatingDoneIds = effectiveAnimatingIds,
                    onToggleDone = onToggleDone,
                    onOpen = onOpen,
                    onLongPress = onLongPress,
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                    onStartTask = onStartTask,
                )
            }
        }
        if (fromEvents.isNotEmpty()) {
            sectionHeader(labelFromEvents)
            items(fromEvents, key = { it.id }) { task ->
                BeatTaskRow(
                    task = task,
                    animatingDoneIds = effectiveAnimatingIds,
                    onToggleDone = onToggleDone,
                    onOpen = onOpen,
                    onLongPress = onLongPress,
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                    onStartTask = onStartTask,
                )
            }
        }
        if (pinned.isNotEmpty()) {
            sectionHeader(labelPinned)
            items(pinned, key = { it.id }) { task ->
                BeatTaskRow(
                    task = task,
                    animatingDoneIds = effectiveAnimatingIds,
                    onToggleDone = onToggleDone,
                    onOpen = onOpen,
                    onLongPress = onLongPress,
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                    onStartTask = onStartTask,
                )
            }
        }
    }
}

/**
 * D-27 — thin wrapper that applies a fade-out alpha when [task.id] is in
 * [animatingDoneIds] (the beat window). The row renders with `done=true`
 * styling (strikethrough via [TaskRow]) and fades from 1f → 0.4f over the
 * beat window via [animateFloatAsState].
 */
@Composable
private fun BeatTaskRow(
    task: TaskItem,
    animatingDoneIds: Set<String>,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    onLongPress: (TaskItem) -> Unit,
    multiRepo: Boolean,
    activeRepoOwner: String,
    onStartTask: ((String) -> Unit)?,
) {
    val beating = task.id in animatingDoneIds
    val beatAlpha by animateFloatAsState(
        targetValue = if (beating) 0.4f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "beatAlpha-${task.id}",
    )
    TaskRow(
        item = task,
        onToggleDone = { onToggleDone(task) },
        onClick = { onOpen(task) },
        onLongClick = { onLongPress(task) },
        multiRepo = multiRepo,
        activeRepoOwner = activeRepoOwner,
        onStartTask = onStartTask,
        modifier = Modifier.alpha(beatAlpha),
    )
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
