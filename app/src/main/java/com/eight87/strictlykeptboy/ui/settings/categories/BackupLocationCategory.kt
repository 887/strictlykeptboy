package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.prefs.MirrorLocation
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs

const val TestTagCatBackup = "Cat-BackupLocation"
const val TestTagCatBackupPickButton = "Cat-BackupLocation-Pick"
const val TestTagCatBackupChangeButton = "Cat-BackupLocation-Change"
const val TestTagCatBackupRemoveButton = "Cat-BackupLocation-Remove"

/**
 * Round 2.7.B.4-UI — Settings → Behaviour → Backup location.
 *
 * Renders the current [MirrorLocation] and offers:
 *   - [MirrorLocation.None]: blurb + "Pick a folder" button.
 *   - [MirrorLocation.External]: shows label + URI + "Change folder"
 *     and "Remove backup" buttons. (Remove sets prefs back to None.)
 *
 * Per the 2.7.D.1 design call, picking a folder does NOT pop a separate
 * "Apply to existing repos?" dialog — the picker action IS the consent.
 * The launcher in `MainActivity` fires `MirrorReconciler.applyToAll()`
 * + Toasts the count after the prefs flip lands.
 */
@Composable
fun BackupLocationCategory(
    prefs: RepoStoragePrefs,
    onPickFolder: () -> Unit,
    onRemoveFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    CategorySurface(
        testTag = TestTagCatBackup,
        title = stringResource(R.string.settings_category_backup),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.settings_backup_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        when (val loc = state) {
            is MirrorLocation.None -> {
                Text(
                    stringResource(R.string.settings_backup_none_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPickFolder,
                    modifier = Modifier.testTag(TestTagCatBackupPickButton),
                ) {
                    Text(stringResource(R.string.settings_backup_pick_button))
                }
            }
            is MirrorLocation.External -> {
                Text(
                    stringResource(R.string.settings_backup_current_label, loc.label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    loc.treeUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onPickFolder,
                        modifier = Modifier.testTag(TestTagCatBackupChangeButton),
                    ) {
                        Text(stringResource(R.string.settings_backup_change_button))
                    }
                    OutlinedButton(
                        onClick = onRemoveFolder,
                        modifier = Modifier.testTag(TestTagCatBackupRemoveButton),
                    ) {
                        Text(stringResource(R.string.settings_backup_remove_button))
                    }
                }
            }
        }
    }
}
