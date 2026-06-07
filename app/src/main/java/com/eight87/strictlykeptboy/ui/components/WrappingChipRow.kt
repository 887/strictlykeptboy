package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * QA iter-4 B.1 — the one wrapping chip-group row.
 *
 * Chips keep their natural full-label width and wrap as *whole* chips to the
 * next line — no label is ever truncated or split mid-word (the B.3 fix that
 * had to be hand-applied across five files in iter-2). This composable is the
 * single home for that behaviour: it replaces the ~8 inline `FlowRow` copies
 * scattered across the settings categories + the wizard's private
 * `WrappingChipRow`, so a new chip group is one call, not another `FlowRow`
 * block with the same three arguments.
 *
 * SOLID-S: one reason to change (chip-group wrapping layout). SOLID-D: pure
 * presentation, no view-model coupling — callers supply the chips.
 *
 * Defaults match the de-facto settings convention (6.dp both axes,
 * full-width). Override [horizontalSpacing] for the few groups that used 8.dp.
 */
@Composable
fun WrappingChipRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}
