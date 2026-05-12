package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.git.auth.PatCredential
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import kotlinx.coroutines.launch

const val TestTagReposPane = "ReposPane"
const val TestTagReposPaneUnifiedToggle = "ReposPane-UnifiedToggle"
const val TestTagReposPaneSwitcher = "ReposPane-Switcher"
const val TestTagReposPaneEmpty = "ReposPane-Empty"

/**
 * Phase I — Repos rail destination. Hosts:
 *   - the unified-view toggle (I.6)
 *   - the inline repo switcher dropdown (I.1)
 *   - the Add-Repo flow (I.2)
 *   - the Repo settings screen (I.3), reachable from each row
 *   - the Identities sub-screen (I.4) reachable from settings
 *   - the Remove-repo flow (I.5)
 *
 * Stateful host. Owns sub-screen navigation via a [Mode] enum to avoid a
 * Navigation library dependency for this single destination.
 */
@Composable
fun ReposPane(
    state: ReposViewState,
    secretsStore: SecretsStore? = null,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf<Mode>(Mode.List) }
    val repos by state.repos.collectAsState()
    val activeRepoId by state.activeRepoId.collectAsState()
    val unified by state.unifiedView.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagReposPane)
            .padding(12.dp),
    ) {
        when (val m = mode) {
            is Mode.List -> ReposList(
                repos = repos,
                activeRepoId = activeRepoId,
                unified = unified,
                state = state,
                onSetUnified = { state.setUnifiedView(it) },
                onSelect = { state.setActive(it) },
                onAddRepo = { mode = Mode.Add },
                onOpenSettings = { repoId -> mode = Mode.Settings(repoId) },
            )
            Mode.Add -> AddRepoNavHost(
                onCancel = { mode = Mode.List },
                onFinish = { result ->
                    scope.launch {
                        when (result) {
                            is AddRepoResult.LocalOnly -> {
                                val cfg = result.toRepoConfig(rootDir = "/tmp/${Uuid7.generate()}")
                                state.store.add(cfg)
                                state.setActive(cfg.repoId)
                            }
                            is AddRepoResult.Remote -> {
                                val cfg = result.toRepoConfig(rootDir = "/tmp/${Uuid7.generate()}")
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
                    }
                },
            )
            is Mode.Settings -> {
                val repo = repos.firstOrNull { it.repoId == m.repoId }
                if (repo == null) {
                    mode = Mode.List
                } else {
                    RepoSettingsScreen(
                        repo = repo,
                        onBack = { mode = Mode.List },
                        onUpdate = { newCfg ->
                            scope.launch { state.store.update(newCfg) }
                        },
                        onAddRemote = { mode = Mode.Add },
                        onRemoveRemote = { name ->
                            scope.launch { state.store.removeRemote(repo.repoId, name) }
                        },
                        onSetPrimaryRemote = { name ->
                            scope.launch { state.store.setPrimary(repo.repoId, name) }
                        },
                        onRemoveRepo = { deleteLocal ->
                            scope.launch {
                                state.store.remove(repo.repoId)
                                secretsStore?.clearForRepo(repo.repoId)
                                if (deleteLocal) {
                                    runCatching {
                                        java.io.File(repo.rootDir).deleteRecursively()
                                    }
                                }
                                mode = Mode.List
                            }
                        },
                        onOpenIdentities = { mode = Mode.Identities(repo.repoId) },
                    )
                }
            }
            is Mode.Identities -> {
                val repo = repos.firstOrNull { it.repoId == m.repoId }
                if (repo == null) {
                    mode = Mode.List
                } else {
                    IdentitiesScreen(
                        repo = repo,
                        onBack = { mode = Mode.Settings(repo.repoId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReposList(
    repos: List<RepoConfig>,
    activeRepoId: String?,
    unified: Boolean,
    state: ReposViewState,
    onSetUnified: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onAddRepo: () -> Unit,
    onOpenSettings: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.repos_title), style = MaterialTheme.typography.headlineSmall)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.repos_unified_toggle_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (unified) stringResource(R.string.repos_unified_on)
                    else stringResource(R.string.repos_unified_off),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = unified,
                onCheckedChange = onSetUnified,
                modifier = Modifier.testTag(TestTagReposPaneUnifiedToggle),
            )
        }
        HorizontalDivider()

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

        RepoSwitcherDropdown(
            repos = repos,
            activeRepoId = activeRepoId,
            statusFor = { state.statusFor(it) },
            onSelect = onSelect,
            onAddRepo = onAddRepo,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxWidth().testTag(TestTagReposPaneSwitcher),
        )
    }
    // imports kept used:
    @Suppress("UNUSED_EXPRESSION") AuthorIdentity("", "")
    @Suppress("UNUSED_EXPRESSION") PatCredential("", "")
    @Suppress("UNUSED_EXPRESSION") RepoStore::class
}

private sealed interface Mode {
    object List : Mode
    object Add : Mode
    data class Settings(val repoId: String) : Mode
    data class Identities(val repoId: String) : Mode
}
