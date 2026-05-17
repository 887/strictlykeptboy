package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import kotlinx.coroutines.launch

/**
 * Phase 2.1.H.3 — detail pane on tablet. Renders Settings / Identities /
 * Add depending on [mode]; falls back to an empty-state hint when the
 * user hasn't selected a repo yet.
 *
 * Hoisted out of [ReposPane] so the compact (single-pane) branch and the
 * tablet (master-detail) branch can both delegate to the same per-mode
 * navigation without duplicating the long `onUpdate` / `onRemoveRepo`
 * lambda set.
 */
@Composable
internal fun ReposDetailPane(
    mode: Mode,
    repos: List<RepoConfig>,
    state: ReposViewState,
    secretsStore: SecretsStore?,
    scope: kotlinx.coroutines.CoroutineScope,
    onModeChange: (Mode) -> Unit,
    onShowShare: (String) -> Unit,
    /**
     * Round 2.17.C.3 — strictlykeptboy parent directory for the Add-Repo
     * branch. The Add flow lands new working trees under this folder so
     * the on-disk layout matches the configured [ParentLocation].
     */
    parentDir: java.io.File,
    storageGate: com.eight87.strictlykeptboy.prefs.ParentLocationGate.State? = null,
    onPickExternalStorage: () -> Unit = {},
    onPickInternalStorage: () -> Unit = {},
) {
    when (mode) {
        Mode.List -> Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TestTagReposPaneDetailEmpty)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.repos_select_hint_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.repos_select_hint_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Mode.Add -> AddRepoNavHost(
            onCancel = { onModeChange(Mode.List) },
            storageGate = storageGate,
            onPickExternalStorage = onPickExternalStorage,
            onPickInternalStorage = onPickInternalStorage,
            onFinish = { result ->
                scope.launch {
                    when (result) {
                        is AddRepoResult.LocalOnly -> {
                            val repoId = Uuid7.generate().toString()
                            val cfg = result.toRepoConfig(
                                rootDir = java.io.File(parentDir, repoId).absolutePath,
                            )
                            com.eight87.strictlykeptboy.git.warnIfRepoOutsideParent(
                                cfg, parentDir.absolutePath,
                            )
                            state.store.add(cfg)
                            state.setActive(cfg.repoId)
                        }
                        is AddRepoResult.Remote -> {
                            val repoId = Uuid7.generate().toString()
                            val cfg = result.toRepoConfig(
                                rootDir = java.io.File(parentDir, repoId).absolutePath,
                            )
                            com.eight87.strictlykeptboy.git.warnIfRepoOutsideParent(
                                cfg, parentDir.absolutePath,
                            )
                            state.store.add(cfg)
                            state.setActive(cfg.repoId)
                            if (result.pat != null) {
                                secretsStore?.storePat(
                                    cfg.repoId,
                                    cfg.primaryRemote!!,
                                    result.pat,
                                )
                            }
                            if (result.oauthToken != null) {
                                secretsStore?.storeOAuthToken(
                                    cfg.repoId,
                                    cfg.primaryRemote!!,
                                    result.oauthToken,
                                )
                            }
                        }
                    }
                    onModeChange(Mode.List)
                }
            },
        )
        is Mode.Settings -> {
            val repo = repos.firstOrNull { it.repoId == mode.repoId }
            if (repo == null) {
                onModeChange(Mode.List)
            } else {
                RepoSettingsHost(
                    repo = repo,
                    state = state,
                    secretsStore = secretsStore,
                    scope = scope,
                    onBack = { onModeChange(Mode.List) },
                    onAddRemote = { onModeChange(Mode.Add) },
                    onOpenIdentities = { onModeChange(Mode.Identities(repo.repoId)) },
                    onShare = { onShowShare(repo.repoId) },
                    onRemoved = { onModeChange(Mode.List) },
                )
            }
        }
        is Mode.Identities -> {
            val repo = repos.firstOrNull { it.repoId == mode.repoId }
            if (repo == null) {
                onModeChange(Mode.List)
            } else {
                IdentitiesScreen(
                    repo = repo,
                    onBack = { onModeChange(Mode.Settings(repo.repoId)) },
                )
            }
        }
        is Mode.StickerPacks -> {
            val repo = repos.firstOrNull { it.repoId == mode.repoId }
            StickerPacksHost(
                repo = repo,
                onBack = { onModeChange(Mode.Settings(mode.repoId)) },
                fallback = { onModeChange(Mode.List) },
            )
        }
    }
}
