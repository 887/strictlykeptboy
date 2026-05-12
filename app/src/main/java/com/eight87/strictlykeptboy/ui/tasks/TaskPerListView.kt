package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagPerListView = "PerListView"
const val TestTagListFilterChip = "ListFilterChip"

/** UI-J.3 — Per-list view: chip-row filter at the top. */
@Composable
fun TaskPerListView(
    tasks: List<TaskItem>,
    todolists: List<TodolistInfo>,
    selectedListId: String?,
    onSelectList: (String) -> Unit,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag(TestTagPerListView)) {
        val scroll = rememberScrollState()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            todolists.forEach { tl ->
                val count = tasks.count { it.todolist.id == tl.id && !it.done }
                FilterChip(
                    selected = tl.id == selectedListId,
                    onClick = { onSelectList(tl.id) },
                    label = {
                        val emoji = tl.emoji?.plus(" ") ?: ""
                        Text("$emoji${tl.name} ($count)")
                    },
                    modifier = Modifier.testTag("$TestTagListFilterChip-${tl.id}"),
                )
            }
        }
        val filtered = if (selectedListId == null) tasks
        else tasks.filter { it.todolist.id == selectedListId }
        val sorted = filtered.sortedForCombined()
        if (sorted.isEmpty()) {
            EmptyTasksState(
                primaryMessage = stringResource(R.string.tasks_empty_list_primary),
                neutralMessage = stringResource(R.string.tasks_empty_list_neutral),
            )
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(sorted, key = { it.id }) { task ->
                TaskRow(item = task, onToggleDone = { onToggleDone(task) }, onClick = { onOpen(task) })
            }
        }
    }
}
