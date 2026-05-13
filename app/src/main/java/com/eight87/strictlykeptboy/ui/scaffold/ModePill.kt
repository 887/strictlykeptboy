package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.ui.settings.AppMode
import com.eight87.strictlykeptboy.ui.settings.ModePrefs

/**
 * Phase DDD.12 / UI-TT — always-visible mode pill in app chrome.
 *
 * Renders the current `AppMode` (free / strictly-kept) as a compact
 * pill. Long-press surfaces the "transition my mode" affordance per
 * D.86 / UI-TT.2; the 24h cooling-off CONFIRMATION (typed phrase)
 * gates the actual mode flip per UI-TT.3 / D.86.
 *
 * SOLID:
 *  - **S:** Only the pill + transition modal. Mode persistence lives
 *    in [ModePrefs]; we just observe + drive.
 *  - **I:** Takes [ModePrefs] only; no other graph dependency leaks.
 *  - **O:** Adding a new variant to [AppMode] forces a `when` arm here.
 */
const val TestTagModePill = "ModePill"
const val TestTagModePillTransitionModal = "ModePillTransitionModal"
const val TestTagModePillTypedInput = "ModePillTypedInput"
const val TestTagModePillConfirmButton = "ModePillConfirmButton"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ModePill(prefs: ModePrefs, modifier: Modifier = Modifier) {
    val state by prefs.state.collectAsState()
    var showTransition by rememberSaveable { mutableStateOf(false) }

    val label = when (state.mode) {
        AppMode.Free -> "free"
        AppMode.StrictlyKept -> "kept"
    }
    val icon = when (state.mode) {
        AppMode.Free -> Icons.Filled.LockOpen
        AppMode.StrictlyKept -> Icons.Filled.Lock
    }
    val a11y = "mode: $label"

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(
                onClick = { /* tap — affordance is long-press per UI-TT.2 */ },
                onLongClick = { showTransition = true },
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(TestTagModePill)
            .semantics { contentDescription = a11y },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.height(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

    if (showTransition) {
        TransitionModal(
            currentMode = state.mode,
            onCancel = { showTransition = false },
            onConfirmKept = { prefs.setMode(AppMode.StrictlyKept); showTransition = false },
            onConfirmFree = { prefs.setMode(AppMode.Free); showTransition = false },
        )
    }
}

@Composable
private fun TransitionModal(
    currentMode: AppMode,
    onCancel: () -> Unit,
    onConfirmKept: () -> Unit,
    onConfirmFree: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val phrase = ModePrefs.FREE_CONFIRMATION_PHRASE
    val needsTypedConfirm = currentMode == AppMode.StrictlyKept
    val canFire = !needsTypedConfirm || typed.trim() == phrase

    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(TestTagModePillTransitionModal),
        title = {
            Text(
                when (currentMode) {
                    AppMode.Free -> "Switch to strictly-kept?"
                    AppMode.StrictlyKept -> "Leave strictly-kept?"
                },
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (currentMode) {
                    AppMode.Free -> Text(
                        "Hand the keys to your dom. You can always switch back.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppMode.StrictlyKept -> {
                        Text(
                            "Are you sure you want to leave this dynamic? " +
                                "Type \"$phrase\" to confirm. No dom can block this.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTagModePillTypedInput),
                            singleLine = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (currentMode == AppMode.StrictlyKept) onConfirmFree else onConfirmKept,
                enabled = canFire,
                modifier = Modifier.testTag(TestTagModePillConfirmButton),
            ) {
                Text(
                    when (currentMode) {
                        AppMode.Free -> "Hand over keys"
                        AppMode.StrictlyKept -> "Leave"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
