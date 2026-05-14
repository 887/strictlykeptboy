package com.eight87.strictlykeptboy.ui.calendars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

const val TestTagCalendarSettingsSheet = "CalendarSettings-Sheet"
const val TestTagCalendarSettingsSave = "CalendarSettings-Save"
const val TestTagCalendarSettingsActiveToggle = "CalendarSettings-ActiveToggle"
const val TestTagCalendarSettingsAddRange = "CalendarSettings-AddRange"

/**
 * Round 2.1.B.4 — calendar-level editor.
 *
 * Edits `activeToggle`, `activeWindows`, `activeHours`, `priority`, and
 * `supersedes` for [calendar]. On save, hands the new
 * (RoutineCalendarConfig, CalendarActivityConfig, SupersedenceConfig)
 * to [onSave]; the host (MainActivity) writes the codec output back to
 * `<repo>/calendars/<id>/calendar.toml` and commits.
 *
 * The sheet is intentionally text-input-driven (date/time pickers are
 * a polish follow-on) so it ships in Round 2.1.B without depending on
 * the M3 date-picker — date strings parse as ISO-8601, weekdays as
 * MON/TUE/.../SUN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsSheet(
    calendar: CalendarMeta,
    onDismiss: () -> Unit,
    onSave: (CalendarSettingsDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagCalendarSettingsSheet),
    ) {
        Body(calendar = calendar, onDismiss = onDismiss, onSave = onSave)
    }
}

@Composable
private fun Body(
    calendar: CalendarMeta,
    onDismiss: () -> Unit,
    onSave: (CalendarSettingsDraft) -> Unit,
) {
    var active by remember { mutableStateOf(calendar.activeToggle) }
    var priority by remember { mutableStateOf(calendar.priority.toString()) }
    var windows by remember {
        mutableStateOf(
            calendar.activeWindows.mapNotNull { r ->
                r.endInclusive?.let { WindowDraft(r.start.toString(), it.toString()) }
                    ?: WindowDraft(r.start.toString(), "")
            },
        )
    }
    var hours by remember {
        mutableStateOf(
            calendar.activeHours.map { h ->
                HourDraft(h.day.name.take(3), h.from.toString(), h.to.toString())
            },
        )
    }
    var supersedes by remember {
        mutableStateOf(calendar.supersedes.joinToString(",") { it.id })
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(calendar.displayName, style = MaterialTheme.typography.titleLarge)
        Text("repo: ${calendar.repo.id}", style = MaterialTheme.typography.labelSmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active", modifier = Modifier.weight(1f))
            Switch(
                checked = active,
                onCheckedChange = { active = it },
                modifier = Modifier.testTag(TestTagCalendarSettingsActiveToggle),
            )
        }

        OutlinedTextField(
            value = priority,
            onValueChange = { priority = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("Priority") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Active windows", style = MaterialTheme.typography.titleSmall)
        windows.forEachIndexed { idx, w ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = w.from,
                    onValueChange = { v -> windows = windows.toMutableList().also { it[idx] = w.copy(from = v) } },
                    label = { Text("From (YYYY-MM-DD)") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = w.to,
                    onValueChange = { v -> windows = windows.toMutableList().also { it[idx] = w.copy(to = v) } },
                    label = { Text("To (blank = ∞)") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    windows = windows.toMutableList().also { it.removeAt(idx) }
                }) { Text("×") }
            }
        }
        OutlinedButton(
            onClick = { windows = windows + WindowDraft("", "") },
            modifier = Modifier.testTag(TestTagCalendarSettingsAddRange),
        ) { Text("+ add range") }

        Text("Active hours (per weekday)", style = MaterialTheme.typography.titleSmall)
        hours.forEachIndexed { idx, h ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = h.day,
                    onValueChange = { v -> hours = hours.toMutableList().also { it[idx] = h.copy(day = v.uppercase().take(3)) } },
                    label = { Text("Day") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = h.from,
                    onValueChange = { v -> hours = hours.toMutableList().also { it[idx] = h.copy(from = v) } },
                    label = { Text("From") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = h.to,
                    onValueChange = { v -> hours = hours.toMutableList().also { it[idx] = h.copy(to = v) } },
                    label = { Text("To") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    hours = hours.toMutableList().also { it.removeAt(idx) }
                }) { Text("×") }
            }
        }
        OutlinedButton(onClick = { hours = hours + HourDraft("MON", "09:00", "17:00") }) {
            Text("+ add hours")
        }

        OutlinedTextField(
            value = supersedes,
            onValueChange = { supersedes = it },
            label = { Text("Supersedes (comma-separated cal IDs)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    onSave(
                        toDraft(
                            calendar = calendar,
                            active = active,
                            priorityStr = priority,
                            windows = windows,
                            hours = hours,
                            supersedes = supersedes,
                        ),
                    )
                },
                modifier = Modifier.weight(1f).testTag(TestTagCalendarSettingsSave),
            ) { Text("Save") }
        }
    }
}

private data class WindowDraft(val from: String, val to: String)
private data class HourDraft(val day: String, val from: String, val to: String)

/** Parsed result handed to [CalendarSettingsSheet]'s onSave. */
data class CalendarSettingsDraft(
    val calendar: CalendarMeta,
    val activeToggle: Boolean,
    val priority: Int,
    val activeWindows: List<CalendarActivityConfig.DateRange>,
    val activeHours: List<CalendarActivityConfig.HourRange>,
    val supersedes: List<String>,
)

private fun toDraft(
    calendar: CalendarMeta,
    active: Boolean,
    priorityStr: String,
    windows: List<WindowDraft>,
    hours: List<HourDraft>,
    supersedes: String,
): CalendarSettingsDraft {
    val parsedWindows = windows.mapNotNull { w ->
        val from = runCatching { LocalDate.parse(w.from) }.getOrNull() ?: return@mapNotNull null
        val to = if (w.to.isBlank()) {
            // "infinite end" sentinel — we store as far-future. The
            // resolver checks `contains` against a fixed date, so 9999
            // matches the user's "until forever" intent.
            LocalDate.of(9999, 12, 31)
        } else runCatching { LocalDate.parse(w.to) }.getOrNull() ?: return@mapNotNull null
        if (to.isBefore(from)) null else CalendarActivityConfig.DateRange(from, to)
    }
    val parsedHours = hours.mapNotNull { h ->
        val day = parseDayOfWeek(h.day) ?: return@mapNotNull null
        val from = runCatching { LocalTime.parse(h.from) }.getOrNull() ?: return@mapNotNull null
        val to = runCatching { LocalTime.parse(h.to) }.getOrNull() ?: return@mapNotNull null
        CalendarActivityConfig.HourRange(day, from, to)
    }
    val parsedSupersedes = supersedes.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return CalendarSettingsDraft(
        calendar = calendar,
        activeToggle = active,
        priority = priorityStr.toIntOrNull() ?: calendar.priority,
        activeWindows = parsedWindows,
        activeHours = parsedHours,
        supersedes = parsedSupersedes,
    )
}

private fun parseDayOfWeek(raw: String): DayOfWeek? = when (raw.trim().uppercase()) {
    "MON", "MONDAY" -> DayOfWeek.MONDAY
    "TUE", "TUES", "TUESDAY" -> DayOfWeek.TUESDAY
    "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY
    "THU", "THUR", "THURS", "THURSDAY" -> DayOfWeek.THURSDAY
    "FRI", "FRIDAY" -> DayOfWeek.FRIDAY
    "SAT", "SATURDAY" -> DayOfWeek.SATURDAY
    "SUN", "SUNDAY" -> DayOfWeek.SUNDAY
    else -> null
}
