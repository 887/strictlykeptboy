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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
const val TestTagOverlayPickerZoom = "Overlay-PickerZoom"
/** Round 2.22 / Fix 3 — inline priority editor on each row. */
const val TestTagOverlayPickerPriority = "Overlay-PickerPriority"

/**
 * Round 2.21 Phase C.2 — full-screen overlay picker destination.
 *
 * Lists every calendar across every repo, grouped under repo headers.
 * Each row: emoji + color dot + display name + visibility toggle + a
 * trailing "..." that fires [onEditCalendar] (host mounts
 * [CalendarSettingsSheet] over the screen).
 *
 * Phase D.4 — each row also carries a 4-stop zoom segmented control
 * binding to [CalendarVisibilityPrefs.setZoom].
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
    modifier: Modifier = Modifier,
) {
    val calendars by calendarsFlow.collectAsState()
    val visState by visibilityPrefs.state.collectAsState()
    val visibilityById = visState.ordered.associateBy { it.repoId to it.id }

    // Group rows by repo id (preserves insertion order from calendarsFlow,
    // which already sorts by repo). Each group head is a single row of its
    // own in the LazyColumn so it scrolls with the list.
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (repoId, calsInRepo) ->
                    item(key = "header-$repoId") {
                        RepoHeader(repoId = repoId)
                    }
                    items(calsInRepo, key = { c -> "row-${c.repo.id}-${c.ref.id}" }) { cal ->
                        val key = cal.repo.id to cal.ref.id
                        val visible = visibilityById[key]?.visible ?: true
                        val zoom = visibilityPrefs.zoomOf(cal.ref.id, cal.repo.id)
                        OverlayRow(
                            calendar = cal,
                            visible = visible,
                            zoom = zoom,
                            onToggle = {
                                visibilityPrefs.setVisible(
                                    id = cal.ref.id,
                                    visible = !visible,
                                    repoId = cal.repo.id,
                                )
                            },
                            onEdit = { onEditCalendar(cal) },
                            onZoom = { level ->
                                visibilityPrefs.setZoom(
                                    id = cal.ref.id,
                                    zoom = level,
                                    repoId = cal.repo.id,
                                )
                            },
                            onPriority = { newPriority ->
                                onPriorityChange(cal, newPriority)
                            },
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
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

@Composable
private fun OverlayRow(
    calendar: CalendarMeta,
    visible: Boolean,
    zoom: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onZoom: (Int) -> Unit,
    onPriority: (Int) -> Unit,
) {
    val tagId = "${calendar.repo.id}-${calendar.ref.id}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("$TestTagOverlayPickerRow-$tagId"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji column (fixed width so labels align).
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (!calendar.emoji.isNullOrBlank()) {
                    Text(text = calendar.emoji!!, style = MaterialTheme.typography.titleMedium)
                }
            }
            // Color dot.
            val tint = calendar.colorSeed?.let {
                Color(0xFF000000.toInt() or (it and 0x00FFFFFF))
            } ?: MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = calendar.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = calendar.repo.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
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
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Edit ${calendar.displayName}",
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        // Round 2.22 / Fix 3 — inline priority editor. User feedback:
        // "priorities are a multiple choice thing rather than a number
        // i can type" — they were reading the zoom segmented control's
        // 40/80/160/320 labels as priority values. Surface priority as
        // a free-text Int input right here on the row, alongside zoom,
        // so it's visible + editable without diving into the per-
        // calendar settings sheet.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var priorityText by remember(calendar.priority) {
                mutableStateOf(calendar.priority.toString())
            }
            val keyboardController = LocalSoftwareKeyboardController.current
            fun commit() {
                val parsed = priorityText.toIntOrNull()
                if (parsed != null && parsed != calendar.priority) {
                    onPriority(parsed)
                } else if (parsed == null) {
                    // Reset to current value on invalid input.
                    priorityText = calendar.priority.toString()
                }
            }
            OutlinedTextField(
                value = priorityText,
                onValueChange = { raw ->
                    // Number-only, 4-char cap.
                    priorityText = raw.filter { it.isDigit() || it == '-' }.take(4)
                },
                label = { Text("Priority") },
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
                    .width(96.dp)
                    .testTag("$TestTagOverlayPickerPriority-$tagId")
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) commit()
                    },
            )
            // Round 2.21 D.4 — four-stop zoom segmented control per row.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .weight(1f)
                    .testTag("$TestTagOverlayPickerZoom-$tagId"),
            ) {
                val stops = listOf(1, 2, 3, 4)
                stops.forEachIndexed { idx, level ->
                    SegmentedButton(
                        selected = zoom == level,
                        onClick = { onZoom(level) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = stops.size),
                    ) {
                        Text(zoomLabel(level), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "higher priority wins overlay tiebreaks · zoom",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun zoomLabel(level: Int): String = when (level) {
    1 -> "40"
    2 -> "80"
    3 -> "160"
    4 -> "320"
    else -> level.toString()
}
