package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagEventCreateFab = "EventCreateFab"
const val TestTagEventCreateFabMenu = "EventCreateFabMenu"
const val TestTagEventCreateFabMenuNewEvent = "EventCreateFabMenuNewEvent"
const val TestTagEventCreateFabMenuRoutine = "EventCreateFabMenuRoutine"
const val TestTagEventCreateFabMenuIcs = "EventCreateFabMenuIcs"

/**
 * Phase FFF / EC-A — single FAB surface for adding to the schedule.
 *
 * - **Tap**: invokes [onClick] — opens the two-tab sheet.
 * - **Long-press**: opens a 3-entry menu (EC-A.4 / EC-D.1).
 * - **Scroll-collapse** (EC-A.1): when [expanded] is false the FAB
 *   renders icon-only; when true it expands to a pill showing `+ New`.
 * - **Disabled** (EC-E.3): when [enabled] is false the FAB dims +
 *   ignores taps.
 *
 * Hand-rolled FAB (Surface + combinedClickable) rather than the M3
 * ExtendedFloatingActionButton — that composable intercepts touches
 * and prevents `combinedClickable`'s long-press from firing.
 */
@Composable
fun EventCreateFab(
    onClick: () -> Unit,
    onLongPressRoutine: () -> Unit = {},
    onLongPressIcs: () -> Unit = {},
    enabled: Boolean = true,
    expanded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val navInsets = WindowInsets.navigationBars.asPaddingValues()
    val alpha = if (enabled) 1f else 0.4f
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .padding(navInsets)
            .testTag(TestTagEventCreateFab),
    ) {
        Surface(
            shape = if (expanded) RoundedCornerShape(16.dp) else CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .alpha(alpha)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
        ) {
            if (expanded) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.event_create_fab_label))
                }
            } else {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.event_create_fab_label),
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.testTag(TestTagEventCreateFabMenu),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.event_create_menu_new_event)) },
                onClick = { menuOpen = false; if (enabled) onClick() },
                modifier = Modifier.testTag(TestTagEventCreateFabMenuNewEvent),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.event_create_menu_routine)) },
                onClick = { menuOpen = false; onLongPressRoutine() },
                modifier = Modifier.testTag(TestTagEventCreateFabMenuRoutine),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.event_create_menu_paste_ics)) },
                onClick = { menuOpen = false; onLongPressIcs() },
                modifier = Modifier.testTag(TestTagEventCreateFabMenuIcs),
            )
        }
    }
}
