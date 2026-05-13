package com.eight87.strictlykeptboy.caldav.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.caldav.CalDavMode
import com.eight87.strictlykeptboy.caldav.CalDavProvider

/**
 * Phase Y.2 — `Settings → Repos → <repo> → + Add CalDAV mirror`.
 * Composable shell — observes [vm] state and forwards user actions.
 *
 * Intentionally simple in v1: the provider-aware presets (Google /
 * M365 / iCloud / Nextcloud / custom) are exposed as plain buttons.
 * AVD-smoke confirmation is via the unit-test path through
 * [AddMirrorViewModel] (the Compose surface is a thin shim).
 */
@Composable
fun AddMirrorScreen(
    vm: AddMirrorViewModel,
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add CalDAV mirror — step ${state.step.name}")
        when (state.step) {
            AddMirrorState.Step.PickProvider -> PickProviderStep(onPick = vm::pickProvider)
            AddMirrorState.Step.EnterCredentials -> EnterCredentialsStep(state, onSubmit = vm::enterCredentials)
            AddMirrorState.Step.PickCalendar -> PickCalendarStep(state, onPick = vm::pickCalendar)
            AddMirrorState.Step.PickMode -> PickModeStep(onPick = vm::pickMode)
            AddMirrorState.Step.Confirm -> ConfirmStep(onConfirm = {
                vm.confirm(); onDone()
            })
            AddMirrorState.Step.Done -> Text("Mirror added.")
        }
        state.error?.let { Text("Error: $it") }
    }
}

@Composable
private fun PickProviderStep(onPick: (CalDavProvider) -> Unit) {
    Text("Pick provider")
    for (p in CalDavProvider.entries) {
        OutlinedButton(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
            Text(p.displayName)
        }
    }
}

@Composable
private fun EnterCredentialsStep(state: AddMirrorState, onSubmit: (String, String, String) -> Unit) {
    var server by remember { mutableStateOf(state.serverUrl) }
    var user by remember { mutableStateOf(state.username) }
    var pass by remember { mutableStateOf(state.password) }
    OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Server URL") })
    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username / email") })
    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password / token") })
    Spacer(Modifier.height(4.dp))
    Button(onClick = { onSubmit(server, user, pass) }) { Text("Discover calendars") }
}

@Composable
private fun PickCalendarStep(state: AddMirrorState, onPick: (String, String) -> Unit) {
    Text("Discovered calendars")
    for (cal in state.discoveredCalendars) {
        OutlinedButton(
            onClick = { onPick(cal.href, cal.displayName.lowercase().replace(' ', '-')) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(cal.displayName) }
    }
}

@Composable
private fun PickModeStep(onPick: (CalDavMode) -> Unit) {
    Text("Pick sync mode")
    OutlinedButton(onClick = { onPick(CalDavMode.PULL_ONLY) }) { Text("Pull only (server → repo)") }
    OutlinedButton(onClick = { onPick(CalDavMode.BIDI) }) { Text("Two-way") }
}

@Composable
private fun ConfirmStep(onConfirm: () -> Unit) {
    Button(onClick = onConfirm) { Text("Confirm and add mirror") }
}
