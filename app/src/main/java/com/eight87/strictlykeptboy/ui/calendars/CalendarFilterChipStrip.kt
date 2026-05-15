package com.eight87.strictlykeptboy.ui.calendars

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import kotlinx.coroutines.flow.StateFlow

const val TestTagCalendarChipStrip = "Calendar-ChipStrip"
/** Round 2.18.C.1 — leading icon test tag suffix: `-<repoId>-<calId>`. */
const val TestTagCalendarChipLeadingIcon = "Calendar-ChipLeadingIcon"
/** Round 2.18.C.2 — read-only lock badge suffix: `-<repoId>-<calId>`. */
const val TestTagCalendarChipReadOnlyLock = "Calendar-ChipReadOnlyLock"

/**
 * Round 2.18.C.1 — external-source identifier glyphs.
 *
 * Resolution (case-insensitive on accountType):
 *  - `com.google` → letter "G" in a circle (Google)
 *  - `com.android.exchange` / starts with `eas` / contains `microsoft` → "O" (Outlook)
 *  - `at.bitfire.davdroid` → `Icons.Default.CloudQueue`
 *  - anything else → `Icons.Default.Settings` (gear)
 *
 * Brand-licensing note (per phase brief): we deliberately ship plain
 * letters in circles rather than authentic Google / Outlook vector marks;
 * Phase H documentation will revisit brand-asset attribution.
 */
enum class ExternalSourceGlyph { G, O, Cloud, Gear }

internal fun glyphFor(accountType: String): ExternalSourceGlyph {
    val at = accountType.lowercase()
    return when {
        at == "com.google" -> ExternalSourceGlyph.G
        at == "com.android.exchange" -> ExternalSourceGlyph.O
        at.startsWith("eas") -> ExternalSourceGlyph.O
        at.contains("microsoft") -> ExternalSourceGlyph.O
        at == "at.bitfire.davdroid" -> ExternalSourceGlyph.Cloud
        else -> ExternalSourceGlyph.Gear
    }
}

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
                val isExternal = cal.kind == CalendarKind.External
                val accountType = cal.externalAccount?.first.orEmpty()
                val glyph = if (isExternal) glyphFor(accountType) else null
                val readOnly = isExternal && cal.externalAccessLevel != null &&
                    cal.externalAccessLevel < CAL_ACCESS_CONTRIBUTOR
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
                    leadingIcon = if (glyph != null) {
                        {
                            ExternalSourceLeadingIcon(
                                glyph = glyph,
                                tag = "$TestTagCalendarChipLeadingIcon-${cal.repo.id}-${cal.ref.id}",
                            )
                        }
                    } else null,
                    trailingIcon = if (readOnly) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "read-only",
                                modifier = Modifier
                                    .size(14.dp)
                                    .testTag("$TestTagCalendarChipReadOnlyLock-${cal.repo.id}-${cal.ref.id}"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else null,
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

/**
 * `CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR` — minimum access
 * level for editing events on a calendar. Mirrors the
 * `android.provider.CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR`
 * constant (500). Duplicated here so the compose surface doesn't pull in
 * `android.provider.*` (keeps Robolectric-free tests possible).
 */
const val CAL_ACCESS_CONTRIBUTOR = 500

/**
 * Round 2.18.C.1 — leading-icon body. Letter glyphs render inside a small
 * outlined circle; cloud / gear glyphs use Material Icons.
 */
@Composable
private fun ExternalSourceLeadingIcon(glyph: ExternalSourceGlyph, tag: String) {
    when (glyph) {
        ExternalSourceGlyph.G, ExternalSourceGlyph.O -> {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag(tag),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (glyph == ExternalSourceGlyph.G) "G" else "O",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        ExternalSourceGlyph.Cloud -> Icon(
            imageVector = Icons.Filled.CloudQueue,
            contentDescription = null,
            modifier = Modifier.size(16.dp).testTag(tag),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExternalSourceGlyph.Gear -> Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(16.dp).testTag(tag),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
