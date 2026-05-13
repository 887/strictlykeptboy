package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Phase XX.10 / AT-J.3 — tiny rounded number badge for the streak
 * counter. Hidden when `count < 2` (a streak of 1 isn't a streak).
 *
 * LOCKED constraints (AT-J.3 / AT-J.5):
 *   - No flame, no trophy, no icon prefix.
 *   - No color escalation tier — color matches the tile's base
 *     surface (we use the M3E `surfaceContainerHighest` token so the
 *     badge reads quietly on any tile color).
 *   - Glanceable only — never animated, never blinks.
 *   - Hidden when count < 2 (renders to nothing).
 *
 * SOLID-S: the composable does one thing — render the integer.
 * Visibility logic + the global "Show streak counts" toggle live on
 * the caller (e.g. the now-card composable reads
 * [com.eight87.strictlykeptboy.notif.NotificationPrefs.isStreakCountsEnabled]
 * and decides whether to instantiate the badge at all).
 */
@Composable
fun StreakBadge(count: Int, modifier: Modifier = Modifier) {
    if (count < MIN_VISIBLE_COUNT) return
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** AT-J.3 threshold. */
private const val MIN_VISIBLE_COUNT = 2
