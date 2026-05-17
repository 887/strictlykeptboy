package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.ZonedDateTime

const val TestTagTasksPane = "TasksPane"
const val TestTagTasksDestinationBody = "TasksDestinationBody"
const val TestTagTasksPerListInlineRail = "TasksPerListInlineRail"

/**
 * Round 2.26.A.4 / B.3 — thin Tasks destination root composable.
 *
 * The shell builds the rail items from [TasksFilter] and routes the
 * selection here; [TasksPane] hands off to [TasksDestinationBody] for
 * the actual content. Round 2.26.B wires [scheduleFlow] so that the
 * `Today` filter can merge resolver-emitted timeboxes with todolist
 * tasks.
 */
@Composable
fun TasksPane(
    filter: TasksFilter,
    tasksState: TasksViewState,
    modifier: Modifier = Modifier,
    scheduleFlow: StateFlow<RenderedSchedule?> = remember { MutableStateFlow(null) },
    onTimeboxTap: (DayBand) -> Unit = {},
    onTaskOpen: (TaskItem) -> Unit = {},
    onTaskLongPress: (TaskItem) -> Unit = {},
    onStartTask: ((String) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagTasksPane),
    ) {
        TasksDestinationBody(
            filter = filter,
            tasksState = tasksState,
            scheduleFlow = scheduleFlow,
            onTimeboxTap = onTimeboxTap,
            onTaskOpen = onTaskOpen,
            onTaskLongPress = onTaskLongPress,
            onStartTask = onStartTask,
        )
    }
}

/**
 * Round 2.26.A.4 / B.3 — body of the Tasks destination.
 *
 * Wires the per-filter content:
 *  - [TasksFilter.Today] → [TaskUnifiedTodayView] driven by
 *    [buildUnifiedToday] over `combine(tasksState.state, scheduleFlow)`.
 *  - [TasksFilter.PerList] → existing [TaskSourceRail] chips inline
 *    (A.5) over a filter-name placeholder.
 *  - everything else → filter-name placeholder (to be filled in later
 *    rounds — Upcoming / All / Done are deferred).
 */
@Composable
fun TasksDestinationBody(
    filter: TasksFilter,
    tasksState: TasksViewState,
    modifier: Modifier = Modifier,
    scheduleFlow: StateFlow<RenderedSchedule?> = remember { MutableStateFlow(null) },
    onTimeboxTap: (DayBand) -> Unit = {},
    onTaskOpen: (TaskItem) -> Unit = {},
    onTaskLongPress: (TaskItem) -> Unit = {},
    onStartTask: ((String) -> Unit)? = null,
) {
    val ui by tasksState.state.collectAsState()
    // Round 2.26.B.3 — reactive merge of tasks + rendered schedule.
    val merged by remember(tasksState, scheduleFlow) {
        combine(tasksState.state, scheduleFlow) { tasksUi, sched ->
            tasksUi to sched
        }.distinctUntilChanged()
    }.collectAsState(initial = ui to scheduleFlow.value)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagTasksDestinationBody),
    ) {
        if (filter == TasksFilter.PerList) {
            TaskSourceRail(
                todolists = ui.todolists,
                hiddenTodolistIds = ui.hiddenTodolistIds,
                showInactive = ui.showInactive,
                activeTodolistIds = ui.activeTodolistIds,
                onToggleList = { tasksState.toggleListVisibility(it) },
                onSetShowInactive = { tasksState.setShowInactive(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagTasksPerListInlineRail),
            )
        }
        when (filter) {
            TasksFilter.Today -> {
                val (tasksUi, schedule) = merged
                val now = ZonedDateTime.now()
                val items = buildUnifiedToday(
                    tasks = tasksUi.visibleTasks(),
                    schedule = schedule,
                    now = now,
                )
                TaskUnifiedTodayView(
                    items = items,
                    onTaskToggleDone = { tasksState.toggleDone(it.id) },
                    onTaskOpen = onTaskOpen,
                    onTimeboxTap = onTimeboxTap,
                    onTaskLongPress = onTaskLongPress,
                    multiRepo = tasksUi.multiRepo,
                    activeRepoOwner = tasksUi.activeRepoOwner,
                    onStartTask = onStartTask,
                    today = now.toLocalDate(),
                    now = now,
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Tasks · filter = ${filter.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
