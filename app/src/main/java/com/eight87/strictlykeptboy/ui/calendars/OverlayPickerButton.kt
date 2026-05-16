package com.eight87.strictlykeptboy.ui.calendars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import kotlinx.coroutines.flow.StateFlow

const val TestTagOverlayPickerButton = "Overlay-PickerButton"
const val TestTagOverlayPickerBadge = "Overlay-PickerBadge"

/**
 * Round 2.21 Phase C.1 — top-bar overlay-picker entry-point.
 *
 * Tap → host opens [OverlayPickerScreen] (full-screen destination per
 * D-2.21.e — not a bottom sheet, not a rail tab). Filter-icon button
 * with an inset badge showing the count of *visible* overlays across
 * all repos (0 ⇒ no badge).
 */
@Composable
fun OverlayPickerButton(
    calendarsFlow: StateFlow<List<CalendarMeta>>,
    visibilityPrefs: CalendarVisibilityPrefs,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendars by calendarsFlow.collectAsState()
    val vis by visibilityPrefs.state.collectAsState()
    val hiddenKeys = vis.ordered.filter { !it.visible }.map { it.repoId to it.id }.toSet()
    val visibleCount = calendars.count { (it.repo.id to it.ref.id) !in hiddenKeys }

    BadgedBox(
        modifier = modifier.testTag(TestTagOverlayPickerButton),
        badge = {
            if (visibleCount > 0) {
                Badge(modifier = Modifier.testTag(TestTagOverlayPickerBadge)) {
                    Text(visibleCount.toString())
                }
            }
        },
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Overlay picker",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        // Keep ambient hooks referenced so future refactors don't drop them.
        @Suppress("UNUSED_EXPRESSION") Box(Modifier.size(0.dp))
        @Suppress("UNUSED_EXPRESSION") CircleShape
    }
}
