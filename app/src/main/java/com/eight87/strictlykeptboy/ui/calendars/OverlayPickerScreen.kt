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
    modifier: Modifier = Modifier,
) {
    val calendars by calendarsFlow.collectAsState()
    val visState by visibilityPrefs.state.collectAsState()
    val visibilityById = visState.ordered.associateBy { it.repoId to it.id }

    val grouped = calendars.groupBy { it.repo.id }

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
                grouped.forEach { (repoId, calsInRepo) ->
                    item(key = "header-$repoId") {
                        RepoHeader(repoId = repoId)
                    }
                    items(calsInRepo, key = { c -> "card-${c.repo.id}-${c.ref.id}" }) { cal ->
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
                            onPriority = { newPriority -> onPriorityChange(cal, newPriority) },
                            onColor = { rgb -> onColorChange(cal, rgb) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoHeader(repoId: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag("$TestTagOverlayPickerRepoHeader-$repoId"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = repoId.ifBlank { "—" },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Round 2.23.2 — multi-row card per the D.119 spec.
 *
 *   ┌────────────────────────────────────┐
 *   │ 🦙  Name              [Switch] [⋮] │  ← header
 *   ├────────────────────────────────────┤
 *   │ Color          [▓] yellow      >   │  ← clickable, expands palette
 *   │   (when expanded: 2×6 swatch grid) │
 *   ├────────────────────────────────────┤
 *   │ Priority       [  999  ]           │  ← typeable TextField
 *   ├────────────────────────────────────┤
 *   │ Repo           repo-id             │  ← supplementary
 *   └────────────────────────────────────┘
 */
@Composable
private fun OverlayCard(
    calendar: CalendarMeta,
    visible: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onPriority: (Int) -> Unit,
    onColor: (Int) -> Unit,
) {
    val tagId = "${calendar.repo.id}-${calendar.ref.id}"
    var paletteOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$TestTagOverlayPickerRow-$tagId"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row: emoji + name + Switch + ⋮
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
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
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
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Edit ${calendar.displayName}",
                    )
                }
            }
            HorizontalDivider()
            // Color row — clickable, expands swatch palette.
            ColorRow(
                tagId = tagId,
                colorSeed = calendar.colorSeed,
                expanded = paletteOpen,
                onToggleExpanded = { paletteOpen = !paletteOpen },
                onPick = { rgb ->
                    paletteOpen = false
                    onColor(rgb)
                },
            )
            HorizontalDivider()
            // Priority row.
            PriorityRow(
                tagId = tagId,
                currentPriority = calendar.priority,
                onPriority = onPriority,
            )
            HorizontalDivider()
            // Repo row — supplementary chrome.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Repo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    text = calendar.repo.id.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ColorRow(
    tagId: String,
    colorSeed: Int?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val tint = colorSeed?.let {
        Color(0xFF000000.toInt() or (it and 0x00FFFFFF))
    } ?: MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .testTag("$TestTagOverlayPickerColorRow-$tagId")
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Color",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = identitySwatchName(colorSeed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Hide palette" else "Choose color",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IdentitySwatches.take(6).forEach { rgb ->
                        PickerSwatch(rgb = rgb, selected = colorSeed == rgb, tagId = tagId, onClick = { onPick(rgb) })
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IdentitySwatches.drop(6).forEach { rgb ->
                        PickerSwatch(rgb = rgb, selected = colorSeed == rgb, tagId = tagId, onClick = { onPick(rgb) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSwatch(rgb: Int, selected: Boolean, tagId: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF000000.toInt() or rgb))
            .clickable(onClick = onClick)
            .testTag("$TestTagOverlayPickerColorSwatchPrefix$tagId-%06X".format(rgb and 0xFFFFFF)),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PriorityRow(
    tagId: String,
    currentPriority: Int,
    onPriority: (Int) -> Unit,
) {
    var priorityText by remember(currentPriority) {
        mutableStateOf(currentPriority.toString())
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    fun commit() {
        val parsed = priorityText.toIntOrNull()
        if (parsed != null && parsed != currentPriority) {
            onPriority(parsed)
        } else if (parsed == null) {
            priorityText = currentPriority.toString()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Priority",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        OutlinedTextField(
            value = priorityText,
            onValueChange = { raw ->
                priorityText = raw.filter { it.isDigit() || it == '-' }.take(4)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    commit()
                    keyboardController?.hide()
                },
            ),
            modifier = Modifier
                .width(120.dp)
                .testTag("$TestTagOverlayPickerPriority-$tagId")
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) commit()
                },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "higher wins tiebreaks",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
