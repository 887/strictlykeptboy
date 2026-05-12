package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagCombinedView = "CombinedView"
const val TestTagCompletedDivider = "CompletedDivider"

/** UI-J.1 — Combined view. */
@Composable
fun TaskCombinedView(
    tasks: List<TaskItem>,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = tasks.sortedForCombined()
    if (sorted.isEmpty()) {
        EmptyTasksState(
            primaryMessage = "no tasks anywhere — good boy ;3",
            neutralMessage = "No tasks.",
            modifier = modifier.testTag(TestTagCombinedView),
        )
        return
    }
    val (active, done) = sorted.partition { !it.done }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagCombinedView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(active, key = { it.id }) { task ->
            TaskRow(
                item = task,
                onToggleDone = { onToggleDone(task) },
                onClick = { onOpen(task) },
            )
        }
        if (done.isNotEmpty()) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp).testTag(TestTagCompletedDivider),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(done, key = { it.id }) { task ->
                TaskRow(
                    item = task,
                    onToggleDone = { onToggleDone(task) },
                    onClick = { onOpen(task) },
                )
            }
        }
    }
}
