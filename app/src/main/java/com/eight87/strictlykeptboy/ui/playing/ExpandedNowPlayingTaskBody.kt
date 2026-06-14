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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.tasks.TaskCombinedView
import com.eight87.strictlykeptboy.ui.tasks.TaskDoneView
import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TaskPerListView
import com.eight87.strictlykeptboy.ui.tasks.TaskShoppingView
import com.eight87.strictlykeptboy.ui.tasks.TaskStandingView
import com.eight87.strictlykeptboy.ui.tasks.TaskTodayView
import com.eight87.strictlykeptboy.ui.tasks.TaskUpcomingView
import com.eight87.strictlykeptboy.ui.tasks.TasksFilter
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.tasks.TodolistMode
import com.eight87.strictlykeptboy.ui.tasks.forUpcoming
import com.eight87.strictlykeptboy.ui.tasks.visibleTasks

const val TestTagExpandedTaskChipStrip = "ExpandedTaskChipStrip"
const val TestTagExpandedTaskChipPrefix = "ExpandedTaskChip-"
const val TestTagExpandedTaskBody = "ExpandedTaskBody"

/**
 * Must match the horizontal `contentPadding` of the host LazyColumn in
 * `NowPlayingScreen` — the task body bleeds back out by this amount on each
 * side so the rail + list sit edge-to-edge (the music hero/transport cards
 * keep the inset; a full-bleed task list does not want it).
 */
private val NowPlayingBodyHorizontalBleed = 24.dp

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
) {
  val uiState by tasksState.state.collectAsState()
  val visibleTasks = uiState.visibleTasks()

  var selectedFilter by rememberSaveable { mutableStateOf(TasksFilter.Today) }
  var selectedListId by rememberSaveable { mutableStateOf<String?>(null) }

  Row(
    modifier = Modifier
      // Fill the actual sheet viewport (like the music QueueSection does
      // via fillParentMaxHeight) instead of a guessed `screenHeightDp - n`
      // height — the guess left a dead band at the bottom that the
      // quick-add FAB floated in, with empty space around it.
      .fillParentMaxHeight()
      // Bleed the RIGHT edge past the host LazyColumn's 24.dp horizontal
      // contentPadding so the task list reaches the screen edge (fixes the
      // dead right margin). The left edge stays at the content inset so the
      // rail's rotated labels keep the small margin they need — bleeding the
      // left clipped them ("Today" → "oday"). Kept in sync with
      // NowPlayingScreen's `contentPadding` horizontal value.
      .layout { measurable, constraints ->
        val extra = NowPlayingBodyHorizontalBleed.roundToPx()
        val placeable = measurable.measure(
          constraints.copy(
            minWidth = constraints.maxWidth + extra,
            maxWidth = constraints.maxWidth + extra,
          ),
        )
        layout(placeable.width, placeable.height) {
          placeable.place(0, 0)
        }
      }
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
        TasksFilter.Upcoming -> TaskUpcomingView(
          // D-11 fix — Upcoming shows only tasks with a due date STRICTLY
          // after today (no-due and today-due tasks excluded; those belong
          // to Today). Sorted chronologically by due date.
          // D-29 fix — grouped into Tomorrow / This week / Next week / Later
          // buckets with section headers for clear chronological structure.
          tasks = visibleTasks,
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
                // D-12 fix — resolve repoId → friendly display name.
                // Falls back to the last path segment (e.g. "marc-kept"
                // from "github.com/887/marc-kept") when no display name
                // is in the map, which is still far more readable than
                // the full raw slug.
                // D-21 fix — repoId can be empty for demo/local tasks;
                // fall back to "Local" instead of rendering a blank header.
                val displayName = uiState.repoDisplayNames[repoId]
                    ?: repoId.substringAfterLast('/').ifEmpty { "Local" }
                Text(
                  text = displayName,
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
        TasksFilter.Done -> TaskDoneView(
          // D-3 fix: use the dedicated done view which shows ALL tasks
          // with done == true (not filtered by standing == true like
          // TaskStandingView did). Sources the full state.tasks list so
          // that tasks toggled done are visible here even if they
          // dropped off the active feed in other filters.
          // D-13 fix: pass multiRepo + activeRepoOwner so author chips
          // and repo dots render in Done exactly like Today/All.
          tasks = uiState.tasks,
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onStartTask = onStartTask,
          multiRepo = uiState.multiRepo,
          activeRepoOwner = uiState.activeRepoOwner,
        )
      }
    }
  }
}
