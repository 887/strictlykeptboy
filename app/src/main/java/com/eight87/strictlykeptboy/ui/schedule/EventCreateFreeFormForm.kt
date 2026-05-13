package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

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
        OutlinedTextField(
            value = draft.start.toString(),
            onValueChange = {
                runCatching { java.time.OffsetDateTime.parse(it) }
                    .onSuccess { v -> onDraftChange(draft.copy(start = v)) }
            },
            label = { Text(stringResource(R.string.event_create_start_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TestTagEventCreateStartField),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.end.toString(),
            onValueChange = {
                runCatching { java.time.OffsetDateTime.parse(it) }
                    .onSuccess { v -> onDraftChange(draft.copy(end = v)) }
            },
            label = { Text(stringResource(R.string.event_create_end_label)) },
            isError = errors.endNotAfterStart || errors.durationTooLong,
            supportingText = {
                when {
                    errors.endNotAfterStart -> Text(
                        text = stringResource(R.string.event_create_end_before_start),
                        color = MaterialTheme.colorScheme.error,
                    )
                    errors.durationTooLong -> Text(
                        text = stringResource(R.string.event_create_duration_too_long),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TestTagEventCreateEndField),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.event_create_repeat_label), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
        ) {
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
        ) {
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
