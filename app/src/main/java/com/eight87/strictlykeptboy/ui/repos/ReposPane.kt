package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
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
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import kotlinx.coroutines.launch

const val TestTagReposPaneDetailEmpty = "ReposPane-DetailEmpty"

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
    onOpenTogether: () -> Unit = {},
    onOpenWizard: () -> Unit = {},
) {
    var mode by remember { mutableStateOf<Mode>(Mode.List) }
    val repos by state.repos.collectAsState()
    val activeRepoId by state.activeRepoId.collectAsState()
    var showShareSheetForRepo by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val unified by state.unifiedView.collectAsState()
    val scope = rememberCoroutineScope()
    val widthClass = LocalWindowWidthSizeClass.current

    // Phase 2.1.H.3 — on Medium/Expanded, render List as the master pane
    // and whatever sub-mode is active as the detail pane. List stays
    // visible at all times so users can hop between repos without
    // popping back through navigation. Compact falls through to the
    // existing single-pane mode-switch (unchanged contract).
    if (widthClass.isTwoPane()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag(TestTagReposPane)
                .padding(12.dp),
        ) {
            MasterDetailLayout(
                widthClass = widthClass,
                master = {
                    ReposList(
                        repos = repos,
                        activeRepoId = activeRepoId,
                        unified = unified,
                        state = state,
                        onSetUnified = { state.setUnifiedView(it) },
                        onSelect = { repoId ->
                            state.setActive(repoId)
                            mode = Mode.Settings(repoId)
                        },
                        onAddRepo = { mode = Mode.Add },
                        onOpenSettings = { repoId -> mode = Mode.Settings(repoId) },
                        onOpenTogether = onOpenTogether,
                        onOpenWizard = onOpenWizard,
                    )
                },
                detail = {
                    ReposDetailPane(
                        mode = mode,
                        repos = repos,
                        state = state,
                        secretsStore = secretsStore,
                        scope = scope,
                        onModeChange = { mode = it },
                        onShowShare = { showShareSheetForRepo = it },
                    )
                },
            )
            // Share sheet host shared with compact branch below.
            showShareSheetForRepo?.let { rid ->
                val shareRepo = repos.firstOrNull { it.repoId == rid }
                if (shareRepo != null) {
                    com.eight87.strictlykeptboy.ui.share.ShareSheet(
                        repo = shareRepo,
                        onDismiss = { showShareSheetForRepo = null },
                        onCopy = { link ->
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("share link", link))
                        },
                        onSend = { link ->
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, link)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, null))
                        },
                    )
                } else {
                    showShareSheetForRepo = null
                }
            }
        }
        return
    }

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
                onOpenTogether = onOpenTogether,
                onOpenWizard = onOpenWizard,
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
                        onShareRepo = { showShareSheetForRepo = repo.repoId },
                        onToggleRemoteReadOnly = { name, value ->
                            scope.launch {
                                val current = state.store.get(repo.repoId) ?: return@launch
                                val updated = current.copy(
                                    remotes = current.remotes.map { rb ->
                                        if (rb.name == name) rb.copy(treatAsReadOnly = value) else rb
                                    },
                                )
                                state.store.update(updated)
                            }
                        },
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

        // Phase O.1 — share bottom sheet host.
        showShareSheetForRepo?.let { rid ->
            val shareRepo = repos.firstOrNull { it.repoId == rid }
            if (shareRepo != null) {
                com.eight87.strictlykeptboy.ui.share.ShareSheet(
                    repo = shareRepo,
                    onDismiss = { showShareSheetForRepo = null },
                    onCopy = { link ->
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("share link", link))
                    },
                    onSend = { link ->
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, null))
                    },
                )
            } else {
                showShareSheetForRepo = null
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
    onOpenTogether: () -> Unit,
    onOpenWizard: () -> Unit,
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
            androidx.compose.material3.IconButton(
                onClick = onOpenTogether,
                modifier = Modifier.testTag("ReposFindTogether"),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = "Find a time together",
                )
            }
            // "+ new account" → launches the lifestyle wizard to set up a new
            // account/repo. Moved here from the top-bar per user direction
            // 2026-05-13 (Wizard only really needed on first launch + when
            // configuring a new account).
            androidx.compose.material3.IconButton(
                onClick = onOpenWizard,
                modifier = Modifier.testTag("ReposNewAccount"),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Set up a new account",
                )
            }
        }

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
private fun ReposDetailPane(
    mode: Mode,
    repos: List<RepoConfig>,
    state: ReposViewState,
    secretsStore: SecretsStore?,
    scope: kotlinx.coroutines.CoroutineScope,
    onModeChange: (Mode) -> Unit,
    onShowShare: (String) -> Unit,
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
                    onModeChange(Mode.List)
                }
            },
        )
        is Mode.Settings -> {
            val repo = repos.firstOrNull { it.repoId == mode.repoId }
            if (repo == null) {
                onModeChange(Mode.List)
            } else {
                RepoSettingsScreen(
                    repo = repo,
                    onBack = { onModeChange(Mode.List) },
                    onUpdate = { newCfg ->
                        scope.launch { state.store.update(newCfg) }
                    },
                    onAddRemote = { onModeChange(Mode.Add) },
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
                            onModeChange(Mode.List)
                        }
                    },
                    onOpenIdentities = { onModeChange(Mode.Identities(repo.repoId)) },
                    onShareRepo = { onShowShare(repo.repoId) },
                    onToggleRemoteReadOnly = { name, value ->
                        scope.launch {
                            val current = state.store.get(repo.repoId) ?: return@launch
                            val updated = current.copy(
                                remotes = current.remotes.map { rb ->
                                    if (rb.name == name) rb.copy(treatAsReadOnly = value) else rb
                                },
                            )
                            state.store.update(updated)
                        }
                    },
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
    }
}
