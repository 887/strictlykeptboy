package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.auth.PatCredential
import kotlinx.coroutines.launch

@Composable
internal fun ReposList(
    repos: List<RepoConfig>,
    activeRepoId: String?,
    state: ReposViewState,
    scope: kotlinx.coroutines.CoroutineScope,
    onSelect: (String) -> Unit,
    onAddRepo: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenTogether: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onSyncRepo: (String) -> Unit = {},
    repoStoragePrefs: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs? = null,
    notificationPrefs: com.eight87.strictlykeptboy.notif.NotificationPrefs? = null,
    onPickBackupFolder: (() -> Unit)? = null,
    demoModePrefs: com.eight87.strictlykeptboy.prefs.DemoModePrefs? = null,
    safPermissionRevoked: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.repos_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            // "Find a time together" → opens the Together pane (common-time
            // finder across one or more repos). Moved here from the top-bar
            // per user direction 2026-05-13.
            IconButton(
                onClick = onOpenTogether,
                modifier = Modifier.testTag("ReposFindTogether"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = "Find a time together",
                )
            }
            // "+ new account" → launches the lifestyle wizard to set up a new
            // account/repo. Moved here from the top-bar per user direction
            // 2026-05-13 (Wizard only really needed on first launch + when
            // configuring a new account).
            IconButton(
                onClick = onOpenWizard,
                modifier = Modifier.testTag("ReposNewAccount"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Set up a new account",
                )
            }
            // Round 2.16.F — global app settings cog removed from the
            // Repos pane top-bar; it moved back into the shell top-bar
            // action row (immediately before the avatar) — its
            // pre-Round-2.1 location. Per-repo settings still open via
            // row-tap → Mode.Settings(repoId) above.
            // The `onOpenAppSettings` parameter is retained on the
            // composable signature so existing call-sites remain stable;
            // it's no longer wired to a Repos-pane affordance.
            @Suppress("UNUSED_EXPRESSION") onOpenAppSettings
        }

        HorizontalDivider()

        // Round 2.15 — demo-mode toggle row. Surfaces the current state +
        // perspective when demo data is loaded; flips off via switch.
        if (demoModePrefs != null) {
            val demoState by demoModePrefs.state.collectAsState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Repos-DemoToggle"),
                colors = CardDefaults.cardColors(
                    containerColor = if (demoState.isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Demo mode" +
                                (if (demoState.isActive && demoState.perspective != null) {
                                    " · ${demoState.perspective!!.name}"
                                } else ""),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (demoState.isActive) {
                                "Read-only demo data is loaded. Toggle off + tap + to make your own calendar."
                            } else {
                                "Toggle on to explore with seeded demo data."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = demoState.isActive,
                        onCheckedChange = { demoModePrefs.setActive(it) },
                        modifier = Modifier.testTag("Repos-DemoToggle-Switch"),
                    )
                }
            }
        }

        // Round 2.17 Phase E.7 — SAF permission-revoked banner. Shown
        // when the host detected that the persisted URI grant is gone
        // (boot-time check in `AppGraph.init` flips `safPermissionRevoked`).
        if (safPermissionRevoked && onPickBackupFolder != null) {
            SafPermissionRevokedBanner(onPick = { onPickBackupFolder() })
        }

        // Round 2.7.D.2-UI — dismissable backup-folder reminder banner.
        // Conditions: mirror still None AND user skipped during wizard
        // AND not already dismissed. Recomposed when prefs flip.
        if (repoStoragePrefs != null && notificationPrefs != null && onPickBackupFolder != null) {
            val parent by repoStoragePrefs.state.collectAsState()
            val dismissed = notificationPrefs.dismissedReminders
            val skippedDuringWizard = repoStoragePrefs.skippedDuringWizard
            // Round 2.17.A — "no parent confirmed yet" replaces the
            // 2.7 `MirrorLocation.None` check. The wizard-skip reminder
            // still drives the banner; Phase D/E replace it with the
            // proper Storage step gate.
            val show = parent == null &&
                skippedDuringWizard &&
                com.eight87.strictlykeptboy.notif.NotificationPrefs.REMINDER_BACKUP_FOLDER !in dismissed
            if (show) {
                BackupFolderReminderBanner(
                    onPick = { onPickBackupFolder() },
                    onDismiss = {
                        notificationPrefs.dismissReminder(
                            com.eight87.strictlykeptboy.notif.NotificationPrefs.REMINDER_BACKUP_FOLDER,
                        )
                    },
                )
            }
        }

        if (repos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagReposPaneEmpty)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.repos_empty_title))
                Text(
                    stringResource(R.string.repos_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Round 2.19 — per-repo expandable Material3 cards. Each repo is
        // its own card with all its binary toggles "hanging from" the
        // header like indented Python config; the long-tail edits live
        // behind the per-card "More settings…" footer that opens
        // `RepoSettingsScreen`. Replaces the dense `RepoSwitcherDropdown`
        // checkbox row layout the user called out as too tiny to use.
        Column(
            modifier = Modifier.fillMaxWidth().testTag(TestTagReposPaneSwitcher),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repos.forEach { repo ->
                RepoCard(
                    repo = repo,
                    isWriteTarget = repo.repoId == activeRepoId,
                    onSelectWriteTarget = { onSelect(repo.repoId) },
                    onToggleShowOnSchedule = { v ->
                        scope.launch { state.store.setShowOnSchedule(repo.repoId, v) }
                    },
                    onToggleDrawTasksFrom = { v ->
                        scope.launch { state.store.setDrawTasksFrom(repo.repoId, v) }
                    },
                    onToggleAutoSync = { v ->
                        scope.launch {
                            val cur = state.store.get(repo.repoId) ?: return@launch
                            state.store.update(cur.copy(autoSyncEnabled = v))
                        }
                    },
                    onToggleWifiOnly = { v ->
                        scope.launch {
                            val cur = state.store.get(repo.repoId) ?: return@launch
                            state.store.update(cur.copy(wifiOnly = v))
                        }
                    },
                    onToggleImportStickers = { v ->
                        scope.launch {
                            val cur = state.store.get(repo.repoId) ?: return@launch
                            state.store.update(cur.copy(importStickersToRepo = v))
                        }
                    },
                    onOpenMoreSettings = { onOpenSettings(repo.repoId) },
                )
            }
            // Footer "Add repo" affordance — keeps the existing test tag
            // so AddRepo flow tests continue to drive entry from here.
            TextButton(
                onClick = onAddRepo,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagRepoSwitcherAdd),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                )
                Text(
                    text = "  Add repo",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        // Silence the sync handler — wired but not surfaced on the new
        // card; per-repo sync now lives behind "More settings…".
        @Suppress("UNUSED_EXPRESSION") onSyncRepo
    }
    // imports kept used:
    @Suppress("UNUSED_EXPRESSION") AuthorIdentity("", "")
    @Suppress("UNUSED_EXPRESSION") PatCredential("", "")
    @Suppress("UNUSED_EXPRESSION") RepoStore::class
}
