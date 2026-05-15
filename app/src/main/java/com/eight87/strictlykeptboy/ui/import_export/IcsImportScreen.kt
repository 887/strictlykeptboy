package com.eight87.strictlykeptboy.ui.import_export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.system.ParsedIcs
import com.eight87.strictlykeptboy.system.ParsedIcsEvent

/**
 * Round 2.18.E.8 — `.ics` import preview + destination picker.
 *
 * Surfaces:
 *
 *   - per-VEVENT preview row (title / start / location / attendees /
 *     reminders summary).
 *   - destination dropdown listing both skb-repo calendars (writes
 *     through `EntityWriter`) AND external write-eligible calendars
 *     from `CalendarContract` (writes through
 *     `CalendarContractWriter.insertEvent`).
 *   - Save / Cancel CTAs. Save returns the picked destination + the
 *     parsed event list to the caller; the caller (MainActivity)
 *     performs the actual write on `Dispatchers.IO` and shows a Toast
 *     summary.
 *
 * Per R.X.7 / ISP: the screen takes only the data + callbacks it
 * needs — no god-state.
 */
const val TestTagIcsImportScreen = "IcsImportScreen"
const val TestTagIcsImportDestinationDropdown = "IcsImportDestinationDropdown"

sealed interface IcsImportDestination {
    val label: String

    /** Write into a skb repo via `EntityWriter`. */
    data class Repo(
        val repoId: String,
        val displayName: String,
        val calendarId: String,
    ) : IcsImportDestination {
        override val label: String get() = "$displayName (skb)"
    }

    /** Write into a `CalendarContract` calendar via `CalendarContractWriter`. */
    data class External(
        val calendarId: Long,
        val displayName: String,
        val accountName: String,
        val isPrimary: Boolean = false,
    ) : IcsImportDestination {
        override val label: String get() = "$displayName · $accountName"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IcsImportScreen(
    parsed: ParsedIcs,
    destinations: List<IcsImportDestination>,
    defaultDestination: IcsImportDestination? = destinations
        .filterIsInstance<IcsImportDestination.External>()
        .firstOrNull { it.isPrimary }
        ?: destinations.firstOrNull(),
    onSave: (IcsImportDestination) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by remember { mutableStateOf(defaultDestination) }
    var dropdownOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag(TestTagIcsImportScreen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Import .ics (${parsed.events.size} event${if (parsed.events.size == 1) "" else "s"})",
            style = MaterialTheme.typography.headlineSmall,
        )

        // Per-event preview, scrolled if many.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parsed.events.forEach { ev -> EventPreviewRow(ev) }
            if (parsed.warnings.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Warnings",
                    style = MaterialTheme.typography.titleSmall,
                )
                parsed.warnings.forEach { w ->
                    Text(
                        text = w,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        HorizontalDivider()

        // Destination dropdown.
        Box {
            OutlinedTextField(
                value = chosen?.label ?: "Pick a destination",
                onValueChange = {},
                readOnly = true,
                label = { Text("Save to") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagIcsImportDestinationDropdown),
            )
            // Invisible click surface to open the menu.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp),
            ) {
                OutlinedButton(
                    onClick = { dropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose destination") }
            }
            DropdownMenu(
                expanded = dropdownOpen,
                onDismissRequest = { dropdownOpen = false },
            ) {
                destinations.forEach { dest ->
                    DropdownMenuItem(
                        text = { Text(dest.label) },
                        onClick = {
                            chosen = dest
                            dropdownOpen = false
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(
                enabled = chosen != null && parsed.events.isNotEmpty(),
                onClick = { chosen?.let(onSave) },
            ) { Text("Save") }
        }
    }
}

@Composable
private fun EventPreviewRow(ev: ParsedIcsEvent) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = ev.summary, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${ev.start} → ${ev.end}",
            style = MaterialTheme.typography.bodySmall,
        )
        ev.location?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
        if (ev.rrule != null) {
            Text(
                text = "Repeats: ${ev.rrule}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (ev.attendees.isNotEmpty()) {
            Text(
                text = "Attendees: " + ev.attendees.joinToString { it.commonName ?: it.email ?: "?" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (ev.reminders.isNotEmpty()) {
            Text(
                text = "Reminders: " + ev.reminders.joinToString { "${it.minutesBeforeStart}m" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
