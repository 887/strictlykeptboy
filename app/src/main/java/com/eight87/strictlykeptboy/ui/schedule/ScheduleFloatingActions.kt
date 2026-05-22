package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagScheduleLayoutFab = "ScheduleLayoutFab"
const val TestTagScheduleFilterFab = "ScheduleFilterFab"
const val TestTagScheduleFilterMenu = "ScheduleFilterMenu"

/**
 * Shutterboy-style floating action cluster for Schedule.
 *
 * Two stacked small-FABs along the right edge:
 *   - **Layout** (top) — toggles between Stacked (single long column)
 *     and Grid (multi-column time view). Icon flips per current mode.
 *   - **Filter** (below) — opens a multi-toggle dropdown menu with
 *     Important / Active / Routine. At least one must stay on.
 *
 * Per user direction these are explicitly separate buttons rather
 * than a unified mode pill — the layout is orthogonal to what's
 * filtered.
 */
@Composable
fun ScheduleFloatingActions(
    layout: ScheduleLayoutMode,
    onLayoutChange: (ScheduleLayoutMode) -> Unit,
    flags: ScheduleFilterFlags,
    onFlagsChange: (ScheduleFilterFlags) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Offset the cluster above the existing "+ New" FAB at
    // bottom-end (16dp self-padding + ~56dp FAB + 16dp gap).
    Column(
        modifier = modifier.padding(end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallFloatingActionButton(
            onClick = {
                onLayoutChange(
                    if (layout == ScheduleLayoutMode.Stacked) ScheduleLayoutMode.Grid
                    else ScheduleLayoutMode.Stacked
                )
            },
            modifier = Modifier.testTag(TestTagScheduleLayoutFab),
        ) {
            Icon(
                imageVector = if (layout == ScheduleLayoutMode.Stacked) {
                    Icons.Outlined.GridView
                } else {
                    Icons.Outlined.ViewAgenda
                },
                contentDescription = if (layout == ScheduleLayoutMode.Stacked) {
                    "Switch to grid layout"
                } else {
                    "Switch to stacked layout"
                },
                modifier = Modifier.size(22.dp),
            )
        }

        var menuOpen by remember { mutableStateOf(false) }
        SmallFloatingActionButton(
            onClick = { menuOpen = true },
            modifier = Modifier.testTag(TestTagScheduleFilterFab),
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = "Filter events",
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.testTag(TestTagScheduleFilterMenu),
        ) {
            FilterToggleRow(
                label = "Important",
                checked = flags.important,
                icon = Icons.Outlined.PriorityHigh,
                onToggle = {
                    val next = flags.copy(important = !flags.important)
                    onFlagsChange(if (next.anyOn()) next else flags)
                },
            )
            FilterToggleRow(
                label = "Active",
                checked = flags.active,
                icon = Icons.Outlined.Bolt,
                onToggle = {
                    val next = flags.copy(active = !flags.active)
                    onFlagsChange(if (next.anyOn()) next else flags)
                },
            )
            FilterToggleRow(
                label = "Routine",
                checked = flags.routine,
                icon = Icons.Outlined.Repeat,
                onToggle = {
                    val next = flags.copy(routine = !flags.routine)
                    onFlagsChange(if (next.anyOn()) next else flags)
                },
            )
        }
    }
}

@Composable
private fun FilterToggleRow(
    label: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = if (checked) "$label  ✓" else label,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onToggle,
    )
}
