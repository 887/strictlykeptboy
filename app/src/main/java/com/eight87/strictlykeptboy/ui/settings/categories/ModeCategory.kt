package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.DomPersonaStore
import com.eight87.strictlykeptboy.ui.settings.AppMode
import com.eight87.strictlykeptboy.ui.settings.DomCadence
import com.eight87.strictlykeptboy.ui.settings.KeptBy
import com.eight87.strictlykeptboy.ui.settings.ModePrefs
import kotlinx.coroutines.delay

const val TestTagCatMode = "Cat-Mode"

/**
 * Phase S.11 / 2.1.K — Mode (HV-Q.1 / DDD.1 / D.86).
 *
 * Phase 2.1.K.3 — kept-by-AI vs kept-by-human vs self-keep is now a
 * three-radio control surfacing the on-disk distinction.
 * Phase 2.1.K.4 — D.86 24h cooling-off enforced with a visible
 * countdown + typed-confirmation phrase.
 * Phase 2.1.K.6 — `DomPersonaStore` is the single source of truth.
 * Phase 2.1.K.7 — "Edit personas" routes through the bottom-sheet
 * [com.eight87.strictlykeptboy.ui.settings.DomPersonaPickerSheet].
 */
@Composable
fun ModeCategory(prefs: ModePrefs, modifier: Modifier = Modifier) {
    val state by prefs.state.collectAsState()
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showPersonaSheet by rememberSaveable { mutableStateOf(false) }
    var showShareCta by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    // Use the app's files dir as the persona "home" — on Android there is
    // no canonical $HOME, and DomPersonaStore writes under
    // `<home>/.config/skb/dom-personas/`. Robolectric + production share
    // the same convention via `context.filesDir`.
    val home = remember { context.filesDir.absolutePath }
    val personas = remember(state.personaId, showPersonaSheet) {
        runCatching {
            DomPersonaStore.ensureBuiltins(home)
            DomPersonaStore.list(home)
        }.getOrDefault(DomPersonaStore.BUILTINS)
    }

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
                        AppMode.SelfKeep -> stringResource(R.string.settings_mode_pill_self_keep)
                    },
                )
            },
            modifier = Modifier.testTag("$TestTagCatMode-Pill"),
        )
        Spacer(Modifier.height(12.dp))

        // 2.1.K.3 — KeptBy three-radio control. Visible whenever mode is
        // NOT Free; selecting flips both mode + persona/target via the
        // migration helpers.
        if (state.mode != AppMode.Free) {
            SectionLabel(stringResource(R.string.settings_mode_kept_by_section))
            Column(Modifier.fillMaxWidth()) {
                KeptByRow(
                    selected = state.keptBy == KeptBy.Ai,
                    label = stringResource(R.string.settings_mode_kept_by_ai),
                    testTag = "$TestTagCatMode-KeptBy-Ai",
                    onSelect = {
                        prefs.migrateToKeptByAi(
                            state.personaId ?: DomPersonaStore.BUILTINS.first().id,
                        )
                    },
                )
                KeptByRow(
                    selected = state.keptBy == KeptBy.Human,
                    label = stringResource(R.string.settings_mode_kept_by_human),
                    testTag = "$TestTagCatMode-KeptBy-Human",
                    onSelect = {
                        prefs.migrateToKeptByHuman(state.writeBackTarget ?: "pending")
                        showShareCta = true
                    },
                )
                KeptByRow(
                    selected = state.keptBy == KeptBy.SelfKeep,
                    label = stringResource(R.string.settings_mode_kept_by_self),
                    testTag = "$TestTagCatMode-KeptBy-SelfKeep",
                    onSelect = { prefs.migrateToSelfKeep() },
                )
            }
        }

        // 2.1.K.4 — Mode switch buttons. Free has direct "go strictly-kept";
        // strictly-kept goes through 24h cooling-off + typed phrase.
        when (state.mode) {
            AppMode.Free -> Button(
                onClick = { prefs.migrateToKeptByAi(DomPersonaStore.BUILTINS.first().id) },
                modifier = Modifier.testTag("$TestTagCatMode-SwitchKept"),
            ) {
                Text(stringResource(R.string.settings_mode_switch_to_strictly_kept))
            }
            AppMode.StrictlyKept, AppMode.SelfKeep -> {
                // First tap arms the cooling-off; subsequent taps open the
                // typed-confirmation dialog (gated until 24h elapse).
                val remaining = prefs.coolingOffRemainingMs()
                if (remaining == null) {
                    Button(
                        onClick = { prefs.requestTransitionToFree() },
                        modifier = Modifier.testTag("$TestTagCatMode-SwitchFree"),
                    ) {
                        Text(stringResource(R.string.settings_mode_switch_to_free))
                    }
                } else {
                    CoolingOffCountdown(
                        prefs = prefs,
                        onConfirmReady = { showConfirm = true },
                        onCancel = { prefs.cancelTransitionRequest() },
                    )
                }
            }
        }

        // 2.1.K.6 — Dom persona row, sourced from DomPersonaStore.
        if (state.mode == AppMode.StrictlyKept && state.keptBy == KeptBy.Ai) {
            SectionLabel(stringResource(R.string.settings_mode_persona_section))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                personas.forEach { persona ->
                    FilterChip(
                        selected = state.personaId == persona.id,
                        onClick = { prefs.setPersonaId(persona.id) },
                        label = { Text(persona.label) },
                        modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatMode-Persona-${persona.id}"),
                    )
                }
            }
            OutlinedButton(
                onClick = { showPersonaSheet = true },
                modifier = Modifier.testTag("$TestTagCatMode-EditPersonas"),
            ) {
                Text(stringResource(R.string.settings_mode_edit_personas))
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
            canConfirmNow = prefs.canConfirmFreeTransition(),
            onCancel = { showConfirm = false },
            onConfirm = {
                prefs.confirmTransitionToFree()
                showConfirm = false
            },
        )
    }

    if (showPersonaSheet) {
        com.eight87.strictlykeptboy.ui.settings.DomPersonaPickerSheet(
            home = home,
            currentPersonaId = state.personaId,
            currentCadence = state.cadence,
            onSelectPersona = { prefs.setPersonaId(it) },
            onSelectCadence = { prefs.setCadence(it) },
            onDismiss = { showPersonaSheet = false },
        )
    }

    if (showShareCta) {
        AlertDialog(
            onDismissRequest = { showShareCta = false },
            title = { Text(stringResource(R.string.settings_mode_share_title)) },
            text = { Text(stringResource(R.string.settings_mode_share_body)) },
            confirmButton = {
                TextButton(onClick = { showShareCta = false }) {
                    Text(stringResource(R.string.settings_mode_share_ok))
                }
            },
            modifier = Modifier.testTag("$TestTagCatMode-ShareCta"),
        )
    }
}

@Composable
private fun KeptByRow(
    selected: Boolean,
    label: String,
    testTag: String,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(testTag),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 2.1.K.4 — Visible countdown until 24h elapse. Recomputes once a
 * minute. When the gate opens, the calling site can launch the typed-
 * confirmation dialog.
 */
@Composable
private fun CoolingOffCountdown(
    prefs: ModePrefs,
    onConfirmReady: () -> Unit,
    onCancel: () -> Unit,
) {
    var remainingMs by remember { mutableLongStateOf(prefs.coolingOffRemainingMs() ?: 0L) }
    LaunchedEffect(Unit) {
        while (true) {
            remainingMs = prefs.coolingOffRemainingMs() ?: 0L
            if (remainingMs <= 0L) break
            delay(60_000L)
        }
    }
    val canConfirm = remainingMs == 0L
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (canConfirm) {
                stringResource(R.string.settings_mode_cooling_off_ready)
            } else {
                val hours = remainingMs / (60L * 60_000L)
                val minutes = (remainingMs / 60_000L) % 60L
                stringResource(R.string.settings_mode_cooling_off_countdown, hours, minutes)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("$TestTagCatMode-CoolingOffLabel"),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConfirmReady,
                enabled = canConfirm,
                modifier = Modifier.testTag("$TestTagCatMode-CoolingOffConfirm"),
            ) { Text(stringResource(R.string.settings_mode_switch_to_free)) }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("$TestTagCatMode-CoolingOffCancel"),
            ) { Text(stringResource(R.string.settings_mode_confirm_cancel)) }
        }
    }
}

@Composable
private fun TypedConfirmationDialog(
    canConfirmNow: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val canConfirm = canConfirmNow && typed.trim() == ModePrefs.FREE_CONFIRMATION_PHRASE
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
