package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane

const val TestTagTasksPane = "TasksPane"
const val TestTagTasksDetailEmpty = "TasksDetailEmpty"

/**
 * UI-J — top-level tasks pane. Owns the active [TaskViewTab], the
 * detail-sheet state, the quick-add-sheet state, and routes events to
 * [TasksViewState].
 *
 * Stateful host. Each sub-view is a stateless composable.
 *
 * Phase R.3 — on Medium/Expanded the detail sheet becomes an
 * always-visible right pane (`MasterDetailLayout`); on Compact the
 * original ModalBottomSheet behaviour is preserved.
 */
@Composable
fun TasksPane(
    activeRepoName: String,
    state: TasksViewState,
    onWriteTask: (TaskQuickAddRequest) -> Unit = {},
    modifier: Modifier = Modifier,
    selectedTab: TaskViewTab? = null,
    onSelectTab: (TaskViewTab) -> Unit = {},
) {
    val widthClass = LocalWindowWidthSizeClass.current
    // Nav-swap polish: when the shell owns the rail it hoists `selectedTab`
    // here; existing callers (and tests) that don't pass it keep the
    // legacy in-pane state holder so the pane stays standalone-friendly.
    var internalSelectedTab by remember { mutableStateOf(TaskViewTab.Combined) }
    val effectiveSelectedTab = selectedTab ?: internalSelectedTab
    val updateTab: (TaskViewTab) -> Unit = { t ->
        if (selectedTab == null) internalSelectedTab = t
        onSelectTab(t)
    }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var openTask by remember { mutableStateOf<TaskItem?>(null) }
    var quickAddOpen by remember { mutableStateOf(false) }

    val uiState by state.state.collectAsState()

    // Shopping mode: when a selected list is tagged shopping, auto-route
    // the shopping view per H.4.
    val effectiveTab = remember(effectiveSelectedTab, selectedListId, uiState.todolists) {
        if (effectiveSelectedTab == TaskViewTab.PerList && selectedListId != null) {
            val tl = uiState.todolists.firstOrNull { it.id == selectedListId }
            if (tl?.mode == TodolistMode.Shopping) TaskViewTab.Shopping else effectiveSelectedTab
        } else effectiveSelectedTab
    }

    val master: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize().testTag(TestTagTasksPane)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Nav-swap polish: the in-pane SecondaryTabRow has moved to
                // the global left rail (driven by SkbAppShell). We still
                // render the active-repo label so the pane is identifiable
                // when the rail is the only chrome above the list — and
                // when the pane is rendered standalone from a test.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = activeRepoName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
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
        }
    }

    if (widthClass.isTwoPane()) {
        MasterDetailLayout(
            modifier = modifier,
            widthClass = widthClass,
            master = master,
            detail = {
                val t = openTask
                if (t != null) {
                    TaskDetailContent(
                        task = t,
                        onEdit = { /* Phase EE — editor stub */ },
                        onToggleDone = { state.toggleDone(t.id) },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag(TestTagTasksDetailEmpty),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.tasks_detail_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    } else {
        master()
        openTask?.let { t ->
            TaskDetailSheet(
                task = t,
                onDismiss = { openTask = null },
                onEdit = { /* Phase EE — editor stub */ },
                onToggleDone = { state.toggleDone(t.id) },
            )
        }
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

data class TaskQuickAddRequest(
    val title: String,
    val target: QuickAddTarget,
)
