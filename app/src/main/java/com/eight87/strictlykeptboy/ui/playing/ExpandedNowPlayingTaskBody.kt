package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.rotate
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

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(bodyHeight)
      .testTag(TestTagExpandedTaskBody),
  ) {
    // Vertical left rail matches the Schedule rail style: rotated text
    // labels, primary accent on the right edge for selection. Width
    // matches SkbScheduleRail's 52dp item width.
    Column(
      modifier = Modifier
        .width(52.dp)
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .testTag(TestTagExpandedTaskChipStrip),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      TasksFilter.entries.forEach { f ->
        val labelRes = when (f) {
          TasksFilter.Today -> R.string.task_filter_today
          TasksFilter.Upcoming -> R.string.task_filter_upcoming
          TasksFilter.All -> R.string.task_filter_all
          TasksFilter.ByRepo -> R.string.task_filter_by_repo
          TasksFilter.Done -> R.string.task_filter_done
        }
        val selected = f == selectedFilter
        val labelColor = if (selected) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
          modifier = Modifier
            .size(width = 52.dp, height = 108.dp)
            .clickable { selectedFilter = f }
            .testTag("$TestTagExpandedTaskChipPrefix${f.name}"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold
              else androidx.compose.ui.text.font.FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier
              .wrapContentSize(unbounded = true)
              .rotate(-90f),
          )
          if (selected) {
            Box(
              modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(2.dp)
                .background(MaterialTheme.colorScheme.primary)
                .clip(RoundedCornerShape(1.dp)),
            )
          }
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxHeight()
        .weight(1f)
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
        TasksFilter.All -> {
          // Per-list secondary filter chips above the combined view —
          // previously its own `PerList` category, now a sub-filter on
          // `All` (D-2.29: rail stays lean, list slicer lives inline).
          Column {
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
        TasksFilter.ByRepo -> {
          // Group tasks by source repo (via todolist.repoId), one
          // section per repo with a sticky-ish header above each.
          val byRepo: Map<String, List<TaskItem>> =
            visibleTasks.groupBy { it.todolist.repoId }
          androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
          ) {
            byRepo.forEach { (repoId, tasks) ->
              item(key = "by-repo-header-$repoId") {
                Text(
                  text = repoId,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                )
              }
              items(
                items = tasks,
                key = { task: TaskItem -> "by-repo-row-$repoId-${task.id}" },
              ) { task ->
                com.eight87.strictlykeptboy.ui.tasks.TaskRow(
                  item = task,
                  onToggleDone = { tasksState.toggleDone(task.id) },
                  onClick = { onOpenTask(task) },
                  onLongClick = { onLongPressTask(task) },
                  multiRepo = uiState.multiRepo,
                  activeRepoOwner = uiState.activeRepoOwner,
                  onStartTask = onStartTask,
                )
              }
            }
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
