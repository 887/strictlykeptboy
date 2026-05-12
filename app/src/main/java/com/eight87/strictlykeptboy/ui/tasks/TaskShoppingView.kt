package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagShoppingView = "ShoppingView"
const val TestTagShoppingRow = "ShoppingRow"
const val TestTagShoppingCheckbox = "ShoppingCheckbox"

/** UI-J.4 — Shopping view: big checkboxes, title only. No due chrome. */
@Composable
fun TaskShoppingView(
    tasks: List<TaskItem>,
    onToggleDone: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = tasks.sortedForShopping()
    if (sorted.isEmpty()) {
        EmptyTasksState(
            primaryMessage = stringResource(R.string.tasks_empty_shopping_primary),
            neutralMessage = stringResource(R.string.tasks_empty_shopping_neutral),
            modifier = modifier.testTag(TestTagShoppingView),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagShoppingView),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(sorted, key = { it.id }) { task ->
            Surface(
                onClick = { onToggleDone(task) },
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$TestTagShoppingRow-${task.id}"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(width = 1.dp, height = 56.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { onToggleDone(task) },
                        modifier = Modifier
                            .scale(1.5f)
                            .testTag("$TestTagShoppingCheckbox-${task.id}"),
                    )
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.let {
                            if (task.done) it.copy(textDecoration = TextDecoration.LineThrough) else it
                        },
                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
