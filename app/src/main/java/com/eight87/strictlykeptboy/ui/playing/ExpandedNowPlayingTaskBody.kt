package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.tasks.TaskCombinedView
import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TaskPerListView
import com.eight87.strictlykeptboy.ui.tasks.TaskShoppingView
import com.eight87.strictlykeptboy.ui.tasks.TaskStandingView
import com.eight87.strictlykeptboy.ui.tasks.TaskTodayView
import com.eight87.strictlykeptboy.ui.tasks.TasksFilter
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.tasks.TodolistMode
import com.eight87.strictlykeptboy.ui.tasks.visibleTasks

const val TestTagExpandedTaskChipStrip = "ExpandedTaskChipStrip"
const val TestTagExpandedTaskChipPrefix = "ExpandedTaskChip-"
const val TestTagExpandedTaskBody = "ExpandedTaskBody"

/**
 * Round 2.16.D / Round 2.26.A.6 — the todolist UI inside the expanded
 * NowPlayingScreen. Renders a horizontal chip-strip of [TasksFilter]
 * options on top, and the selected view below. View-mode state is held
 * in `rememberSaveable` with a default of [TasksFilter.Today].
 *
 * Round 2.26.A.6 swapped the chip enum from the deprecated
 * `TaskViewTab` to the rail-driven [TasksFilter] so the swipe-up sheet
 * and the rail-driven Tasks destination share a single filter
 * vocabulary. `Upcoming` and `Done` route through the same underlying
 * Combined/Today/Standing views for now — Subagent 2 (Phase B) will
 * route `Today` through the unified day-of feed.
 */
@Composable
fun LazyItemScope.ExpandedNowPlayingTaskBody(
  tasksState: TasksViewState,
  onOpenTask: (TaskItem) -> Unit,
  onStartTask: ((String) -> Unit)?,
  onLongPressTask: (TaskItem) -> Unit,
  bodyHeight: androidx.compose.ui.unit.Dp,
) {
  val uiState by tasksState.state.collectAsState()
  val visibleTasks = uiState.visibleTasks()

  var selectedFilter by rememberSaveable { mutableStateOf(TasksFilter.Today) }
  var selectedListId by rememberSaveable { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(TestTagExpandedTaskBody),
  ) {
    // Chip strip — horizontal scroll for the 5 filter options.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(vertical = 4.dp)
        .testTag(TestTagExpandedTaskChipStrip),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TasksFilter.entries.forEach { f ->
        val labelRes = when (f) {
          TasksFilter.Today -> R.string.task_filter_today
          TasksFilter.Upcoming -> R.string.task_filter_upcoming
          TasksFilter.All -> R.string.task_filter_all
          TasksFilter.PerList -> R.string.task_filter_per_list
          TasksFilter.Done -> R.string.task_filter_done
        }
        FilterChip(
          selected = f == selectedFilter,
          onClick = { selectedFilter = f },
          label = { Text(stringResource(labelRes)) },
          modifier = Modifier.testTag("$TestTagExpandedTaskChipPrefix${f.name}"),
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(bodyHeight)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surface),
    ) {
      when (selectedFilter) {
        TasksFilter.Today -> TaskTodayView(
          tasks = visibleTasks,
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onLongPress = onLongPressTask,
          multiRepo = uiState.multiRepo,
          activeRepoOwner = uiState.activeRepoOwner,
          onStartTask = onStartTask,
        )
        TasksFilter.Upcoming -> TaskCombinedView(
          // Stub: Subagent 2 / Phase B routes Upcoming through a
          // dedicated upcoming-only filter. For now reuse Combined so
          // the chip is functional.
          tasks = visibleTasks.filter { !it.done },
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onLongPress = onLongPressTask,
          multiRepo = uiState.multiRepo,
          activeRepoOwner = uiState.activeRepoOwner,
          onStartTask = onStartTask,
        )
        TasksFilter.All -> TaskCombinedView(
          tasks = visibleTasks,
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onLongPress = onLongPressTask,
          multiRepo = uiState.multiRepo,
          activeRepoOwner = uiState.activeRepoOwner,
          onStartTask = onStartTask,
        )
        TasksFilter.PerList -> {
          val selectedList = uiState.todolists.firstOrNull { it.id == selectedListId }
          if (selectedList?.mode == TodolistMode.Shopping) {
            TaskShoppingView(
              tasks = uiState.tasks.filter { it.todolist.id == selectedListId },
              onToggleDone = { tasksState.toggleDone(it.id) },
            )
          } else {
            TaskPerListView(
              tasks = visibleTasks,
              todolists = uiState.todolists,
              activeTodolistIds = uiState.activeTodolistIds,
              selectedListId = selectedListId,
              onSelectList = { selectedListId = it },
              onToggleDone = { tasksState.toggleDone(it.id) },
              onOpen = onOpenTask,
              multiRepo = uiState.multiRepo,
              activeRepoOwner = uiState.activeRepoOwner,
              onStartTask = onStartTask,
            )
          }
        }
        TasksFilter.Done -> TaskStandingView(
          // Stub: Subagent 2 / Phase B routes Done through a dedicated
          // done-only filter. For now reuse Standing-style row layout
          // over the done subset.
          tasks = visibleTasks.filter { it.done },
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onPinToday = { task, pin -> tasksState.pinStanding(task.id, pin) },
          onStartTask = onStartTask,
        )
      }
    }
  }
}
