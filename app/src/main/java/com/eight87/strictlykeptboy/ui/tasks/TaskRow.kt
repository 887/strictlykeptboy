package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

const val TestTagTaskRow = "TaskRow"
const val TestTagTaskCheckbox = "TaskCheckbox"
const val TestTagTaskTitle = "TaskTitle"
const val TestTagListChip = "TaskListChip"
const val TestTagDueChip = "TaskDueChip"
const val TestTagTaskAuthor = "TaskAuthor"
const val TestTagTaskRepoDot = "TaskRepoDot"
const val TestTagTaskLinkedTimebox = "TaskLinkedTimebox"
const val TestTagTaskStartButton = "TaskStartButton"

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
    /**
     * Phase 2.1.D.4 — when true, the row paints a tiny repo dot before
     * the list-name chip. Driven by `TasksUiState.multiRepo` (>1 repo
     * configured).
     */
    multiRepo: Boolean = false,
    /**
     * Phase 2.1.D.3 — active repo's owner string. The author bubble
     * renders iff `item.author` is non-empty AND differs from this. An
     * empty string here means "unknown owner" — we still render the
     * bubble when `item.author` is non-empty.
     */
    activeRepoOwner: String = "",
    /**
     * Round 2.16.B — temporary "Start" affordance. When non-null, a
     * small Play IconButton renders at the trailing edge of the row;
     * tapping it invokes this callback with the task id, which
     * MainActivity wires to `ActiveTaskController.start`.
     *
     * TODO Phase D — replace with proper start-from-mini-player flow
     * inside the expanded NowPlayingScreen sheet.
     */
    onStartTask: ((String) -> Unit)? = null,
) {
    val accent = colorFromSeed(item.todolist.colorSeed.ifBlank { item.todolist.id })
    val dim = item.done

    Surface(
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("$TestTagTaskRow-${item.id}")
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
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
                    if (multiRepo) {
                        RepoDot(
                            seed = item.todolist.repoId,
                            modifier = Modifier.testTag("$TestTagTaskRepoDot-${item.id}"),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (item.todolist.emoji != null) {
                        Text(item.todolist.emoji + " ", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = item.todolist.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("$TestTagListChip-${item.id}"),
                    )
                    if (item.author.isNotEmpty() && item.author != activeRepoOwner) {
                        Spacer(Modifier.width(6.dp))
                        AuthorBubble(
                            author = item.author,
                            modifier = Modifier.testTag("$TestTagTaskAuthor-${item.id}"),
                        )
                    }
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
                    if (item.linkedEventStart != null) {
                        Spacer(Modifier.width(6.dp))
                        LinkedTimeboxChip(
                            start = item.linkedEventStart,
                            today = today,
                            modifier = Modifier.testTag("$TestTagTaskLinkedTimebox-${item.id}"),
                        )
                    }
                }
            }
            if (item.priority > 0) {
                PriorityDot(level = item.priority)
                Spacer(Modifier.width(8.dp))
            }
            if (onStartTask != null && !item.done) {
                // Round 2.16.B — temp start affordance. Phase D removes
                // this in favour of the expanded-sheet start flow.
                IconButton(
                    onClick = { onStartTask(item.id) },
                    modifier = Modifier.testTag("$TestTagTaskStartButton-${item.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Start task",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoDot(seed: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(
                colorFromSeed(seed.ifBlank { "repo" }),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
    )
}

@Composable
private fun AuthorBubble(author: String, modifier: Modifier = Modifier) {
    val initials = author.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifEmpty { "?" }
    Box(
        modifier = modifier
            .size(16.dp)
            .background(
                colorFromSeed(author),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun LinkedTimeboxChip(
    start: java.time.ZonedDateTime,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val local = start.toLocalDate()
    val time = DateTimeFormatter.ofPattern("HH:mm").format(start)
    val daySuffix = when {
        local == today -> " today"
        local == today.plusDays(1) -> " tomorrow"
        else -> " " + local.toString()
    }
    Text(
        text = "  ·  $time$daySuffix",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
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
