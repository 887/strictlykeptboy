package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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

const val TestTagTasksPane = "TasksPane"
const val TestTagTaskViewTab = "TaskViewTab"

/**
 * UI-J — top-level tasks pane. Owns the active [TaskViewTab], the
 * detail-sheet state, the quick-add-sheet state, and routes events to
 * [TasksViewState].
 *
 * Stateful host. Each sub-view is a stateless composable.
 */
@Composable
fun TasksPane(
    activeRepoName: String,
    state: TasksViewState,
    onWriteTask: (TaskQuickAddRequest) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(TaskViewTab.Combined) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var openTask by remember { mutableStateOf<TaskItem?>(null) }
    var quickAddOpen by remember { mutableStateOf(false) }

    val uiState by state.state.collectAsState()

    // Shopping mode: when a selected list is tagged shopping, auto-route
    // the shopping view per H.4.
    val effectiveTab = remember(selectedTab, selectedListId, uiState.todolists) {
        if (selectedTab == TaskViewTab.PerList && selectedListId != null) {
            val tl = uiState.todolists.firstOrNull { it.id == selectedListId }
            if (tl?.mode == TodolistMode.Shopping) TaskViewTab.Shopping else selectedTab
        } else selectedTab
    }

    Box(modifier = modifier.fillMaxSize().testTag(TestTagTasksPane)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Text(
                        text = activeRepoName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                    SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                        TaskViewTab.entries.forEach { tab ->
                            Tab(
                                selected = tab == selectedTab,
                                onClick = { selectedTab = tab },
                                modifier = Modifier.testTag("$TestTagTaskViewTab-${tab.name}"),
                                text = { Text(tab.label) },
                            )
                        }
                    }
                }
            }

            when (effectiveTab) {
                TaskViewTab.Combined -> TaskCombinedView(
                    tasks = uiState.tasks,
                    onToggleDone = { state.toggleDone(it.id) },
                    onOpen = { openTask = it },
                )
                TaskViewTab.Today -> TaskTodayView(
                    tasks = uiState.tasks,
                    onToggleDone = { state.toggleDone(it.id) },
                    onOpen = { openTask = it },
                )
                TaskViewTab.PerList -> TaskPerListView(
                    tasks = uiState.tasks,
                    todolists = uiState.todolists,
                    selectedListId = selectedListId,
                    onSelectList = { selectedListId = it },
                    onToggleDone = { state.toggleDone(it.id) },
                    onOpen = { openTask = it },
                )
                TaskViewTab.Shopping -> TaskShoppingView(
                    tasks = if (selectedListId != null) uiState.tasks.filter { it.todolist.id == selectedListId }
                    else uiState.tasks.filter { it.todolist.mode == TodolistMode.Shopping },
                    onToggleDone = { state.toggleDone(it.id) },
                )
                TaskViewTab.Standing -> TaskStandingView(
                    tasks = uiState.tasks,
                    onToggleDone = { state.toggleDone(it.id) },
                    onOpen = { openTask = it },
                    onPinToday = { task, pin -> state.pinStanding(task.id, pin) },
                )
            }
        }

        TaskQuickAddFab(
            onClick = { quickAddOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        )

        openTask?.let { t ->
            TaskDetailSheet(
                task = t,
                onDismiss = { openTask = null },
                onEdit = { /* Phase EE — editor stub */ },
                onToggleDone = { state.toggleDone(t.id) },
            )
        }

        if (quickAddOpen) {
            val initialTarget = uiState.todolists.firstOrNull {
                it.id == selectedListId
            }?.let { QuickAddTarget.Todolist(it) }
                ?: uiState.todolists.firstOrNull()?.let { QuickAddTarget.Todolist(it) }
            TaskQuickAddSheet(
                todolists = uiState.todolists,
                initialTarget = initialTarget,
                onDismiss = { quickAddOpen = false },
                onSubmit = { title, target ->
                    onWriteTask(TaskQuickAddRequest(title = title, target = target))
                    quickAddOpen = false
                },
            )
        }
    }
}

data class TaskQuickAddRequest(
    val title: String,
    val target: QuickAddTarget,
)
