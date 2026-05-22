package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.DayBand

/**
 * Round 2.2.C — band overlay helpers shared across Day / Week / Agenda
 * (and a colorSeed-aware tint helper used by Month / Year).
 *
 * The resolver pipes the data (Round 2.1.C.1/.3/.4/.5); the helpers here
 * make it visible on the surface. Each overlay is keyed on a single
 * [DayBand] field so callers compose them à la carte.
 *
 * Test-tag suffixes are stable so screenshot harnesses + Robolectric
 * snap-tests can match them.
 */

const val TestTagBandAuthorChip = "BandAuthorChip"
const val TestTagBandKindGlyph = "BandKindGlyph"
const val TestTagBandSupersededGlyph = "BandSupersededGlyph"
const val TestTagBandOffScheduleGlyph = "BandOffScheduleGlyph"
const val TestTagBandColorStripe = "BandColorStripe"
const val TestTagBandDashedBorder = "BandDashedBorder"

/**
 * Map an Int color seed (resolver-pipe from [com.eight87.strictlykeptboy.resolver.CalendarMeta.colorSeed])
 * into a Compose [Color] with a fixed saturation/lightness range. The
 * input is treated as an arbitrary hash; we map the low 9 bits onto a
 * 360-degree hue wheel and use HSV → ARGB for stable, calm tones.
 *
 * Alpha is always opaque (1.0). Callers apply alpha modifiers as needed
 * (e.g. supersedence 0.35f, week-view full-fill 0.6f).
 */
fun colorForSeed(seed: Int): Color {
    if (seed == 0) return Color.Unspecified
    val hue = ((seed.toLong() and 0xFFFFFFFFL) % 360L).toFloat()
    return Color.hsv(hue, saturation = 0.55f, value = 0.85f)
}

/**
 * 16-dp author bubble, top-right of band. Initials derived from the
 * author PersonRef id ("alex-887" → "AS"; "alex" → "A").
 *
 * Caller is responsible for the `isForeignBand` check — this composable
 * just paints when invoked. Skip on Month for density (per spec).
 */
@Composable
fun BandAuthorChip(
    authorId: String,
    modifier: Modifier = Modifier,
) {
    val initials = initialsForAuthor(authorId)
    Surface(
        modifier = modifier
            .size(16.dp)
            .testTag(TestTagBandAuthorChip),
        shape = RoundedCornerShape(50),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

internal fun initialsForAuthor(authorId: String): String {
    if (authorId.isBlank()) return "?"
    val tokens = authorId.split('-', '_', '.', ' ').filter { it.isNotBlank() }
    return when (tokens.size) {
        0 -> "?"
        1 -> tokens[0].take(1).uppercase()
        else -> (tokens[0].take(1) + tokens[1].take(1)).uppercase()
    }
}

/**
 * 12-dp kind glyph (hourglass for Timebox, calendar dot for Regular).
 * Render before the title text on Day/Week/Agenda. Skip on Month/Year.
 */
@Composable
fun BandKindGlyph(
    kind: CalendarKind,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val icon = when (kind) {
        CalendarKind.Timebox -> Icons.Filled.HourglassEmpty
        CalendarKind.Regular -> Icons.Outlined.CalendarToday
        // Round 2.18.A.3 — external calendars (CalendarContract-backed)
        // render with the same glyph as a regular calendar; the
        // source-icon overlay is handled separately in Phase C.
        CalendarKind.External -> Icons.Outlined.CalendarToday
        // Base-layer scaffolding paints behind everything as a wide
        // subdued slab; no per-band glyph needed — the slab IS the cue.
        CalendarKind.Base -> Icons.Outlined.CalendarToday
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(12.dp).testTag("$TestTagBandKindGlyph-${kind.name}"),
    )
}

/**
 * Habit-kind glyph — distinguishes passive habits (📏 ruler, default-done)
 * from active habits (⚡ lightning, user must act). Passive vs active is
 * the user-facing axis the app surfaces; plain events / meetings show no
 * glyph here and rely on the calendar-kind glyph instead. Caller decides
 * whether a band is passive/active and which (if any) glyph to render.
 */
@Composable
fun BandPassiveHabitGlyph(modifier: Modifier = Modifier) {
    Text(
        text = "📏",
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.testTag("BandPassiveHabitGlyph"),
    )
}

@Composable
fun BandActiveHabitGlyph(modifier: Modifier = Modifier) {
    Text(
        text = "⚡",
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.testTag("BandActiveHabitGlyph"),
    )
}

/** Pause / leaf glyph for supersedence-painted bands. */
@Composable
fun BandSupersededGlyph(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Pause,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(12.dp).testTag(TestTagBandSupersededGlyph),
    )
}

/** Warning glyph for off-schedule bands. */
@Composable
fun BandOffScheduleGlyph(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Outlined.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier.size(12.dp).testTag(TestTagBandOffScheduleGlyph),
    )
}

/**
 * 4-dp left color stripe (Day view) painted via Canvas overlay.
 *
 * Caller passes [seed]; we route through [colorForSeed]. The stripe is
 * rendered inside the band's clip so it follows the rounded corner.
 */
@Composable
fun BandLeftStripe(
    seed: Int,
    modifier: Modifier = Modifier,
    widthDp: androidx.compose.ui.unit.Dp = 4.dp,
) {
    val color = colorForSeed(seed)
    if (color == Color.Unspecified) return
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagBandColorStripe),
    ) {
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(widthDp.toPx(), this.size.height),
        )
    }
}

/**
 * Dashed border overlay for off-schedule bands. Rendered on top of the
 * band Surface (the Surface's `border = null` to avoid double-stroke).
 */
@Composable
fun BandDashedBorder(
    color: Color,
    modifier: Modifier = Modifier,
    cornerRadiusDp: androidx.compose.ui.unit.Dp = 12.dp,
) {
    Canvas(modifier = modifier.fillMaxSize().testTag(TestTagBandDashedBorder)) {
        val strokeWidthPx = 1.5.dp.toPx()
        val effect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(8f, 6f),
            phase = 0f,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
            size = Size(
                this.size.width - strokeWidthPx,
                this.size.height - strokeWidthPx,
            ),
            cornerRadius = CornerRadius(cornerRadiusDp.toPx()),
            style = Stroke(width = strokeWidthPx, pathEffect = effect),
        )
    }
}
