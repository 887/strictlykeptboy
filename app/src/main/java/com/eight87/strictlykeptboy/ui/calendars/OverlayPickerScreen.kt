package com.eight87.strictlykeptboy.ui.calendars

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
// (removed: RoundedCornerShape — card now uses M3 Card's default shape)
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import kotlinx.coroutines.flow.StateFlow

const val TestTagOverlayPickerScreen = "Overlay-PickerScreen"
const val TestTagOverlayPickerRow = "Overlay-PickerRow"
const val TestTagOverlayPickerToggle = "Overlay-PickerToggle"
const val TestTagOverlayPickerEdit = "Overlay-PickerEdit"
const val TestTagOverlayPickerRepoHeader = "Overlay-PickerRepoHeader"
/** Round 2.22 / Fix 3 — inline priority editor on each row. */
const val TestTagOverlayPickerPriority = "Overlay-PickerPriority"
/** Round 2.23.2 — inline color row on each card. Tap = expand swatches. */
const val TestTagOverlayPickerColorRow = "Overlay-PickerColorRow"
const val TestTagOverlayPickerColorSwatchPrefix = "Overlay-PickerColorSwatch-"
/** Round 2.23.5 — single top-of-screen explainer (replaces per-row helper). */
const val TestTagOverlayPickerExplainer = "Overlay-PickerExplainer"
/** Round 2.23.5 — second-row repo display-name on each card. */
const val TestTagOverlayPickerRepoName = "Overlay-PickerRepoName"
/** Round 2.23.5 — free-form hex input inside the color expansion. */
const val TestTagOverlayPickerHexInput = "Overlay-PickerHexInput"

/**
 * Round 2.21 Phase C.2 — full-screen overlay picker destination.
 *
 * Round 2.23.2 redesign (per D.119): each calendar is a multi-row M3
 * [Card] mirroring `RepoSettingsScreen`'s `SectionCard` pattern. The
 * header row carries emoji + name + visibility Switch + ⋮ (full
 * identity editor). A clickable Color row opens an inline 12-swatch
 * palette (reuses [ColorSwatch] + [IdentitySwatches] from
 * [CalendarSettingsSheet]). A Priority row hosts the existing typeable
 * Int editor. The Repo row is supplementary chrome.
 *
 * The per-overlay 4-stop zoom segmented control is RETIRED from this
 * surface — the top-of-Day `ZoomLevelRow` (D.115) is now the single
 * user-facing zoom entry-point. Per-overlay zoom storage stays (it
 * still backs the max-of-visible fallback when globalZoomOverride is
 * null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayPickerScreen(
    calendarsFlow: StateFlow<List<CalendarMeta>>,
    visibilityPrefs: CalendarVisibilityPrefs,
    onBack: () -> Unit,
    onEditCalendar: (CalendarMeta) -> Unit,
    /**
     * Round 2.22 / Fix 3 — inline priority writer. Fires on focus
     * loss / IME done. Default no-op so previews + tests don't have
     * to plumb it; MainActivity wires the real
     * [CalendarSettingsWriter.writePriority].
     */
    onPriorityChange: (CalendarMeta, Int) -> Unit = { _, _ -> },
    /**
     * Round 2.23.2 — inline color writer for the Color row. Receives
     * the chosen `0xRRGGBB` int. Default no-op for previews + tests;
     * MainActivity wires [CalendarSettingsWriter.writeColorSeed].
     */
    onColorChange: (CalendarMeta, Int) -> Unit = { _, _ -> },
    /**
     * Round 2.23.5 / Fix 3 — resolves `cal.repo.id` (GUID) to a friendly
     * display name (e.g. "demo · richdemo"). Return `null` for foreign /
     * unknown UIDs; the card falls back to the truncated GUID. Default
     * no-op for previews + tests.
     */
    repoDisplayNameFor: (String) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    val calendars by calendarsFlow.collectAsState()
    val visState by visibilityPrefs.state.collectAsState()
    val visibilityById = visState.ordered.associateBy { it.repoId to it.id }

    // Sort by priority descending — highest-priority overlay first.
    // Ties break on displayName for stable ordering.
    val sortedCalendars = calendars.sortedWith(
        compareByDescending<CalendarMeta> { it.priority }.thenBy { it.displayName },
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag(TestTagOverlayPickerScreen),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Overlays") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
            // Single top-of-screen explainer. Detail editor opens via the
            // row's chevron — repo / color / priority all live there.
            Text(
                text = "Sorted by priority — higher number wins overlay tiebreaks. " +
                    "Tap a row to edit color, priority, and active windows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(TestTagOverlayPickerExplainer),
            )
            if (calendars.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No calendars yet. Run the lifestyle wizard to seed some.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(sortedCalendars, key = { c -> "card-${c.repo.id}-${c.ref.id}" }) { cal ->
                    val key = cal.repo.id to cal.ref.id
                    val visible = visibilityById[key]?.visible ?: true
                    OverlayCard(
                        calendar = cal,
                        visible = visible,
                        onToggle = {
                            visibilityPrefs.setVisible(
                                id = cal.ref.id,
                                visible = !visible,
                                repoId = cal.repo.id,
                            )
                        },
                        onEdit = { onEditCalendar(cal) },
                    )
                }
            }
        }
    }
}

/**
 * Single-row card. Color + priority are surfaced inline as read-only
 * affordances next to the toggle; tapping the row opens the full-screen
 * editor (CalendarSettingsSheet) where repo / color / priority /
 * windows / supersedes are editable. The `>` chevron mirrors the
 * row-tap so screen-readers + thumb-stretchers both have an explicit
 * target.
 */
@Composable
private fun OverlayCard(
    calendar: CalendarMeta,
    visible: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val tagId = "${calendar.repo.id}-${calendar.ref.id}"
    val dotTint = calendar.colorSeed?.let {
        Color(0xFF000000.toInt() or (it and 0x00FFFFFF))
    } ?: MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("$TestTagOverlayPickerRow-$tagId"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (!calendar.emoji.isNullOrBlank()) {
                    Text(text = calendar.emoji!!, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotTint),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "priority ${calendar.priority}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = visible,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("$TestTagOverlayPickerToggle-$tagId"),
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("$TestTagOverlayPickerEdit-$tagId"),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Edit ${calendar.displayName}",
                )
            }
        }
    }
}
