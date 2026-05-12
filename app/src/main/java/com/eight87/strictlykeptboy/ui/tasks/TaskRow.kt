package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.abs

const val TestTagTaskRow = "TaskRow"
const val TestTagTaskCheckbox = "TaskCheckbox"
const val TestTagTaskTitle = "TaskTitle"
const val TestTagListChip = "TaskListChip"
const val TestTagDueChip = "TaskDueChip"

/**
 * UI-J row layout — 4dp left accent, checkbox, title, due chip, list chip.
 * Used by Combined / Today / Per-list / Standing. Shopping has its own
 * bigger-checkbox variant in [TaskShoppingView].
 */
@Composable
fun TaskRow(
    item: TaskItem,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val accent = colorFromSeed(item.todolist.colorSeed.ifBlank { item.todolist.id })
    val dim = item.done

    Surface(
        onClick = onClick,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("$TestTagTaskRow-${item.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(accent),
            )
            Checkbox(
                checked = item.done,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .testTag("$TestTagTaskCheckbox-${item.id}"),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.let {
                        if (dim) it.copy(textDecoration = TextDecoration.LineThrough) else it
                    },
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("$TestTagTaskTitle-${item.id}"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.todolist.emoji != null) {
                        Text(item.todolist.emoji + " ", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = item.todolist.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("$TestTagListChip-${item.id}"),
                    )
                    if (item.due != null) {
                        Text(
                            text = "  ·  " + formatDue(item.due, today, item.done, item.doneAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("$TestTagDueChip-${item.id}"),
                        )
                    } else if (item.done && item.doneAt != null) {
                        Text(
                            text = "  ·  " + agoFormat(item.doneAt, today),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (item.priority > 0) {
                PriorityDot(level = item.priority)
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun PriorityDot(level: Int) {
    val color = when (level) {
        in 8..Int.MAX_VALUE -> MaterialTheme.colorScheme.error
        in 4..7 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape),
    )
}

internal fun formatDue(due: LocalDate, today: LocalDate, done: Boolean, doneAt: LocalDate?): String {
    if (done && doneAt != null) return "done " + agoFormat(doneAt, today)
    val diff = java.time.temporal.ChronoUnit.DAYS.between(today, due).toInt()
    return when {
        diff == 0 -> "today"
        diff == 1 -> "tomorrow"
        diff == -1 -> "yesterday"
        diff in -6..-1 -> "${-diff}d overdue"
        diff in 2..6 -> "in ${diff}d"
        else -> due.toString()
    }
}

internal fun agoFormat(date: LocalDate, today: LocalDate): String {
    val diff = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()
    return when {
        diff == 0 -> "today"
        diff == 1 -> "yesterday"
        diff in 2..6 -> "${diff}d ago"
        else -> date.toString()
    }
}

/** Deterministic color from a seed string. Stable across recompositions. */
internal fun colorFromSeed(seed: String): Color {
    val h = (abs(seed.hashCode()) % 360).toFloat()
    return Color.hsv(h, 0.55f, 0.85f)
}
