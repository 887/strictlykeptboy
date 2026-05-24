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
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
const val TestTagScheduleSupersededFab = "ScheduleSupersededFab"

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFloatingActions(
    layout: ScheduleLayoutMode,
    onLayoutChange: (ScheduleLayoutMode) -> Unit,
    flags: ScheduleFilterFlags,
    onFlagsChange: (ScheduleFilterFlags) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * When false, the layout-toggle FAB is omitted. Day tab passes
     * `false` because the "Now" tab already covers the
     * stacked-list use-case (per user 2026-05-23).
     */
    showLayoutToggle: Boolean = true,
    /**
     * Round 2026-05-24 — current visual treatment for supersedence-tagged
     * bands. Tapping the FAB cycles between [SupersededDisplayMode.Hidden]
     * (drops them entirely — the default; "I just want to see what the
     * special-base event actually replaced my day with") and
     * [SupersededDisplayMode.Strikethrough] (keeps them visible at 0.35
     * alpha + LineThrough; "I want to see what got skipped").
     */
    supersededMode: SupersededDisplayMode = SupersededDisplayMode.Hidden,
    onSupersededModeChange: (SupersededDisplayMode) -> Unit = {},
    /**
     * When false, the superseded-mode toggle FAB is omitted. Preview /
     * test entry-points without a wired [ScheduleViewModePrefs] pass
     * `false` so taps don't no-op silently.
     */
    showSupersededToggle: Boolean = true,
) {
    // Bottom-start cluster — placed against the rail edge so it
    // never overlaps actual schedule content. The "+ New" FAB still
    // lives at bottom-end. Per user 2026-05-23.
    Column(
        modifier = modifier.padding(start = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showLayoutToggle) {
            // Fix-batch W3.11 / M-1 (2026-05-24) — long-press / hover
            // tooltip describes what the FAB does so users don't have
            // to tap blind.
            val layoutTip = if (layout == ScheduleLayoutMode.Stacked) {
                "Switch to grid layout"
            } else {
                "Switch to stacked layout"
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(layoutTip) } },
                state = rememberTooltipState(),
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
                        contentDescription = layoutTip,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        var menuOpen by remember { mutableStateOf(false) }
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("Filter events by tag") } },
            state = rememberTooltipState(),
        ) {
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

        if (showSupersededToggle) {
            // Fix-batch W3.5 / U-2 + W3.11 / M-1 (2026-05-24) —
            // long-press / hover tooltip describing what tapping the
            // FAB does. The content-description is also more concrete
            // about what "superseded" means (events hidden by an
            // overriding base layer like a holiday or vacation).
            val supersededTip = if (supersededMode == SupersededDisplayMode.Hidden) {
                "Show events hidden by base layer (as strikethrough)"
            } else {
                "Hide events hidden by base layer"
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(supersededTip) } },
                state = rememberTooltipState(),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        onSupersededModeChange(
                            if (supersededMode == SupersededDisplayMode.Hidden) {
                                SupersededDisplayMode.Strikethrough
                            } else {
                                SupersededDisplayMode.Hidden
                            },
                        )
                    },
                    modifier = Modifier.testTag(TestTagScheduleSupersededFab),
                ) {
                    Icon(
                        imageVector = if (supersededMode == SupersededDisplayMode.Hidden) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.StrikethroughS
                        },
                        contentDescription = supersededTip,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
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
