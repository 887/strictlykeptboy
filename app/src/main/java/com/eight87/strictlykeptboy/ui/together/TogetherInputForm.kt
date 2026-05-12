package com.eight87.strictlykeptboy.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

const val TestTagTogetherForm = "TogetherForm"
const val TestTagTogetherRepoChipPrefix = "TogetherRepoChip-"
const val TestTagTogetherDowChipPrefix = "TogetherDowChip-"
const val TestTagTogetherSubmit = "TogetherSubmit"
const val TestTagTogetherEmptyRepos = "TogetherEmptyRepos"

private val DateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Phase N.1 — input form. Stateless; the caller (Pane) owns the
 * [TogetherInputState] and the callbacks. R.X.7 — receives only what
 * it needs, not a god-state holder.
 */
@Composable
fun TogetherInputForm(
    repoOptions: List<TogetherRepoOption>,
    state: TogetherInputState,
    onToggleRepo: (String) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onUpdate: ((TogetherInputState) -> TogetherInputState) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTagTogetherForm)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.together_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (repoOptions.isEmpty()) {
            Text(
                text = stringResource(R.string.together_empty_no_repos),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(TestTagTogetherEmptyRepos),
            )
            return@Column
        }

        // Repo picker
        Text(stringResource(R.string.together_section_repos), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repoOptions.forEach { opt ->
                FilterChip(
                    selected = opt.repoId in state.selectedRepoIds,
                    onClick = { onToggleRepo(opt.repoId) },
                    label = { Text(opt.displayName) },
                    modifier = Modifier.testTag("$TestTagTogetherRepoChipPrefix${opt.repoId}"),
                )
            }
        }

        // Date range
        Text(stringResource(R.string.together_section_date_range), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.startDate.format(DateFmt),
                onValueChange = { v ->
                    runCatching { LocalDate.parse(v, DateFmt) }
                        .getOrNull()?.let { d -> onUpdate { it.copy(startDate = d) } }
                },
                label = { Text(stringResource(R.string.together_field_start_date)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.endDate.format(DateFmt),
                onValueChange = { v ->
                    runCatching { LocalDate.parse(v, DateFmt) }
                        .getOrNull()?.let { d -> onUpdate { it.copy(endDate = d) } }
                },
                label = { Text(stringResource(R.string.together_field_end_date)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        // Duration
        Text(
            text = stringResource(R.string.together_section_duration) + " · " +
                stringResource(R.string.together_field_duration_minutes, state.durationMinutes),
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = state.durationMinutes.toFloat(),
            onValueChange = { v -> onUpdate { it.copy(durationMinutes = v.toInt().coerceIn(TogetherInputState.DURATION_MIN_RANGE)) } },
            valueRange = TogetherInputState.DURATION_MIN_RANGE.first.toFloat()..TogetherInputState.DURATION_MIN_RANGE.last.toFloat(),
            steps = ((TogetherInputState.DURATION_MIN_RANGE.last - TogetherInputState.DURATION_MIN_RANGE.first) / 15) - 1,
        )

        // Day-of-week chips
        Text(stringResource(R.string.together_section_days), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.values().forEach { d ->
                FilterChip(
                    selected = d in state.daysOfWeek,
                    onClick = { onToggleDay(d) },
                    label = { Text(dowLabel(d)) },
                    modifier = Modifier.testTag("$TestTagTogetherDowChipPrefix${d.name}"),
                )
            }
        }

        // Time of day
        Text(stringResource(R.string.together_section_time), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.timeFrom.format(TimeFmt),
                onValueChange = { v ->
                    runCatching { LocalTime.parse(v, TimeFmt) }
                        .getOrNull()?.let { t -> onUpdate { it.copy(timeFrom = t) } }
                },
                label = { Text(stringResource(R.string.together_field_time_from)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.timeTo.format(TimeFmt),
                onValueChange = { v ->
                    runCatching { LocalTime.parse(v, TimeFmt) }
                        .getOrNull()?.let { t -> onUpdate { it.copy(timeTo = t) } }
                },
                label = { Text(stringResource(R.string.together_field_time_to)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Button(
            onClick = onSubmit,
            enabled = state.isSubmittable,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagTogetherSubmit),
        ) {
            Text(stringResource(R.string.together_submit))
        }
    }
}

@Composable
private fun dowLabel(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> stringResource(R.string.together_dow_mon)
    DayOfWeek.TUESDAY -> stringResource(R.string.together_dow_tue)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.together_dow_wed)
    DayOfWeek.THURSDAY -> stringResource(R.string.together_dow_thu)
    DayOfWeek.FRIDAY -> stringResource(R.string.together_dow_fri)
    DayOfWeek.SATURDAY -> stringResource(R.string.together_dow_sat)
    DayOfWeek.SUNDAY -> stringResource(R.string.together_dow_sun)
}
