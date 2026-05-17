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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    /**
     * Round 2.27 / Phase D.3 — invoked when the user submits a keeper-
     * prompt response via [PromptResponseSheet]. Receives the task plus
     * the typed reply / attachment. The host translates this into a
     * [com.eight87.strictlykeptboy.store.PromptResponseWriter.write]
     * call on the right repo root.
     */
    onPromptRespond: ((TaskItem, String, String?) -> Unit)? = null,
    /**
     * Round 2.27 / Phase C.3 — invoked when the user long-presses a
     * keeper-prompt row to mark it answered without typing a reply.
     * Host writes a synthetic empty response file.
     */
    onPromptMarkAnsweredOffline: ((TaskItem) -> Unit)? = null,
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
            onPromptRespond = onPromptRespond,
            onPromptMarkAnsweredOffline = onPromptMarkAnsweredOffline,
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
    onPromptRespond: ((TaskItem, String, String?) -> Unit)? = null,
    onPromptMarkAnsweredOffline: ((TaskItem) -> Unit)? = null,
) {
    val ui by tasksState.state.collectAsState()
    // Round 2.27 / Phase D.3 — hoisted sheet target. When non-null, the
    // PromptResponseSheet is rendered for this task; submit clears it.
    var responseSheetTarget by remember { mutableStateOf<TaskItem?>(null) }
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
                    onTaskLongPress = { task ->
                        if (task.source == TaskSource.KeeperPrompt) {
                            onPromptMarkAnsweredOffline?.invoke(task)
                        } else {
                            onTaskLongPress(task)
                        }
                    },
                    multiRepo = tasksUi.multiRepo,
                    activeRepoOwner = tasksUi.activeRepoOwner,
                    onStartTask = onStartTask,
                    onTaskRespond = if (onPromptRespond != null) {
                        { task -> responseSheetTarget = task }
                    } else null,
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
    // Round 2.27 / Phase D.3 — mount the response sheet at the Tasks
    // destination root so it overlays the rail + content regardless of
    // which filter is active.
    val target = responseSheetTarget
    if (target != null && onPromptRespond != null) {
        PromptResponseSheet(
            item = target,
            onDismiss = { responseSheetTarget = null },
            onSubmit = { body, attachment ->
                onPromptRespond(target, body, attachment)
                responseSheetTarget = null
            },
        )
    }
}
