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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagBackupRestoreScreen = "BackupRestoreScreen"
const val TestTagBackupRestoreExportRow = "BackupRestore-ExportRow"

/**
 * Round 2.17 Phase F.3 — Backup/Restore screen. The export row is
 * wired in F; the restore rows land in Phase G (still rendered here as
 * placeholder text until then).
 *
 * The export row fires `onExportBackup`, which (via MainActivity's
 * F.4 wiring) opens a `CreateDocument("application/gzip")` SAF picker
 * suggesting `strictlykeptboy-backup-<yyyy-MM-dd>.tar.gz`. The actual
 * write happens on `Dispatchers.IO` once the user grants a URI.
 */
@Composable
fun BackupRestoreScreen(
    onExportBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
        // Phase G — restore actions land here.
        Text(
            stringResource(R.string.backuprestore_restore_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
