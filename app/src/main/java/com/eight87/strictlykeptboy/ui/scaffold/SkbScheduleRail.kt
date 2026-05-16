package com.eight87.strictlykeptboy.ui.scaffold

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.ui.calendars.OverlayPickerButton
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import kotlinx.coroutines.flow.StateFlow

/**
 * Round 2.21 SOLID split — extracted from `SkbAppShell.kt`. This file
 * owns the left view-mode rail composition + the per-pane
 * placeholder. Single reason to change: rail visual / layout / item
 * shape.
 *
 * The shell remains the only call-site of [RailColumn] and
 * [PlaceholderScreen]; the shell builds the [RailItem] list per
 * destination and hands it to [RailColumn] for layout.
 */

/**
 * Narrow data interface for a left-rail entry (R.X.1 / R.X.7).
 *
 * The shell renders these with rotated text labels, à la tonearmboy's
 * `LibraryRail` — see [RailColumn]. Panes that have no view-mode
 * sub-navigation simply return an empty list and the rail collapses.
 *
 * @param key stable identity for testTag composition + `rememberSaveable`
 *            round-tripping (must be ASCII-safe).
 * @param labelRes the localised label, fed to `stringResource`.
 * @param selected true if this is the currently-active rail entry.
 * @param onClick fired when the user taps this entry.
 */
data class RailItem(
    val key: String,
    @StringRes val labelRes: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Vertical left rail — rotated text labels per [items]. 52dp wide on
 * all width classes for v1; future polish may widen on Expanded.
 *
 * Mirrors `tonearmboy.ui.library.LibraryRail` in spirit: rotated -90°
 * text inside a fixed-size Box; selected item draws a 2dp accent
 * stripe on its right edge. Scrolls vertically if items don't fit.
 */
@Composable
internal fun RailColumn(
    items: List<RailItem>,
    activeIconKind: com.eight87.strictlykeptboy.ui.theming.RepoIconKind,
    onAccountTap: () -> Unit,
    onSettingsTap: () -> Unit,
    /**
     * Round 2.22 / Fix 2 — when non-null + prefs non-null, the
     * overlay-picker icon button renders pinned to the bottom of the
     * rail (tonearmboy `LibraryRail` parity — the settings gear lives
     * at the bottom-left there; here it's the overlay-picker filter
     * icon, because the user complained that the previous top-bar
     * mount-point vanished on the Reviews destination).
     */
    overlayPickerCalendars: StateFlow<List<CalendarMeta>>? = null,
    overlayPickerPrefs: CalendarVisibilityPrefs? = null,
    onOverlayPickerClick: () -> Unit = {},
) {
    // Match tonearmboy's LibraryRail: 52dp wide, 108dp per item.
    // Bottom of the rail now carries the overlay-picker filter icon
    // (Round 2.22 / Fix 2) — this is the persistent entry-point for the
    // full-screen OverlayPickerScreen on the Schedule destination. On
    // destinations that don't supply picker wiring the bottom slot is
    // empty; on destinations without a rail at all (Reviews / Settings
    // / Repos / Wizard) the user reaches schedule overlays by going
    // back to Schedule first — per design, overlays are a
    // Schedule-context concern.
    val railWidth = 52.dp
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .requiredWidth(railWidth)
            .background(MaterialTheme.colorScheme.surface)
            .testTag(TestTagShellRail),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // TOP: view-mode tabs (scrollable if many).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    RailTabItem(item = item)
                }
            }
            // BOTTOM: overlay-picker filter icon (when wired).
            if (overlayPickerCalendars != null && overlayPickerPrefs != null) {
                OverlayPickerButton(
                    calendarsFlow = overlayPickerCalendars,
                    visibilityPrefs = overlayPickerPrefs,
                    onClick = onOverlayPickerClick,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    // Keep params referenced so callers retain the existing surface
    // (account/settings slots may return in a tablet expansion).
    @Suppress("UNUSED_EXPRESSION") activeIconKind
    @Suppress("UNUSED_EXPRESSION") onAccountTap
    @Suppress("UNUSED_EXPRESSION") onSettingsTap
}

@Composable
private fun RailTabItem(item: RailItem) {
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = if (item.selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = stringResource(item.labelRes)
    val tag = "$TestTagShellRailItemPrefix${item.key}"

    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 108.dp)
            .clickable(onClick = item.onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .rotate(-90f),
        )
        if (item.selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(accent)
                    .clip(RoundedCornerShape(1.dp)),
            )
        }
    }
}

/**
 * Empty-state placeholder for destinations whose pane hasn't been
 * wired yet (or whose wiring is null at the call-site, e.g. tests
 * without a ReposViewState).
 */
@Composable
internal fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.scaffold_placeholder_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ---- Label-resolver helpers ------------------------------------------------
//
// We keep the shell decoupled from `ui.a11y.EnumLabels` (which depends on
// the Compose runtime to produce a String). For the rail we only need the
// `@StringRes Int` so we resolve it locally — same mapping, no coupling.

@StringRes
internal fun scheduleTabLabelRes(tab: ScheduleViewTab): Int = when (tab) {
    ScheduleViewTab.Schedule -> R.string.schedule_view_tab_schedule
    ScheduleViewTab.Day -> R.string.schedule_view_tab_day
    ScheduleViewTab.ThreeDay -> R.string.schedule_view_tab_3day
    ScheduleViewTab.Week -> R.string.schedule_view_tab_week
    ScheduleViewTab.Month -> R.string.schedule_view_tab_month
    ScheduleViewTab.Agenda -> R.string.schedule_view_tab_agenda
    ScheduleViewTab.Year -> R.string.schedule_view_tab_year
}
