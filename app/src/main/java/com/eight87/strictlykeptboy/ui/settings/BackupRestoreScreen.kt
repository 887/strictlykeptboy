package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.eight87.strictlykeptboy.ui.components.DestructiveConfirmDialog

const val TestTagBackupRestoreScreen = "BackupRestoreScreen"
const val TestTagBackupRestoreExportRow = "BackupRestore-ExportRow"
const val TestTagBackupRestoreFolderRow = "BackupRestore-RestoreFolderRow"
const val TestTagBackupRestoreArchiveRow = "BackupRestore-RestoreArchiveRow"

private enum class RestorePending { None, FromFolder, FromArchive }

/**
 * Round 2.17 Phase F.3 / Phase G.3 — Backup/Restore screen.
 *
 *  - Export row (Phase F): launches the SAF `CreateDocument` picker via
 *    [onExportBackup], streams the parent through `BackupArchiver`.
 *  - Restore from current folder (Phase G.3): triggers a `rescanParent`
 *    + `RepoStore.replaceAll`. Wrapped in [DestructiveConfirmDialog].
 *  - Restore from backup archive (Phase G.3): fires the SAF
 *    `OpenDocument` picker via [onPickRestoreArchive]; the caller
 *    parks a handler that performs the wipe + extract + replaceAll.
 *
 * Both restore actions go through the same destructive-confirm dialog
 * per D-2.17.g. The dialog requires the user to type `"restore"` AND
 * press-and-hold the red CTA for 3 s before either flow runs.
 */
@Composable
fun BackupRestoreScreen(
    onExportBackup: () -> Unit = {},
    onRestoreFromFolder: () -> Unit = {},
    onPickRestoreArchive: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf(RestorePending.None) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag(TestTagBackupRestoreScreen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.backuprestore_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.backuprestore_export_title)) },
            supportingContent = {
                Text(stringResource(R.string.backuprestore_export_subtitle))
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagBackupRestoreExportRow)
                .clickable(onClick = onExportBackup),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.backuprestore_restore_folder_title))
            },
            supportingContent = {
                Text(stringResource(R.string.backuprestore_restore_folder_subtitle))
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagBackupRestoreFolderRow)
                .clickable { pending = RestorePending.FromFolder },
        )
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.backuprestore_restore_archive_title))
            },
            supportingContent = {
                Text(stringResource(R.string.backuprestore_restore_archive_subtitle))
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagBackupRestoreArchiveRow)
                .clickable { pending = RestorePending.FromArchive },
        )
    }

    when (pending) {
        RestorePending.None -> Unit
        RestorePending.FromFolder -> DestructiveConfirmDialog(
            title = stringResource(R.string.backuprestore_restore_dialog_title),
            body = stringResource(R.string.backuprestore_restore_folder_body),
            confirmLabel = stringResource(R.string.backuprestore_restore_confirm),
            cancelLabel = stringResource(R.string.backuprestore_restore_cancel),
            onConfirm = {
                pending = RestorePending.None
                onRestoreFromFolder()
            },
            onDismiss = { pending = RestorePending.None },
            testTagRoot = "BackupRestore-ConfirmFolder",
        )
        RestorePending.FromArchive -> DestructiveConfirmDialog(
            title = stringResource(R.string.backuprestore_restore_dialog_title),
            body = stringResource(R.string.backuprestore_restore_archive_body),
            confirmLabel = stringResource(R.string.backuprestore_restore_confirm),
            cancelLabel = stringResource(R.string.backuprestore_restore_cancel),
            onConfirm = {
                pending = RestorePending.None
                onPickRestoreArchive()
            },
            onDismiss = { pending = RestorePending.None },
            testTagRoot = "BackupRestore-ConfirmArchive",
        )
    }
}
