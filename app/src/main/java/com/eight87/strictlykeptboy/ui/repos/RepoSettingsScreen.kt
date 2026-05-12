package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.PushPolicy
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Transport

const val TestTagRepoSettings = "RepoSettings"
const val TestTagRepoSettingsRemotes = "RepoSettings-Remotes"
const val TestTagRepoSettingsNoRemotesCta = "RepoSettings-NoRemotesCta"
const val TestTagRepoSettingsAddRemote = "RepoSettings-AddRemote"
const val TestTagRepoSettingsRemoteRow = "RepoSettings-RemoteRow"
const val TestTagRepoSettingsRemoveRepo = "RepoSettings-RemoveRepo"
const val TestTagRepoSettingsConfirmRemove = "RepoSettings-ConfirmRemove"
const val TestTagRepoSettingsConfirmDeleteLocal = "RepoSettings-ConfirmDeleteLocal"
const val TestTagRepoSettingsRemoveRemote = "RepoSettings-RemoveRemote"
const val TestTagRepoSettingsIdentities = "RepoSettings-Identities"
const val TestTagRepoSettingsAutoSync = "RepoSettings-AutoSync"
const val TestTagRepoSettingsWifiOnly = "RepoSettings-WifiOnly"

/**
 * Phase I.3 — Repo settings screen. Renders all sections per the brief:
 *   Display / Sync / Identity / Defaults / Remotes / Identity preferences.
 * Stateless wrt persistence; caller wires update callbacks.
 */
@Composable
fun RepoSettingsScreen(
    repo: RepoConfig,
    onBack: () -> Unit,
    onUpdate: (RepoConfig) -> Unit,
    onAddRemote: () -> Unit,
    onRemoveRemote: (RemoteName) -> Unit,
    onSetPrimaryRemote: (RemoteName) -> Unit,
    onRemoveRepo: (deleteLocalClone: Boolean) -> Unit,
    onOpenIdentities: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(repo) { mutableStateOf(repo) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var deleteLocalClone by remember { mutableStateOf(false) }

    // Propagate live edits up.
    LaunchedEffect(draft) {
        if (draft != repo) onUpdate(draft)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagRepoSettings)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(repo.displayName, style = MaterialTheme.typography.headlineSmall)
        }

        // Display
        SettingsSection("Display") {
            OutlinedTextField(
                value = draft.displayName,
                onValueChange = { draft = draft.copy(displayName = it) },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.iconEmoji.orEmpty(),
                onValueChange = { draft = draft.copy(iconEmoji = it.ifBlank { null }) },
                label = { Text("Icon emoji") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Sync
        SettingsSection("Sync") {
            ToggleRow(
                label = "Auto-sync",
                checked = draft.autoSyncEnabled,
                onChange = { draft = draft.copy(autoSyncEnabled = it) },
                tag = TestTagRepoSettingsAutoSync,
            )
            Text(
                "Sync interval: ${draft.syncIntervalMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 30, 60, 240, -1).forEach { mins ->
                    AssistChip(
                        onClick = { draft = draft.copy(syncIntervalMinutes = mins) },
                        label = { Text(if (mins == -1) "Manual" else "${mins}m") },
                        colors = if (draft.syncIntervalMinutes == mins)
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ) else AssistChipDefaults.assistChipColors(),
                    )
                }
            }
            ToggleRow(
                label = "Wi-Fi only",
                checked = draft.wifiOnly,
                onChange = { draft = draft.copy(wifiOnly = it) },
                tag = TestTagRepoSettingsWifiOnly,
            )
        }

        // Identity
        SettingsSection("Identity") {
            OutlinedTextField(
                value = draft.authorIdentity.name,
                onValueChange = {
                    draft = draft.copy(authorIdentity = draft.authorIdentity.copy(name = it))
                },
                label = { Text("Author name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.authorIdentity.email,
                onValueChange = {
                    draft = draft.copy(authorIdentity = draft.authorIdentity.copy(email = it))
                },
                label = { Text("Author email") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onOpenIdentities,
                modifier = Modifier.testTag(TestTagRepoSettingsIdentities),
            ) { Text("Manage identities…") }
        }

        // Defaults
        SettingsSection("Defaults") {
            OutlinedTextField(
                value = draft.defaultCalendarId.orEmpty(),
                onValueChange = { draft = draft.copy(defaultCalendarId = it.ifBlank { null }) },
                label = { Text("Default calendar id") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.defaultTodolistId.orEmpty(),
                onValueChange = { draft = draft.copy(defaultTodolistId = it.ifBlank { null }) },
                label = { Text("Default todolist id") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Remotes (per ZZ.G)
        SettingsSection("Remotes", modifier = Modifier.testTag(TestTagRepoSettingsRemotes)) {
            if (repo.remotes.isEmpty()) {
                Text(
                    "No remotes — this repo lives only on this device",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onAddRemote,
                    modifier = Modifier.testTag(TestTagRepoSettingsNoRemotesCta),
                ) { Text("Add a remote") }
            } else {
                repo.remotes.forEach { binding ->
                    RemoteRow(
                        binding = binding,
                        isPrimary = binding.name == repo.primaryRemote,
                        lastSyncedAt = repo.lastSyncedAt,
                        onRemove = { onRemoveRemote(binding.name) },
                        onSetPrimary = { onSetPrimaryRemote(binding.name) },
                    )
                }
                TextButton(
                    onClick = onAddRemote,
                    modifier = Modifier.testTag(TestTagRepoSettingsAddRemote),
                ) { Text("+ Add another remote") }
            }
        }

        // Identity preferences (HV-R) — surface-only, simple preview.
        SettingsSection("Identity preferences") {
            Text(
                "Praise term, pronouns, tone register live in identity.toml — " +
                    "use the wizard to revisit.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card {
                Text(
                    text = "Hi ${draft.authorIdentity.name.ifBlank { "friend" }} — " +
                        "ready to keep your day kept?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        HorizontalDivider()
        Button(
            onClick = { showRemoveDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.testTag(TestTagRepoSettingsRemoveRepo),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text("Remove repo", modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove this repo from the app?") },
            text = {
                Column {
                    Text("The repo will be removed from your repo list.")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Checkbox(
                            checked = deleteLocalClone,
                            onCheckedChange = { deleteLocalClone = it },
                            modifier = Modifier.testTag(TestTagRepoSettingsConfirmDeleteLocal),
                        )
                        Text("Also delete the local clone")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveDialog = false
                        onRemoveRepo(deleteLocalClone)
                    },
                    modifier = Modifier.testTag(TestTagRepoSettingsConfirmRemove),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun RemoteRow(
    binding: RemoteBinding,
    isPrimary: Boolean,
    lastSyncedAt: Long?,
    onRemove: () -> Unit,
    onSetPrimary: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$TestTagRepoSettingsRemoteRow-${binding.name.value}"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(binding.name.value, style = MaterialTheme.typography.titleSmall)
                Box(modifier = Modifier.weight(1f))
                if (isPrimary) {
                    Text(
                        "primary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(binding.url, style = MaterialTheme.typography.bodySmall)
            Text(
                "${binding.transport} · ${binding.authMethod} · ${binding.pushPolicy}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Last synced: ${lastSyncedAt?.let { "epoch=$it" } ?: "never"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isPrimary) {
                    TextButton(onClick = onSetPrimary) { Text("Set primary") }
                }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(
                        "$TestTagRepoSettingsRemoveRemote-${binding.name.value}",
                    ),
                ) { Text("Remove") }
            }
        }
    }
    // silence unused-import warnings for types used implicitly via RemoteBinding
    @Suppress("UNUSED_EXPRESSION") AuthMethod.None
    @Suppress("UNUSED_EXPRESSION") Transport.File
    @Suppress("UNUSED_EXPRESSION") PushPolicy.Never
}
