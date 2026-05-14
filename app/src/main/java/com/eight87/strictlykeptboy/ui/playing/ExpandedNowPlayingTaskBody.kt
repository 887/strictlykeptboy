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
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.ui.tasks.TaskCombinedView
import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TaskPerListView
import com.eight87.strictlykeptboy.ui.tasks.TaskShoppingView
import com.eight87.strictlykeptboy.ui.tasks.TaskStandingView
import com.eight87.strictlykeptboy.ui.tasks.TaskTodayView
import com.eight87.strictlykeptboy.ui.tasks.TaskViewTab
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.tasks.TodolistMode
import com.eight87.strictlykeptboy.ui.tasks.visibleTasks
import com.eight87.strictlykeptboy.ui.a11y.labelString

const val TestTagExpandedTaskChipStrip = "ExpandedTaskChipStrip"
const val TestTagExpandedTaskChipPrefix = "ExpandedTaskChip-"
const val TestTagExpandedTaskBody = "ExpandedTaskBody"

/**
 * Round 2.16.D — the todolist UI inside the expanded NowPlayingScreen.
 * Renders a horizontal chip-strip of [TaskViewTab] options on top, and
 * the selected view below. View-mode state is held in `rememberSaveable`
 * with a default of [TaskViewTab.Combined].
 *
 * Hoisted out of TasksPane so the same task surfaces can be reached
 * via the swipe-up sheet. Phase E removes the Tasks tab + TasksPane.
 *
 * The body intentionally fills the remaining viewport via
 * `LazyItemScope.fillParentMaxHeight` so the inner LazyColumns inside
 * each `Task*View` can measure inside the outer NowPlayingScreen
 * LazyColumn.
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

  var selectedTab by rememberSaveable { mutableStateOf(TaskViewTab.Combined) }
  var selectedListId by rememberSaveable { mutableStateOf<String?>(null) }

  val effectiveTab = if (selectedTab == TaskViewTab.PerList && selectedListId != null) {
    val tl = uiState.todolists.firstOrNull { it.id == selectedListId }
    if (tl?.mode == TodolistMode.Shopping) TaskViewTab.Shopping else selectedTab
  } else selectedTab

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(TestTagExpandedTaskBody),
  ) {
    // Chip strip — horizontal scroll for the 5 view-mode options.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(vertical = 4.dp)
        .testTag(TestTagExpandedTaskChipStrip),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TaskViewTab.entries.forEach { tab ->
        FilterChip(
          selected = tab == effectiveTab,
          onClick = { selectedTab = tab },
          label = { Text(tab.labelString()) },
          modifier = Modifier.testTag("$TestTagExpandedTaskChipPrefix${tab.name}"),
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
      when (effectiveTab) {
        TaskViewTab.Combined -> TaskCombinedView(
          tasks = visibleTasks,
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onLongPress = onLongPressTask,
          multiRepo = uiState.multiRepo,
          activeRepoOwner = uiState.activeRepoOwner,
          onStartTask = onStartTask,
        )
        TaskViewTab.Today -> TaskTodayView(
          tasks = visibleTasks,
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onLongPress = onLongPressTask,
          multiRepo = uiState.multiRepo,
          activeRepoOwner = uiState.activeRepoOwner,
          onStartTask = onStartTask,
        )
        TaskViewTab.PerList -> TaskPerListView(
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
        TaskViewTab.Shopping -> TaskShoppingView(
          tasks = if (selectedListId != null) {
            uiState.tasks.filter { it.todolist.id == selectedListId }
          } else {
            uiState.tasks.filter { it.todolist.mode == TodolistMode.Shopping }
          },
          onToggleDone = { tasksState.toggleDone(it.id) },
        )
        TaskViewTab.Standing -> TaskStandingView(
          tasks = visibleTasks,
          onToggleDone = { tasksState.toggleDone(it.id) },
          onOpen = onOpenTask,
          onPinToday = { task, pin -> tasksState.pinStanding(task.id, pin) },
          onStartTask = onStartTask,
        )
      }
    }
  }
}
