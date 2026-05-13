package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagTaskSourceRail = "TaskSourceRail"
const val TestTagTaskSourceChip = "TaskSourceChip"
const val TestTagTaskShowInactiveToggle = "TaskShowInactiveToggle"
const val TestTagTaskListSettingsSheet = "TaskListSettingsSheet"

/**
 * Phase 2.1.D.2 — source rail for tasks. Mirror of the (in-progress)
 * `CalendarFilterChipStrip` from 2.1.B.2.
 *
 * One `FilterChip` per todolist. Tap = filter (toggles
 * [hiddenTodolistIds] membership). Long-press = open list-settings
 * sheet (currently a stub — see kdoc on [TaskListSettingsSheet]).
 *
 * Plus an inline `Show inactive` switch (D.1).
 */
@Composable
fun TaskSourceRail(
    todolists: List<TodolistInfo>,
    hiddenTodolistIds: Set<String>,
    showInactive: Boolean,
    activeTodolistIds: Set<String>,
    onToggleList: (String) -> Unit,
    onSetShowInactive: (Boolean) -> Unit,
    onLongPressList: (TodolistInfo) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (todolists.isEmpty()) return
    val activeKnown = activeTodolistIds.isNotEmpty()
    val scroll = rememberScrollState()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTagTaskSourceRail),
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                todolists.forEach { tl ->
                    val visible = tl.id !in hiddenTodolistIds
                    val isInactive = activeKnown && tl.id !in activeTodolistIds
                    SourceChip(
                        list = tl,
                        selected = visible,
                        inactive = isInactive,
                        onClick = { onToggleList(tl.id) },
                        onLongClick = { onLongPressList(tl) },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Show inactive",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = showInactive,
                    onCheckedChange = onSetShowInactive,
                    modifier = Modifier.testTag(TestTagTaskShowInactiveToggle),
                )
            }
        }
    }
}

@Composable
private fun SourceChip(
    list: TodolistInfo,
    selected: Boolean,
    inactive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val label = buildString {
        list.emoji?.let { append(it); append(' ') }
        append(list.name)
        if (inactive) append(" (inactive)")
    }
    Box(
        modifier = Modifier.pointerInput(list.id) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongClick() },
            )
        },
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            modifier = Modifier.testTag("$TestTagTaskSourceChip-${list.id}"),
        )
    }
}

/**
 * Phase 2.1.D.2 — STUB list-settings sheet. The brief explicitly defers
 * the full per-list settings sheet (priority, active-windows,
 * active-hours, mode) to a follow-on commit after the calendar-side
 * `CalendarSettingsSheet` lands in 2.1.B. Until then, long-press shows
 * this placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListSettingsSheet(
    list: TodolistInfo,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        modifier = Modifier.testTag(TestTagTaskListSettingsSheet),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = (list.emoji?.plus(" ") ?: "") + list.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Per-list settings — coming with 2.1.B-style sheet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (list.activeHours.isNotEmpty() || list.activeWindows.isNotEmpty()) {
                Text(
                    text = formatActiveSummary(list),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/**
 * Phase 2.1.D.9 — short summary of a todolist's active-windows /
 * active-hours, suitable for the per-list view header.
 */
internal fun formatActiveSummary(list: TodolistInfo): String {
    val parts = mutableListOf<String>()
    if (list.activeWindows.isNotEmpty()) {
        val rs = list.activeWindows.joinToString(", ") { range ->
            val to = range.endInclusive?.toString() ?: "∞"
            "${range.start}..$to"
        }
        parts += "Active: $rs"
    }
    if (list.activeHours.isNotEmpty()) {
        val hs = list.activeHours.joinToString(", ") { h ->
            "${h.day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${h.from}-${h.to}"
        }
        parts += "Hours: $hs"
    }
    return parts.joinToString(" · ")
}

/** Mirror of [formatActiveSummary] but for the "inactive now" banner. */
internal fun isCurrentlyInactive(list: TodolistInfo, activeTodolistIds: Set<String>): Boolean =
    activeTodolistIds.isNotEmpty() && list.id !in activeTodolistIds
