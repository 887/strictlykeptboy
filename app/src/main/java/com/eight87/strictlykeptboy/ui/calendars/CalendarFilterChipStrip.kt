package com.eight87.strictlykeptboy.ui.calendars

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import kotlinx.coroutines.flow.StateFlow

const val TestTagCalendarChipStrip = "Calendar-ChipStrip"

/**
 * Round 2.1.B.2 — chip strip rendered above [SchedulePane].
 *
 * One [FilterChip] per [CalendarMeta]: emoji (when present) + display name
 * + color seed tint on the selected container. Tap toggles
 * device-local visibility through [CalendarVisibilityPrefs]; long-press
 * dispatches [onLongPressCalendar] (the host opens [CalendarSettingsSheet]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarFilterChipStrip(
    calendarsFlow: StateFlow<List<CalendarMeta>>,
    visibilityPrefs: CalendarVisibilityPrefs,
    modifier: Modifier = Modifier,
    onLongPressCalendar: (CalendarMeta) -> Unit = {},
) {
    val calendars by calendarsFlow.collectAsState()
    val visibility by visibilityPrefs.state.collectAsState()
    val visibilityById = visibility.ordered.associateBy { it.repoId to it.id }

    if (calendars.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(TestTagCalendarChipStrip),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        calendars.forEach { cal ->
            val key = cal.repo.id to cal.ref.id
            val visible = visibilityById[key]?.visible ?: true
            val tint = cal.colorSeed?.let { Color(0xFF000000.toInt() or (it and 0x00FFFFFF)) }
            val interactionSource = remember { MutableInteractionSource() }
            // FilterChip does not surface long-press directly; wrap in a
            // Box with combinedClickable, and dispatch the chip's tap
            // toggle from there. Visual chip state stays in sync because
            // we still pass `selected = visible`.
            Box(
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            visibilityPrefs.setVisible(
                                id = cal.ref.id,
                                visible = !visible,
                                repoId = cal.repo.id,
                            )
                        },
                        onLongClick = { onLongPressCalendar(cal) },
                    )
                    .testTag("$TestTagCalendarChipStrip-${cal.repo.id}-${cal.ref.id}"),
            ) {
                FilterChip(
                    selected = visible,
                    onClick = {
                        visibilityPrefs.setVisible(
                            id = cal.ref.id,
                            visible = !visible,
                            repoId = cal.repo.id,
                        )
                    },
                    label = {
                        val prefix = if (!cal.activeToggle) "(off) " else ""
                        Text("$prefix${cal.displayName}", style = MaterialTheme.typography.labelMedium)
                    },
                    colors = if (tint != null) {
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tint.copy(alpha = 0.25f),
                        )
                    } else FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}
