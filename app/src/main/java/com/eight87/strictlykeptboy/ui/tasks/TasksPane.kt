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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagTasksPane = "TasksPane"
const val TestTagTasksDestinationBody = "TasksDestinationBody"
const val TestTagTasksPerListInlineRail = "TasksPerListInlineRail"

/**
 * Round 2.26.A.4 — thin Tasks destination root composable.
 *
 * The shell builds the rail items from [TasksFilter] and routes the
 * selection here; [TasksPane] hands off to [TasksDestinationBody] for
 * the actual content. Round 2.26.A leaves the body stubbed
 * (filter-name placeholder); Subagent 2 (Phase B) fills in the unified
 * day-of feed.
 */
@Composable
fun TasksPane(
    filter: TasksFilter,
    tasksState: TasksViewState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagTasksPane),
    ) {
        TasksDestinationBody(
            filter = filter,
            tasksState = tasksState,
        )
    }
}

/**
 * Round 2.26.A.4 — STUB. Renders the filter name as a placeholder so
 * the rail + destination wiring can be AVD-smoked. Subagent 2 replaces
 * this with the unified-today feed (Phase B).
 *
 * A.5: when [filter] == [TasksFilter.PerList], we mount the existing
 * [TaskSourceRail] chips inline above the placeholder so users can
 * scope which todolist they're working in (the chips are the canonical
 * per-list selector — previously mounted as a header above
 * [ExpandedNowPlayingTaskBody]).
 */
@Composable
fun TasksDestinationBody(
    filter: TasksFilter,
    tasksState: TasksViewState,
    modifier: Modifier = Modifier,
) {
    val ui by tasksState.state.collectAsState()
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
