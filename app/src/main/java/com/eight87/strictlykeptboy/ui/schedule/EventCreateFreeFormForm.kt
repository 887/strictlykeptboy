@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.components.WrappingChipRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TestTagEventCreateFreeForm = "EventCreateFreeForm"
const val TestTagEventCreateTitle = "EventCreateTitle"
const val TestTagEventCreateNotes = "EventCreateNotes"
const val TestTagEventCreatePrivateToggle = "EventCreatePrivateToggle"
const val TestTagEventCreateConfirm = "EventCreateConfirm"
const val TestTagEventCreateCalendarChip = "EventCreateCalendarChip"
const val TestTagEventCreateStartField = "EventCreateStartField"
const val TestTagEventCreateEndField = "EventCreateEndField"
const val TestTagEventCreateRepeatChip = "EventCreateRepeatChip"

/**
 * Phase FFF / EC-B — free-form event-create form, stateless.
 *
 * Caller owns [draft], the available [calendars], and submission.
 * Validation errors are surfaced inline as M3 `supporting text`
 * (`isError = true` + helper text under the field). No snackbar
 * validation (EC-B.2 explicitly forbids it).
 *
 * SOLID-S: form rendering + dispatch only. Date/time pickers wired
 * as plain text input for v1 (ISO-8601 format); a date-time-picker
 * sheet is a follow-up under EC-F.
 */
@Composable
fun EventCreateFreeFormForm(
    draft: EventDraft,
    calendars: List<CalendarOption>,
    errors: DraftErrors,
    onDraftChange: (EventDraft) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(16.dp)
            .testTag(TestTagEventCreateFreeForm),
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChange(draft.copy(title = it)) },
            label = { Text(stringResource(R.string.event_create_title_label)) },
            singleLine = true,
            isError = errors.titleEmpty,
            supportingText = {
                if (errors.titleEmpty) {
                    Text(
                        text = stringResource(R.string.event_create_title_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(TestTagEventCreateTitle),
        )
        Spacer(Modifier.height(12.dp))
        DateTimeField(
            label = stringResource(R.string.event_create_start_label),
            value = draft.start,
            onChange = { onDraftChange(draft.copy(start = it)) },
            testTag = TestTagEventCreateStartField,
        )
        Spacer(Modifier.height(8.dp))
        DateTimeField(
            label = stringResource(R.string.event_create_end_label),
            value = draft.end,
            isError = errors.endNotAfterStart || errors.durationTooLong,
            errorText = when {
                errors.endNotAfterStart -> stringResource(R.string.event_create_end_before_start)
                errors.durationTooLong -> stringResource(R.string.event_create_duration_too_long)
                else -> null
            },
            onChange = { onDraftChange(draft.copy(end = it)) },
            testTag = TestTagEventCreateEndField,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.event_create_repeat_label), style = MaterialTheme.typography.labelMedium)
        WrappingChipRow(modifier = Modifier.padding(top = 4.dp)) {
            RecurrencePreset.entries.forEach { p ->
                FilterChip(
                    selected = draft.recurrence == p,
                    onClick = { onDraftChange(draft.copy(recurrence = p)) },
                    label = { Text(p.label()) },
                    modifier = Modifier.testTag("$TestTagEventCreateRepeatChip-${p.name}"),
                )
            }
        }
        if (draft.recurrence == RecurrencePreset.Custom) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.customRRule,
                onValueChange = { onDraftChange(draft.copy(customRRule = it)) },
                label = { Text(stringResource(R.string.event_create_custom_rrule)) },
                placeholder = { Text("FREQ=WEEKLY;BYDAY=MO,WE,FR") },
                singleLine = true,
                isError = errors.customRRuleBlank,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.event_create_calendar_label), style = MaterialTheme.typography.labelMedium)
        WrappingChipRow(modifier = Modifier.padding(top = 4.dp)) {
            calendars.forEach { c ->
                FilterChip(
                    selected = draft.calendarId == c.id,
                    onClick = { onDraftChange(draft.copy(calendarId = c.id)) },
                    label = { Text(c.label()) },
                    modifier = Modifier.testTag("$TestTagEventCreateCalendarChip-${c.id}"),
                )
            }
        }
        if (errors.calendarMissing) {
            Text(
                text = stringResource(R.string.event_create_calendar_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft.notes,
            onValueChange = { onDraftChange(draft.copy(notes = it)) },
            label = { Text(stringResource(R.string.event_create_notes_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .testTag(TestTagEventCreateNotes),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (draft.private) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.event_create_private_label), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.event_create_private_tooltip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.private,
                onCheckedChange = { onDraftChange(draft.copy(private = it)) },
                modifier = Modifier.testTag(TestTagEventCreatePrivateToggle),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onConfirm,
            enabled = !errors.any,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagEventCreateConfirm),
        ) {
            Text(stringResource(R.string.event_create_confirm))
        }
        Spacer(Modifier.height(32.dp))
    }
}

private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/**
 * EC-F — friendly date + time entry. Replaces the raw ISO-8601 text
 * field with two buttons (date / time) showing localized values; each
 * opens the Material3 [DatePicker] / [TimePicker]. Picks preserve the
 * untouched component AND the draft's existing UTC offset, so editing
 * the time never silently shifts the date or zone.
 */
@Composable
private fun DateTimeField(
    label: String,
    value: OffsetDateTime,
    onChange: (OffsetDateTime) -> Unit,
    testTag: String,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { showDate = true },
                modifier = Modifier.weight(1f).testTag("$testTag-Date"),
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(value.format(DATE_FMT), maxLines = 1)
            }
            OutlinedButton(
                onClick = { showTime = true },
                modifier = Modifier.weight(1f).testTag("$testTag-Time"),
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(value.format(TIME_FMT), maxLines = 1)
            }
        }
        if (isError && errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (showDate) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = value.toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(
                            value.withYear(d.year)
                                .withMonth(d.monthValue)
                                .withDayOfMonth(d.dayOfMonth),
                        )
                    }
                    showDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = dpState)
        }
    }
    if (showTime) {
        val tpState = rememberTimePickerState(
            initialHour = value.hour,
            initialMinute = value.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(value.withHour(tpState.hour).withMinute(tpState.minute))
                    showTime = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = { TimePicker(state = tpState) },
        )
    }
}

@Composable
private fun RecurrencePreset.label(): String = when (this) {
    RecurrencePreset.Once -> stringResource(R.string.event_create_repeat_once)
    RecurrencePreset.Daily -> stringResource(R.string.event_create_repeat_daily)
    RecurrencePreset.Weekly -> stringResource(R.string.event_create_repeat_weekly)
    RecurrencePreset.Monthly -> stringResource(R.string.event_create_repeat_monthly)
    RecurrencePreset.Custom -> stringResource(R.string.event_create_repeat_custom)
}

data class CalendarOption(
    val id: String,
    val displayName: String,
    val repoDisplayName: String? = null,
) {
    fun label(): String = repoDisplayName?.let { "$it · $displayName" } ?: displayName
}
