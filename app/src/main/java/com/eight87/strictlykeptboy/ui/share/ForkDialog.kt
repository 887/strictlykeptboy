package com.eight87.strictlykeptboy.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagForkDialog = "ForkDialog"
const val TestTagForkDialogConfirmInput = "Fork-ConfirmInput"
const val TestTagForkDialogConfirmButton = "Fork-Confirm"
const val TestTagForkDialogCancelButton = "Fork-Cancel"

/**
 * Phase SS.3 — migration UI for the simplified → own fork.
 *
 * Explains the trade-off (no auto-update, can add own entries, original
 * preserved as read-only reference), then requires the user to type the
 * source repo's display name to confirm. Disables the confirm button
 * until the typed name matches exactly.
 *
 * Stateless wrt persistence — [onConfirm] receives no payload; the
 * caller already knows what to fork. Dialog lives here so it can be
 * shown from Settings → Repos → <shared-repo>.
 */
@Composable
fun ForkDialog(
    sourceRepoName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    val canConfirm = typed.trim() == sourceRepoName.trim() && sourceRepoName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TestTagForkDialog),
        title = { Text(stringResource(R.string.fork_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.fork_dialog_blurb))
                Text(
                    stringResource(R.string.fork_dialog_tradeoff_will_change),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(stringResource(R.string.fork_dialog_tradeoff_no_auto_update))
                Text(stringResource(R.string.fork_dialog_tradeoff_own_entries))
                Text(stringResource(R.string.fork_dialog_tradeoff_reference))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.fork_dialog_confirm_label)) },
                    placeholder = { Text(sourceRepoName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTagForkDialogConfirmInput),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
                modifier = Modifier.testTag(TestTagForkDialogConfirmButton),
            ) { Text(stringResource(R.string.fork_dialog_confirm_button)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTagForkDialogCancelButton),
            ) { Text(stringResource(R.string.fork_dialog_cancel_button)) }
        },
    )
}
