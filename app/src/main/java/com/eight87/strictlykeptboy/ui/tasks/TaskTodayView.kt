package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

const val TestTagTodayView = "TodayView"
const val TestTagTodaySection = "TodaySection"

/** UI-J.2 — Today view with 3 sections: today/overdue, from-events, pinned. */
@Composable
fun TaskTodayView(
    tasks: List<TaskItem>,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val forToday = tasks.forToday(today)
    if (forToday.isEmpty()) {
        EmptyTasksState(
            primaryMessage = "nothing on your plate today — good boy ;3",
            neutralMessage = "No tasks for today.",
            modifier = modifier.testTag(TestTagTodayView),
        )
        return
    }

    val dated = forToday.filter { !it.standing && it.source != TaskSource.FromEvents }
        .sortedForCombined(today)
    val fromEvents = forToday.filter { it.source == TaskSource.FromEvents }
        .sortedForCombined(today)
    val pinned = forToday.filter { it.standing && it.pinnedForToday }
        .sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.title.lowercase() })

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagTodayView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (dated.isNotEmpty()) {
            sectionHeader("Today")
            items(dated, key = { it.id }) { task ->
                TaskRow(item = task, onToggleDone = { onToggleDone(task) }, onClick = { onOpen(task) })
            }
        }
        if (fromEvents.isNotEmpty()) {
            sectionHeader("From today's events")
            items(fromEvents, key = { it.id }) { task ->
                TaskRow(item = task, onToggleDone = { onToggleDone(task) }, onClick = { onOpen(task) })
            }
        }
        if (pinned.isNotEmpty()) {
            sectionHeader("Pinned")
            items(pinned, key = { it.id }) { task ->
                TaskRow(item = task, onToggleDone = { onToggleDone(task) }, onClick = { onOpen(task) })
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionHeader(label: String) {
    item {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("$TestTagTodaySection-$label"),
        )
    }
}
