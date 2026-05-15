package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagBackupRestoreScreen = "BackupRestoreScreen"

/**
 * Round 2.17 Phase E.2 — STUB for the Backup/Restore screen. Phase F
 * fills in export, Phase G fills in restore. Renders placeholder text
 * only — no actions wired.
 */
@Composable
fun BackupRestoreScreen(
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
        Text(
            stringResource(R.string.backuprestore_export_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.backuprestore_restore_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
