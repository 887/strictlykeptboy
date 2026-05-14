package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagStandingView = "StandingView"
const val TestTagPinMenu = "PinMenu"
const val TestTagPinAction = "PinAction"

/** UI-J.5 — Standing view: no-deadline tasks; long-press → pin-to-today. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskStandingView(
    tasks: List<TaskItem>,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    onPinToday: (TaskItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onStartTask: ((String) -> Unit)? = null,
) {
    val sorted = tasks.sortedForStanding()
    if (sorted.isEmpty()) {
        EmptyTasksState(
            primaryMessage = stringResource(R.string.tasks_empty_standing_primary),
            neutralMessage = stringResource(R.string.tasks_empty_standing_neutral),
            modifier = modifier.testTag(TestTagStandingView),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagStandingView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(sorted, key = { it.id }) { task ->
            StandingTaskRowWithMenu(
                item = task,
                onToggleDone = { onToggleDone(task) },
                onOpen = { onOpen(task) },
                onPin = { onPinToday(task, !task.pinnedForToday) },
                onStartTask = onStartTask,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandingTaskRowWithMenu(
    item: TaskItem,
    onToggleDone: () -> Unit,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onStartTask: ((String) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { menuOpen = true },
            ),
    ) {
        TaskRow(
            item = item,
            onToggleDone = onToggleDone,
            onClick = onOpen,
            onLongClick = { menuOpen = true },
            onStartTask = onStartTask,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.testTag("$TestTagPinMenu-${item.id}"),
        ) {
            DropdownMenuItem(
                text = { Text(if (item.pinnedForToday) stringResource(R.string.tasks_standing_unpin) else stringResource(R.string.tasks_standing_pin)) },
                onClick = {
                    menuOpen = false
                    onPin()
                },
                modifier = Modifier.testTag("$TestTagPinAction-${item.id}"),
            )
        }
    }
}
