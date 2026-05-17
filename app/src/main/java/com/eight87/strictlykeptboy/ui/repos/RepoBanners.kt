package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagBackupReminderBanner = "ReposPane-BackupReminderBanner"
const val TestTagBackupReminderPick = "ReposPane-BackupReminderPick"
const val TestTagBackupReminderDismiss = "ReposPane-BackupReminderDismiss"

const val TestTagSafRevokedBanner = "ReposPane-SafRevokedBanner"
const val TestTagSafRevokedRepick = "ReposPane-SafRevokedRepick"

/**
 * Round 2.17 Phase E.7 — banner shown when SAF permission for the
 * external parent has been revoked from outside the app. Red surface
 * (error-container) per D-2.17.k. Tap fires the parent picker.
 */
@Composable
internal fun SafPermissionRevokedBanner(onPick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTagSafRevokedBanner)
            .clickable(onClick = onPick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.repos_lost_access_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(
                    onClick = onPick,
                    modifier = Modifier.testTag(TestTagSafRevokedRepick),
                ) {
                    Text(stringResource(R.string.repos_backup_reminder_pick))
                }
            }
        }
    }
}

/**
 * Round 2.7.D.2-UI — primary-container card with copy + "Pick now"
 * button + dismiss icon. Banner hides once the dismiss key lands in
 * `NotificationPrefs.dismissedReminders` (or the mirror flips to
 * External, clearing `skippedDuringWizard`).
 */
@Composable
internal fun BackupFolderReminderBanner(
    onPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTagBackupReminderBanner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.repos_backup_reminder_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.padding(top = 4.dp))
                TextButton(
                    onClick = onPick,
                    modifier = Modifier.testTag(TestTagBackupReminderPick),
                ) {
                    Text(stringResource(R.string.repos_backup_reminder_pick))
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTagBackupReminderDismiss),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.repos_backup_reminder_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
