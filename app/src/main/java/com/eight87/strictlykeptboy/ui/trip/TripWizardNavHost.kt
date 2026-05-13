package com.eight87.strictlykeptboy.ui.trip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---- Test tags --------------------------------------------------------------

const val TestTagTripWizard = "TripWizard"
const val TestTagTripWizardBasics = "TripWizard-Basics"
const val TestTagTripWizardTransport = "TripWizard-Transport"
const val TestTagTripWizardAnchors = "TripWizard-Anchors"
const val TestTagTripWizardConfirm = "TripWizard-Confirm"
const val TestTagTripWizardNext = "TripWizard-Next"
const val TestTagTripWizardBack = "TripWizard-Back"
const val TestTagTripWizardMaterialize = "TripWizard-Materialize"
const val TestTagTripWizardDiscardDialog = "TripWizard-DiscardDialog"

private val SCREEN_ORDER: List<TripScreen> = listOf(
    TripScreen.Basics, TripScreen.Transport, TripScreen.Anchors, TripScreen.Confirm,
)

/**
 * Phase CCC.1 / HV-F.1 — quick-trip wizard host.
 *
 * Compact 4-screen flow: Basics → Transport → Anchors → Confirm. Sibling
 * to the lifestyle wizard's `WizardNavHost` (Phase K); shares the
 * bat-mascot per-screen sticker pattern. Materialization is delegated to
 * the [onMaterialize] callback so the host stays UI-only.
 *
 * SOLID notes:
 *  - **S** — owns nothing but screen routing + draft accumulation. Each
 *    screen is a private composable in this file, no cross-screen state.
 *  - **I** — caller passes only the four lambdas it needs to wire
 *    (finish / cancel / materialize) plus an optional [initialDraft]
 *    for HV-F.9 edit-in-flight (deferred; field unused for now).
 *  - **D** — materialization is a `suspend (TripDraft) -> Result<Unit>`
 *    so prod and tests inject independently.
 */
@Composable
fun TripWizardNavHost(
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onMaterialize: suspend (TripDraft) -> Result<Unit>,
    modifier: Modifier = Modifier,
    initialDraft: TripDraft = TripDraft(),
) {
    var draft by remember { mutableStateOf(initialDraft) }
    var current by remember { mutableStateOf(TripScreen.Basics) }
    var showDiscard by remember { mutableStateOf(false) }
    var materializeStatus by remember { mutableStateOf<MaterializeStatus>(MaterializeStatus.Idle) }
    val scope = rememberCoroutineScope()

    fun goNext() {
        val idx = SCREEN_ORDER.indexOf(current)
        if (idx in 0 until SCREEN_ORDER.size - 1) current = SCREEN_ORDER[idx + 1]
    }
    fun goBack() {
        val idx = SCREEN_ORDER.indexOf(current)
        if (idx > 0) current = SCREEN_ORDER[idx - 1]
    }

    BackHandler(enabled = current != TripScreen.Basics) {
        if (draft.hasUserChoices) showDiscard = true else onCancel()
    }

    if (showDiscard) {
        AlertDialog(
            modifier = Modifier.testTag(TestTagTripWizardDiscardDialog),
            onDismissRequest = { showDiscard = false },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onCancel() }) {
                    Text(stringResource(R.string.trip_wizard_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.trip_wizard_discard_keep))
                }
            },
            title = { Text(stringResource(R.string.trip_wizard_discard_title)) },
            text = { Text(stringResource(R.string.trip_wizard_discard_body)) },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().testTag(TestTagTripWizard).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TripProgressRow(currentIndex = SCREEN_ORDER.indexOf(current), total = SCREEN_ORDER.size)
        TripBatMascot(screen = current)

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (current) {
                TripScreen.Basics -> BasicsScreen(draft) { draft = it }
                TripScreen.Transport -> TransportScreen(draft) { draft = it }
                TripScreen.Anchors -> AnchorsScreen(draft) { draft = it }
                TripScreen.Confirm -> ConfirmScreen(draft = draft, status = materializeStatus)
            }
        }

        // Nav buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current != TripScreen.Basics) {
                TextButton(
                    onClick = ::goBack,
                    modifier = Modifier.testTag(TestTagTripWizardBack),
                    enabled = materializeStatus !is MaterializeStatus.Running,
                ) { Text(stringResource(R.string.trip_wizard_back)) }
            }
            if (current != TripScreen.Confirm) {
                Button(
                    onClick = ::goNext,
                    modifier = Modifier.testTag(TestTagTripWizardNext),
                    enabled = canAdvance(draft, current),
                ) { Text(stringResource(R.string.trip_wizard_continue)) }
            } else {
                Button(
                    onClick = {
                        if (materializeStatus is MaterializeStatus.Running) return@Button
                        materializeStatus = MaterializeStatus.Running
                        scope.launch {
                            val r = onMaterialize(draft)
                            materializeStatus = if (r.isSuccess) MaterializeStatus.Done
                            else MaterializeStatus.Failed(r.exceptionOrNull()?.message)
                        }
                    },
                    modifier = Modifier.testTag(TestTagTripWizardMaterialize),
                    enabled = draft.isComplete && materializeStatus !is MaterializeStatus.Running,
                ) { Text(stringResource(R.string.trip_wizard_materialize)) }
            }
        }
    }

    // After successful materialize, navigate out.
    LaunchedEffect(materializeStatus) {
        if (materializeStatus is MaterializeStatus.Done) onFinish()
    }
}

private sealed interface MaterializeStatus {
    data object Idle : MaterializeStatus
    data object Running : MaterializeStatus
    data object Done : MaterializeStatus
    data class Failed(val message: String?) : MaterializeStatus
}

private fun canAdvance(draft: TripDraft, screen: TripScreen): Boolean = when (screen) {
    TripScreen.Basics -> draft.name.isNotBlank() &&
        draft.startDate != null && draft.endDate != null &&
        !draft.endDate.isBefore(draft.startDate) && draft.travelerCount >= 1
    TripScreen.Transport -> true
    TripScreen.Anchors -> true
    TripScreen.Confirm -> false
}

@Composable
private fun TripProgressRow(currentIndex: Int, total: Int) {
    val frac = (currentIndex + 1).toFloat() / total.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Text(
            text = stringResource(R.string.trip_wizard_step_of, currentIndex + 1, total),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun TripBatMascot(screen: TripScreen) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        contentAlignment = ComposeAlign.Center,
    ) {
        Column(horizontalAlignment = ComposeAlign.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.about_bat),
                contentDescription = stringResource(R.string.cd_trip_wizard_mascot, screen.stickerKey),
                modifier = Modifier.size(80.dp),
            )
            Text(
                text = screen.stickerKey,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("TripWizard-StickerKey-${screen.name}"),
            )
        }
    }
}

// ---- Screen 1 — Basics ------------------------------------------------------

@Composable
private fun BasicsScreen(draft: TripDraft, onUpdate: (TripDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagTripWizardBasics),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.trip_wizard_basics_title),
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onUpdate(draft.copy(name = it)) },
            label = { Text(stringResource(R.string.trip_wizard_name_label)) },
            modifier = Modifier.fillMaxWidth().testTag("TripWizard-Name"),
        )
        OutlinedTextField(
            value = draft.destination,
            onValueChange = { onUpdate(draft.copy(destination = it)) },
            label = { Text(stringResource(R.string.trip_wizard_destination_label)) },
            modifier = Modifier.fillMaxWidth().testTag("TripWizard-Destination"),
        )
        // Date pickers — compact: use a simple text-entry "yyyy-mm-dd" for v1.
        // M3 DatePicker is heavyweight; the FAB path uses native DatePicker too.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.startDate?.toString() ?: "",
                onValueChange = { onUpdate(draft.copy(startDate = parseDate(it))) },
                label = { Text(stringResource(R.string.trip_wizard_start_date_label)) },
                modifier = Modifier.weight(1f).testTag("TripWizard-StartDate"),
            )
            OutlinedTextField(
                value = draft.endDate?.toString() ?: "",
                onValueChange = { onUpdate(draft.copy(endDate = parseDate(it))) },
                label = { Text(stringResource(R.string.trip_wizard_end_date_label)) },
                modifier = Modifier.weight(1f).testTag("TripWizard-EndDate"),
            )
        }
        OutlinedTextField(
            value = draft.travelerCount.toString(),
            onValueChange = { txt ->
                val n = txt.toIntOrNull()
                if (n != null && n >= 1) onUpdate(draft.copy(travelerCount = n))
            },
            label = { Text(stringResource(R.string.trip_wizard_travelers_label)) },
            modifier = Modifier.fillMaxWidth().testTag("TripWizard-Travelers"),
        )
        Text(stringResource(R.string.trip_wizard_date_format_hint),
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

// ---- Screen 2 — Transport ---------------------------------------------------

@Composable
private fun TransportScreen(draft: TripDraft, onUpdate: (TripDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagTripWizardTransport),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.trip_wizard_transport_title),
            style = MaterialTheme.typography.titleMedium)
        for (mode in TransportMode.entries) {
            val selected = draft.transport == mode
            Card(
                onClick = { onUpdate(draft.copy(transport = mode)) },
                colors = if (selected) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else CardDefaults.cardColors(),
                modifier = Modifier.fillMaxWidth().testTag("TripWizard-Transport-${mode.id}"),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = ComposeAlign.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = { onUpdate(draft.copy(transport = mode)) })
                    Spacer(Modifier.size(8.dp))
                    Text(modeLabel(mode), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun modeLabel(mode: TransportMode): String = stringResource(
    when (mode) {
        TransportMode.Flight -> R.string.trip_wizard_transport_flight
        TransportMode.Train -> R.string.trip_wizard_transport_train
        TransportMode.Car -> R.string.trip_wizard_transport_car
        TransportMode.Boat -> R.string.trip_wizard_transport_boat
        TransportMode.None -> R.string.trip_wizard_transport_none
    },
)

// ---- Screen 3 — Anchors -----------------------------------------------------

@Composable
private fun AnchorsScreen(draft: TripDraft, onUpdate: (TripDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagTripWizardAnchors),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.trip_wizard_anchors_title),
            style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = ComposeAlign.CenterVertically) {
            Switch(
                checked = draft.includeVacationDaily,
                onCheckedChange = { onUpdate(draft.copy(includeVacationDaily = it)) },
                modifier = Modifier.testTag("TripWizard-Anchors-IncludeDaily"),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.trip_wizard_anchors_include_daily))
        }
        Row(verticalAlignment = ComposeAlign.CenterVertically) {
            Switch(
                checked = draft.packKinkKit,
                onCheckedChange = { onUpdate(draft.copy(packKinkKit = it)) },
                modifier = Modifier.testTag("TripWizard-Anchors-PackKinkKit"),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.trip_wizard_anchors_pack_kink_kit))
        }
        Text(stringResource(R.string.trip_wizard_anchors_hint),
            style = MaterialTheme.typography.labelSmall)
    }
}

// ---- Screen 4 — Confirm -----------------------------------------------------

@Composable
private fun ConfirmScreen(draft: TripDraft, status: MaterializeStatus) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagTripWizardConfirm),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.trip_wizard_confirm_title),
            style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.trip_wizard_confirm_name, draft.name))
                Text(stringResource(R.string.trip_wizard_confirm_destination,
                    draft.destination.ifBlank { "—" }))
                Text(stringResource(R.string.trip_wizard_confirm_window,
                    draft.startDate.toString(), draft.endDate.toString(), draft.durationDays))
                Text(stringResource(R.string.trip_wizard_confirm_transport, draft.transport.id))
                Text(stringResource(R.string.trip_wizard_confirm_travelers, draft.travelerCount))
                Text(stringResource(R.string.trip_wizard_confirm_daily_anchors,
                    if (draft.includeVacationDaily) "yes" else "no"))
                Text(stringResource(R.string.trip_wizard_confirm_pack_kink_kit,
                    if (draft.packKinkKit) "yes" else "no"))
            }
        }
        Text(
            stringResource(R.string.trip_wizard_confirm_reassurance),
            style = MaterialTheme.typography.bodySmall,
        )
        when (status) {
            MaterializeStatus.Running -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.trip_wizard_materializing))
            }
            is MaterializeStatus.Failed -> Text(
                status.message ?: stringResource(R.string.trip_wizard_materialize_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Unit
        }
    }
}
