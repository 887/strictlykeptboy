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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.PushPolicy
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.ui.theming.CalendarColorPicker
import com.eight87.strictlykeptboy.ui.theming.RepoIconKind
import com.eight87.strictlykeptboy.ui.theming.RepoIconPicker
import com.eight87.strictlykeptboy.ui.theming.initialsFromName
import com.eight87.strictlykeptboy.ui.theming.seedColorFromName

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
const val TestTagRepoSettingsShare = "RepoSettings-Share"
const val TestTagRepoSettingsRemoteReadOnlyToggle = "RepoSettings-RemoteReadOnly"

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
    onShareRepo: () -> Unit = {},
    onToggleRemoteReadOnly: (RemoteName, Boolean) -> Unit = { _, _ -> },
    /** Phase ZZ.D — names of mirror remotes currently showing divergence from primary. */
    divergedMirrors: Set<RemoteName> = emptySet(),
    /** Phase ZZ.E — names of mirror remotes whose last push lagged the primary. */
    partialPushDegradedMirrors: Set<RemoteName> = emptySet(),
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
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(repo.displayName, style = MaterialTheme.typography.headlineSmall)
        }

        // Display
        SettingsSection(stringResource(R.string.repo_settings_section_display)) {
            OutlinedTextField(
                value = draft.displayName,
                onValueChange = { draft = draft.copy(displayName = it) },
                label = { Text(stringResource(R.string.repo_settings_display_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            // Phase T.2 — repo icon picker. The legacy `iconEmoji` field
            // remains the persisted source for emoji-kind icons; photo +
            // auto-initials map onto the same field for now (photo: store
            // the URI string, prefix-tagged; initials: empty → renderer
            // re-derives from displayName + seedColor).
            val currentKind: RepoIconKind = run {
                val raw = draft.iconEmoji
                when {
                    raw == null -> RepoIconKind.AutoInitials(
                        initials = initialsFromName(draft.displayName),
                        seedColor = seedColorFromName(draft.displayName),
                    )
                    raw.startsWith("photo:") -> RepoIconKind.Photo(raw.removePrefix("photo:"))
                    else -> RepoIconKind.Emoji(raw)
                }
            }
            RepoIconPicker(
                displayName = draft.displayName,
                current = currentKind,
                onKindChange = { kind ->
                    draft = draft.copy(
                        iconEmoji = when (kind) {
                            is RepoIconKind.Emoji -> kind.glyph
                            is RepoIconKind.Photo -> "photo:${kind.uri}"
                            is RepoIconKind.AutoInitials -> null
                            // Sticker variants are sourced from the wizard's
                            // species choice (D.88 / F48) — picking a sticker
                            // in this screen clears the legacy `iconEmoji` so
                            // the toIconKind() resolver picks `iconSpecies`
                            // first. iconSpecies update lives at the repo-
                            // creation site (wizard) for now.
                            is RepoIconKind.Sticker -> null
                        },
                    )
                },
            )
            // Phase T.5 — per-repo color seed override.
            Text(
                stringResource(R.string.repo_settings_color_seed_header),
                style = MaterialTheme.typography.titleSmall,
            )
            CalendarColorPicker(
                currentArgb = draft.colorSeed,
                onSeedChange = { argb -> draft = draft.copy(colorSeed = argb) },
            )
        }

        // Sync
        SettingsSection(stringResource(R.string.repo_settings_section_sync)) {
            ToggleRow(
                label = stringResource(R.string.repo_settings_auto_sync),
                checked = draft.autoSyncEnabled,
                onChange = { draft = draft.copy(autoSyncEnabled = it) },
                tag = TestTagRepoSettingsAutoSync,
            )
            Text(
                stringResource(R.string.repo_settings_sync_interval, draft.syncIntervalMinutes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 30, 60, 240, -1).forEach { mins ->
                    AssistChip(
                        onClick = { draft = draft.copy(syncIntervalMinutes = mins) },
                        label = {
                            Text(
                                if (mins == -1) stringResource(R.string.repo_settings_sync_manual)
                                else stringResource(R.string.repo_settings_sync_minutes, mins),
                            )
                        },
                        colors = if (draft.syncIntervalMinutes == mins)
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ) else AssistChipDefaults.assistChipColors(),
                    )
                }
            }
            ToggleRow(
                label = stringResource(R.string.repo_settings_wifi_only),
                checked = draft.wifiOnly,
                onChange = { draft = draft.copy(wifiOnly = it) },
                tag = TestTagRepoSettingsWifiOnly,
            )
        }

        // Identity
        SettingsSection(stringResource(R.string.repo_settings_section_identity)) {
            OutlinedTextField(
                value = draft.authorIdentity.name,
                onValueChange = {
                    draft = draft.copy(authorIdentity = draft.authorIdentity.copy(name = it))
                },
                label = { Text(stringResource(R.string.repo_settings_author_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.authorIdentity.email,
                onValueChange = {
                    draft = draft.copy(authorIdentity = draft.authorIdentity.copy(email = it))
                },
                label = { Text(stringResource(R.string.repo_settings_author_email)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onOpenIdentities,
                modifier = Modifier.testTag(TestTagRepoSettingsIdentities),
            ) { Text(stringResource(R.string.repo_settings_manage_identities)) }
        }

        // Defaults
        SettingsSection(stringResource(R.string.repo_settings_section_defaults)) {
            OutlinedTextField(
                value = draft.defaultCalendarId.orEmpty(),
                onValueChange = { draft = draft.copy(defaultCalendarId = it.ifBlank { null }) },
                label = { Text(stringResource(R.string.repo_settings_default_calendar_id)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.defaultTodolistId.orEmpty(),
                onValueChange = { draft = draft.copy(defaultTodolistId = it.ifBlank { null }) },
                label = { Text(stringResource(R.string.repo_settings_default_todolist_id)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Remotes (per ZZ.G)
        SettingsSection(stringResource(R.string.repo_settings_section_remotes), modifier = Modifier.testTag(TestTagRepoSettingsRemotes)) {
            // Phase O.3 — banner shown when any remote is treated read-only
            // (auto-detected or user-toggled).
            if (repo.remotes.any { it.effectiveReadOnly }) {
                com.eight87.strictlykeptboy.ui.share.ReadOnlyBanner()
            }
            // Phase ZZ.D — per-mirror yellow divergence banner.
            divergedMirrors.forEach { mirror ->
                val label = repo.remotes.firstOrNull { it.name == mirror }?.displayName
                    ?: mirror.value
                com.eight87.strictlykeptboy.ui.share.MirrorDivergenceBanner(mirrorLabel = label)
            }
            if (repo.remotes.isEmpty()) {
                Text(
                    stringResource(R.string.repo_settings_no_remotes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onAddRemote,
                    modifier = Modifier.testTag(TestTagRepoSettingsNoRemotesCta),
                ) { Text(stringResource(R.string.repo_settings_add_remote)) }
            } else {
                repo.remotes.forEach { binding ->
                    RemoteRow(
                        binding = binding,
                        isPrimary = binding.name == repo.primaryRemote,
                        lastSyncedAt = repo.lastSyncedAt,
                        partialPushDegraded = binding.name in partialPushDegradedMirrors,
                        onRemove = { onRemoveRemote(binding.name) },
                        onSetPrimary = { onSetPrimaryRemote(binding.name) },
                        onToggleReadOnly = { v -> onToggleRemoteReadOnly(binding.name, v) },
                    )
                }
                TextButton(
                    onClick = onAddRemote,
                    modifier = Modifier.testTag(TestTagRepoSettingsAddRemote),
                ) { Text(stringResource(R.string.repo_settings_add_another_remote)) }
                // Phase O.1 — share entry point.
                Button(
                    onClick = onShareRepo,
                    modifier = Modifier.testTag(TestTagRepoSettingsShare),
                ) { Text(stringResource(R.string.share_this_repo)) }
            }
        }

        // Identity preferences (HV-R) — surface-only, simple preview.
        SettingsSection(stringResource(R.string.repo_settings_section_identity_prefs)) {
            Text(
                stringResource(R.string.repo_settings_identity_prefs_blurb),
                style = MaterialTheme.typography.bodyMedium,
            )
            Card {
                val friend = stringResource(R.string.repo_settings_identity_prefs_hi_friend_default)
                Text(
                    text = stringResource(
                        R.string.repo_settings_identity_prefs_hi,
                        draft.authorIdentity.name.ifBlank { friend },
                    ),
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
            Text(stringResource(R.string.repo_settings_remove_repo), modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.repo_settings_remove_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.repo_settings_remove_dialog_body))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Checkbox(
                            checked = deleteLocalClone,
                            onCheckedChange = { deleteLocalClone = it },
                            modifier = Modifier.testTag(TestTagRepoSettingsConfirmDeleteLocal),
                        )
                        Text(stringResource(R.string.repo_settings_remove_dialog_delete_local))
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
                ) { Text(stringResource(R.string.repo_settings_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
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
    onToggleReadOnly: (Boolean) -> Unit = {},
    partialPushDegraded: Boolean = false,
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
                        stringResource(R.string.repo_settings_remote_primary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(binding.url, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(
                    R.string.repo_settings_remote_meta,
                    binding.transport.toString(),
                    binding.authMethod.toString(),
                    binding.pushPolicy.toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            val syncedSummary = lastSyncedAt?.let {
                stringResource(R.string.repo_settings_last_synced_epoch, it)
            } ?: stringResource(R.string.repo_settings_last_synced_never)
            Text(
                stringResource(R.string.repo_settings_last_synced, syncedSummary),
                style = MaterialTheme.typography.bodySmall,
            )
            if (partialPushDegraded) {
                com.eight87.strictlykeptboy.ui.share.PartialPushDegradedDot(
                    mirrorLabel = binding.displayName ?: binding.name.value,
                )
            }
            // Phase O.3 — per-remote "Treat as read-only" toggle.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.read_only_treat_as_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = binding.treatAsReadOnly,
                    onCheckedChange = onToggleReadOnly,
                    modifier = Modifier.testTag(
                        "$TestTagRepoSettingsRemoteReadOnlyToggle-${binding.name.value}",
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isPrimary) {
                    TextButton(onClick = onSetPrimary) { Text(stringResource(R.string.repo_settings_set_primary)) }
                }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(
                        "$TestTagRepoSettingsRemoveRemote-${binding.name.value}",
                    ),
                ) { Text(stringResource(R.string.repo_settings_remove)) }
            }
        }
    }
    // silence unused-import warnings for types used implicitly via RemoteBinding
    @Suppress("UNUSED_EXPRESSION") AuthMethod.None
    @Suppress("UNUSED_EXPRESSION") Transport.File
    @Suppress("UNUSED_EXPRESSION") PushPolicy.Never
}
