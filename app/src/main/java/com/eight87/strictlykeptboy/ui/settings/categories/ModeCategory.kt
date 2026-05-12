package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.settings.AppMode
import com.eight87.strictlykeptboy.ui.settings.DomCadence
import com.eight87.strictlykeptboy.ui.settings.ModePrefs

const val TestTagCatMode = "Cat-Mode"

/**
 * Phase S.11 — Mode (HV-Q.1 / DDD.1 / D.86).
 *
 * Mode pill, transition affordance with 24h cooling-off confirmation
 * flow (typed-confirmation, D.86 — dom cannot block), dom-persona
 * picker, dom-cadence selector.
 *
 * The "switch to free" flow is deliberately surfaced — D.86 mandates
 * this affordance is reachable from the most-kept UI state and not
 * buried.
 */
@Composable
fun ModeCategory(prefs: ModePrefs, modifier: Modifier = Modifier) {
    val state by prefs.state.collectAsState()
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    CategorySurface(
        testTag = TestTagCatMode,
        title = stringResource(R.string.settings_category_mode),
        modifier = modifier,
    ) {
        AssistChip(
            onClick = {},
            label = {
                Text(
                    when (state.mode) {
                        AppMode.Free -> stringResource(R.string.settings_mode_pill_free)
                        AppMode.StrictlyKept -> stringResource(R.string.settings_mode_pill_strictly_kept)
                    },
                )
            },
            modifier = Modifier.testTag("$TestTagCatMode-Pill"),
        )
        Spacer(Modifier.height(12.dp))
        when (state.mode) {
            AppMode.Free -> Button(
                onClick = { prefs.setMode(AppMode.StrictlyKept) },
                modifier = Modifier.testTag("$TestTagCatMode-SwitchKept"),
            ) {
                Text(stringResource(R.string.settings_mode_switch_to_strictly_kept))
            }
            AppMode.StrictlyKept -> Button(
                onClick = { showConfirm = true },
                modifier = Modifier.testTag("$TestTagCatMode-SwitchFree"),
            ) {
                Text(stringResource(R.string.settings_mode_switch_to_free))
            }
        }

        SectionLabel(stringResource(R.string.settings_mode_persona_section))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            prefs.availablePersonas().forEach { persona ->
                FilterChip(
                    selected = state.personaId == persona.id,
                    onClick = { prefs.setPersonaId(persona.id) },
                    label = { Text(persona.label) },
                    modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatMode-Persona-${persona.id}"),
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_mode_cadence_section))
        Row {
            val items = listOf(
                DomCadence.Realtime to R.string.settings_mode_cadence_realtime,
                DomCadence.EndOfDay to R.string.settings_mode_cadence_eod,
                DomCadence.Weekly to R.string.settings_mode_cadence_weekly,
            )
            items.forEach { (cadence, label) ->
                FilterChip(
                    selected = state.cadence == cadence,
                    onClick = { prefs.setCadence(cadence) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatMode-Cadence-${cadence.name}"),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showConfirm) {
        TypedConfirmationDialog(
            onCancel = { showConfirm = false },
            onConfirm = {
                prefs.setMode(AppMode.Free)
                showConfirm = false
            },
        )
    }
}

@Composable
private fun TypedConfirmationDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val canConfirm = typed.trim() == ModePrefs.FREE_CONFIRMATION_PHRASE
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_mode_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_mode_confirm_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(stringResource(R.string.settings_mode_confirm_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("$TestTagCatMode-ConfirmInput"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
                modifier = Modifier.testTag("$TestTagCatMode-ConfirmButton"),
            ) {
                Text(stringResource(R.string.settings_mode_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.settings_mode_confirm_cancel))
            }
        },
        modifier = Modifier.testTag("$TestTagCatMode-ConfirmDialog"),
    )
}
