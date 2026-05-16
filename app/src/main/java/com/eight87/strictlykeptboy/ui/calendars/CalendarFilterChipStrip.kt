package com.eight87.strictlykeptboy.ui.calendars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Round 2.21 Phase C.3 — `CalendarFilterChipStrip` was **deleted** per
 * D-2.21.d. The horizontal-scroll chip strip lost to the new top-bar
 * overlay-picker (full-screen [OverlayPickerScreen]); keeping both
 * surfaces was double work and the strip lost on density with
 * 11+ overlays.
 *
 * The external-source glyph helpers ([ExternalSourceGlyph] +
 * [glyphFor] + [ExternalSourceLeadingIcon]) and the
 * `CAL_ACCESS_CONTRIBUTOR` constant remain — they're consumed by
 * future surfaces (overlay-row leading icons, calendar-detail sheets)
 * and the standalone external-icon test.
 */

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
 * `CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR` — minimum access
 * level for editing events on a calendar. Duplicated here so the
 * compose surface doesn't pull in `android.provider.*`.
 */
const val CAL_ACCESS_CONTRIBUTOR = 500

/**
 * Round 2.18.C.1 — leading-icon body. Letter glyphs render inside a small
 * outlined circle; cloud / gear glyphs use Material Icons.
 */
@Composable
internal fun ExternalSourceLeadingIcon(glyph: ExternalSourceGlyph, tag: String) {
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
