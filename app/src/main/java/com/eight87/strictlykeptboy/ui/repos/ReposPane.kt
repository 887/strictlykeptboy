package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.git.auth.PatCredential
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import com.eight87.strictlykeptboy.store.IdentityTomlCodec
import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.ModeTomlCodec
import com.eight87.strictlykeptboy.store.RepoMode
import com.eight87.strictlykeptboy.store.RoutineCalendarConfig
import com.eight87.strictlykeptboy.store.SupersedenceConfig
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlWriter
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

const val TestTagReposPaneDetailEmpty = "ReposPane-DetailEmpty"

const val TestTagReposPane = "ReposPane"
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
    /**
     * Round 2.4 — global app settings cog. Lives in the Repos header row
     * since the user explicitly moved it there from the top-bar.
     */
    onOpenAppSettings: () -> Unit = {},
    /**
     * Round 2.3.A.3 — per-repo sync trigger surfaced inside each repo
     * card row. Null falls back to the in-pane default which dispatches
     * `SyncService.startSyncRepo(context, repoId)`.
     */
    onSyncRepo: ((String) -> Unit)? = null,
    /**
     * Round 2.7.D.2-UI — backup-folder reminder banner. When all three
     * are non-null, the banner is shown iff the mirror is unset AND the
     * user skipped during the wizard AND hasn't already dismissed.
     */
    repoStoragePrefs: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs? = null,
    notificationPrefs: com.eight87.strictlykeptboy.notif.NotificationPrefs? = null,
    onPickBackupFolder: (() -> Unit)? = null,
    /**
     * Round 2.15 — demo-mode toggle row at the top of the repo list.
     */
    demoModePrefs: com.eight87.strictlykeptboy.prefs.DemoModePrefs? = null,
    /**
     * Round 2.17 Phase E.1 — when `repoStoragePrefs` is configured, the
     * Add-Repo branch consults `ParentLocationGate.evaluate` and renders
     * an inline Storage step instead of the form until the gate flips
     * to Confirmed. Wired here so the host can pass `onPickInternal`
     * + `onPickExternal` callbacks without duplicating the picker
     * launcher.
     */
    onPickInternalStorage: () -> Unit = {},
    /**
     * Round 2.17 Phase E.7 — banner shown when the persisted SAF URI
     * permission has been revoked from outside the app (system
     * Settings → Apps → permissions). Tap fires the parent picker.
     * Default null → no banner check.
     */
    safPermissionRevoked: Boolean = false,
) {
    var mode by remember { mutableStateOf<Mode>(Mode.List) }
    val repos by state.repos.collectAsState()
    val activeRepoId by state.activeRepoId.collectAsState()
    var showShareSheetForRepo by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val widthClass = LocalWindowWidthSizeClass.current

    // Round 2.17 Phase E.1 — gate evaluation. Recomposed on prefs flip
    // (collectAsState pulls in `parent` so the gate re-derives).
    val parentState = repoStoragePrefs?.state?.collectAsState()?.value
    val storageGate: com.eight87.strictlykeptboy.prefs.ParentLocationGate.State? =
        repoStoragePrefs?.let {
            // Use `parentState` as a recomposition trigger (read but
            // not used directly — `evaluate` reads the same prefs).
            @Suppress("UNUSED_EXPRESSION") parentState
            com.eight87.strictlykeptboy.prefs.ParentLocationGate.evaluate(
                it, context.contentResolver,
            )
        }
    val onAddRepoPickExternal: () -> Unit = { onPickBackupFolder?.invoke() }

    // Round 2.3.A.3 — default per-repo sync dispatch: call the existing
    // SyncService entrypoint for the given repo. Callers can override via
    // [onSyncRepo] for tests / previews.
    val resolvedOnSyncRepo: (String) -> Unit = onSyncRepo
        ?: { repoId ->
            com.eight87.strictlykeptboy.sync.SyncService.startSyncRepo(context, repoId)
        }

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
                        state = state,
                        scope = scope,
                        onSelect = { repoId ->
                            state.setActive(repoId)
                            mode = Mode.Settings(repoId)
                        },
                        onAddRepo = { mode = Mode.Add },
                        onOpenSettings = { repoId -> mode = Mode.Settings(repoId) },
                        onOpenTogether = onOpenTogether,
                        onOpenWizard = onOpenWizard,
                        onOpenAppSettings = onOpenAppSettings,
                        onSyncRepo = resolvedOnSyncRepo,
                        repoStoragePrefs = repoStoragePrefs,
                        notificationPrefs = notificationPrefs,
                        onPickBackupFolder = onPickBackupFolder,
                        demoModePrefs = demoModePrefs,
                        safPermissionRevoked = safPermissionRevoked,
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
                        // Round 2.17.C.3 — tablet detail-pane Add-Repo lands
                        // under the configured parent (same as compact).
                        parentDir = repoStoragePrefs?.location?.workingDir(context.filesDir)
                            ?: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs.defaultInternalDir(context),
                        storageGate = storageGate,
                        onPickExternalStorage = onAddRepoPickExternal,
                        onPickInternalStorage = onPickInternalStorage,
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
                state = state,
                scope = scope,
                onSelect = { state.setActive(it) },
                onAddRepo = { mode = Mode.Add },
                onOpenSettings = { repoId -> mode = Mode.Settings(repoId) },
                onOpenTogether = onOpenTogether,
                onOpenWizard = onOpenWizard,
                onOpenAppSettings = onOpenAppSettings,
                onSyncRepo = resolvedOnSyncRepo,
                repoStoragePrefs = repoStoragePrefs,
                notificationPrefs = notificationPrefs,
                onPickBackupFolder = onPickBackupFolder,
                demoModePrefs = demoModePrefs,
                safPermissionRevoked = safPermissionRevoked,
            )
            Mode.Add -> AddRepoNavHost(
                onCancel = { mode = Mode.List },
                storageGate = storageGate,
                onPickExternalStorage = onAddRepoPickExternal,
                onPickInternalStorage = onPickInternalStorage,
                onFinish = { result ->
                    scope.launch {
                        // Round 2.17.C.3 — working-tree path now lives under
                        // the configured parent. Compact-pane branch.
                        val parentDir = repoStoragePrefs?.location?.workingDir(context.filesDir)
                            ?: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs.defaultInternalDir(context)
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
                    }
                },
            )
            is Mode.Settings -> {
                val repo = repos.firstOrNull { it.repoId == m.repoId }
                if (repo == null) {
                    mode = Mode.List
                } else {
                    RepoSettingsHost(
                        repo = repo,
                        state = state,
                        secretsStore = secretsStore,
                        scope = scope,
                        onBack = { mode = Mode.List },
                        onAddRemote = { mode = Mode.Add },
                        onOpenIdentities = { mode = Mode.Identities(repo.repoId) },
                        onShare = { showShareSheetForRepo = repo.repoId },
                        onRemoved = { mode = Mode.List },
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
            is Mode.StickerPacks -> {
                val repo = repos.firstOrNull { it.repoId == m.repoId }
                StickerPacksHost(repo = repo, onBack = { mode = Mode.Settings(m.repoId) }, fallback = { mode = Mode.List })
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

/**
 * Round 2.5.C — sticker pack host. Reads composition-locals provided by
 * MainActivity to instantiate [StickerPackSelectorScreen]. Falls back to
 * [fallback] when the active repo doesn't resolve or required deps are
 * not wired (e.g. preview / tests without AppGraph).
 */
@Composable
private fun StickerPacksHost(
    repo: RepoConfig?,
    onBack: () -> Unit,
    fallback: () -> Unit,
) {
    val packPrefs = LocalAvatarPackPrefs.current
    val packStore = LocalPackStore.current
    val assetLoader = LocalAssetPackLoader.current
    val userLoader = LocalUserPackLoader.current
    if (repo == null || packPrefs == null || packStore == null || assetLoader == null || userLoader == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { fallback() }
        return
    }
    // Species derived from repo's identity.toml in a future iteration;
    // for now use the wizard-scaffold default of "bat" until per-repo
    // species resolution lands (see plan 2.5.C deferral note).
    val species = "bat"
    StickerPackSelectorScreen(
        species = species,
        packStore = packStore,
        assetPackLoader = assetLoader,
        userPackLoader = userLoader,
        packPrefs = packPrefs,
        onBack = onBack,
    )
}

@Composable
private fun ReposList(
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
            androidx.compose.material3.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Repos-DemoToggle"),
                colors = androidx.compose.material3.CardDefaults.cardColors(
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
                    androidx.compose.material3.Switch(
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
            androidx.compose.material3.TextButton(
                onClick = onAddRepo,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagRepoSwitcherAdd),
            ) {
                androidx.compose.material3.Icon(
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
private fun SafPermissionRevokedBanner(onPick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTagSafRevokedBanner)
            .clickable(onClick = onPick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
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
                androidx.compose.material3.TextButton(
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
private fun BackupFolderReminderBanner(
    onPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTagBackupReminderBanner),
        colors = androidx.compose.material3.CardDefaults.cardColors(
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
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                androidx.compose.material3.TextButton(
                    onClick = onPick,
                    modifier = Modifier.testTag(TestTagBackupReminderPick),
                ) {
                    Text(stringResource(R.string.repos_backup_reminder_pick))
                }
            }
            androidx.compose.material3.IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTagBackupReminderDismiss),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                    contentDescription = stringResource(R.string.repos_backup_reminder_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private sealed interface Mode {
    object List : Mode
    object Add : Mode
    data class Settings(val repoId: String) : Mode
    data class Identities(val repoId: String) : Mode
    data class StickerPacks(val repoId: String) : Mode
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

/**
 * Round 2.5.B — single Composable wrapping `RepoSettingsScreen` with the
 * disk-backed wiring for the new Calendars / Identity / Mode sections.
 * Both the compact + two-pane branches above delegate to this to keep
 * the wiring DRY.
 *
 * Reads `<repoRoot>/calendars/<id>/calendar.toml` for the calendar list,
 * `<repoRoot>/identity.toml` for the per-repo identity snapshot, and
 * `<repoRoot>/mode.toml` for the mode. Writes round-trip through the
 * matching codecs + `GitRepoRegistry.get(repoId).commitAll(...)`. A
 * `refreshTick` state forces a re-read after each write so the UI
 * reflects the on-disk truth.
 */
@Composable
private fun RepoSettingsHost(
    repo: RepoConfig,
    state: ReposViewState,
    secretsStore: SecretsStore?,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit,
    onAddRemote: () -> Unit,
    onOpenIdentities: () -> Unit,
    onShare: () -> Unit,
    onRemoved: () -> Unit,
) {
    val repoRoot: Path = remember(repo.repoId, repo.rootDir) { Path.of(repo.rootDir) }
    var refreshTick by remember { mutableStateOf(0) }
    var calendars by remember(repo.repoId) { mutableStateOf<List<CalendarMeta>>(emptyList()) }
    var identity by remember(repo.repoId) {
        mutableStateOf(PerRepoIdentitySnapshot())
    }
    var modeSnap by remember(repo.repoId) { mutableStateOf(RepoMode.Free) }
    // Calendar-edit sheet host.
    var pendingEdit by remember { mutableStateOf<CalendarMeta?>(null) }

    LaunchedEffect(repo.repoId, refreshTick) {
        val (cals, id, md) = withContext(Dispatchers.IO) {
            Triple(
                scanCalendars(repoRoot, repo.repoId, repo.colorSeed),
                runCatching { IdentityTomlCodec.readOrDefault(repoRoot) }.getOrNull(),
                runCatching { ModeTomlCodec.readOrDefault(repoRoot) }.getOrNull(),
            )
        }
        calendars = cals
        if (id != null) {
            identity = PerRepoIdentitySnapshot(
                praise = id.praiseTerm,
                pronouns = "${id.pronouns.subject}/${id.pronouns.obj}",
                honorific = id.honorificForDom,
            )
        }
        if (md != null) modeSnap = md.mode
    }

    RepoSettingsScreen(
        repo = repo,
        onBack = onBack,
        onUpdate = { newCfg -> scope.launch { state.store.update(newCfg) } },
        onAddRemote = onAddRemote,
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
                    runCatching { java.io.File(repo.rootDir).deleteRecursively() }
                }
                onRemoved()
            }
        },
        onOpenIdentities = onOpenIdentities,
        onShareRepo = onShare,
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
        calendars = calendars,
        onToggleCalendarActive = { cal, active ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    setCalendarActive(repoRoot, cal.ref.id, active)
                    GitRepoRegistry.get(repo.repoId)
                        ?.commitAll("calendar: toggle ${cal.displayName} active=$active")
                }
                refreshTick++
            }
        },
        onEditCalendar = { cal -> pendingEdit = cal },
        onAddCalendar = { name, kind, emoji ->
            scope.launch(Dispatchers.IO) {
                runCatching { createCalendar(repoRoot, name, kind, emoji) }
                runCatching {
                    GitRepoRegistry.get(repo.repoId)?.commitAll("calendar: add $name")
                }
                refreshTick++
            }
        },
        identitySnapshot = identity,
        onIdentityEdit = { snap ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val existing = runCatching {
                        IdentityTomlCodec.readOrDefault(repoRoot)
                    }.getOrNull()
                        ?: com.eight87.strictlykeptboy.store.IdentityTomlData.LockedDefaults
                    val parsedPronouns = parsePronounsPair(snap.pronouns) ?: existing.pronouns
                    val merged = existing.copy(
                        praiseTerm = snap.praise.ifBlank { existing.praiseTerm },
                        pronouns = parsedPronouns,
                        honorificForDom = snap.honorific.ifBlank { existing.honorificForDom },
                    )
                    IdentityTomlCodec.write(repoRoot, merged)
                    GitRepoRegistry.get(repo.repoId)?.commitAll("identity: update")
                }
                refreshTick++
            }
        },
        modeSnapshot = modeSnap,
        onModeEdit = { newMode ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val existing = runCatching {
                        ModeTomlCodec.readOrDefault(repoRoot)
                    }.getOrNull()
                        ?: com.eight87.strictlykeptboy.store.ModeTomlData.Default
                    ModeTomlCodec.write(repoRoot, existing.copy(mode = newMode))
                    GitRepoRegistry.get(repo.repoId)?.commitAll("mode: ${newMode.wire}")
                }
                refreshTick++
            }
        },
    )

    // CalendarSettingsSheet overlay — reuses the existing sheet (2.1.B.4).
    pendingEdit?.let { meta ->
        com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsSheet(
            calendar = meta,
            onDismiss = { pendingEdit = null },
            onSave = { draft ->
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        writeCalendarSheetDraft(repoRoot, draft)
                        GitRepoRegistry.get(repo.repoId)
                            ?.commitAll("calendar settings: ${meta.displayName}")
                    }
                    pendingEdit = null
                    refreshTick++
                }
            },
        )
    }
}

// -----------------------------------------------------------------------
// Disk helpers — scan + write per-repo calendar.toml files.
// -----------------------------------------------------------------------

private fun scanCalendars(
    repoRoot: Path,
    repoId: String,
    repoColorFallback: Int?,
): List<CalendarMeta> {
    val dir = repoRoot.resolve("calendars")
    if (!Files.isDirectory(dir)) return emptyList()
    val out = mutableListOf<CalendarMeta>()
    Files.list(dir).use { listing ->
        listing.forEach { calDir ->
            if (!Files.isDirectory(calDir)) return@forEach
            val calendarId = calDir.fileName.toString()
            val tomlPath = calDir.resolve("calendar.toml")
            if (!Files.isRegularFile(tomlPath)) return@forEach
            runCatching {
                val text = String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8)
                val t = TomlReader.parse(text)
                val displayName = t.getString("name") ?: t.getString("display_name") ?: calendarId
                val priority = t.getInt("priority") ?: 500
                val routine = RoutineCalendarConfig.read(t)
                val supersedence = SupersedenceConfig.read(t, hostCalendarId = calendarId)
                val activity = CalendarActivityConfig.read(t)
                val kindStr = t.getString("kind")
                val kind = when (kindStr?.lowercase()) {
                    "timebox" -> CalendarKind.Timebox
                    else -> CalendarKind.Regular
                }
                out += CalendarMeta(
                    ref = CalendarRef(calendarId),
                    repo = RepoRef(repoId),
                    displayName = displayName,
                    priority = priority,
                    activeToggle = routine.activeToggle,
                    activeWindows = activity.activeWindows.map {
                        com.eight87.strictlykeptboy.resolver.DateRange(
                            start = it.from,
                            endInclusive = it.to,
                        )
                    },
                    activeHours = activity.activeHours.map {
                        com.eight87.strictlykeptboy.resolver.HourRange(
                            day = it.day,
                            from = it.from,
                            to = it.to,
                        )
                    },
                    kind = kind,
                    supersedes = supersedence.supersedes.map { CalendarRef(it) },
                    colorSeed = activity.colorSeed ?: repoColorFallback,
                )
            }
        }
    }
    return out.sortedBy { it.displayName.lowercase() }
}

private fun setCalendarActive(repoRoot: Path, calendarId: String, active: Boolean) {
    val tomlPath = repoRoot.resolve("calendars/$calendarId/calendar.toml")
    if (!Files.isRegularFile(tomlPath)) return
    val table = TomlReader.parse(
        String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8),
    )
    val routine = RoutineCalendarConfig.read(table).copy(activeToggle = active)
    routine.writeInto(table)
    Files.write(tomlPath, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))
}

private fun createCalendar(
    repoRoot: Path,
    name: String,
    kind: CalendarKind,
    emoji: String?,
) {
    val id = Uuid7.generate().toString()
    val dir = repoRoot.resolve("calendars/$id")
    Files.createDirectories(dir)
    val table = TomlTable().apply {
        putString("id", id)
        putString("name", name)
        putString("kind", if (kind == CalendarKind.Timebox) "timebox" else "regular")
        emoji?.takeIf { it.isNotBlank() }?.let { putString("emoji", it) }
        putInt("priority", 500)
    }
    RoutineCalendarConfig(routine = false, activeToggle = true).writeInto(table)
    val tomlPath = dir.resolve("calendar.toml")
    Files.write(tomlPath, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))
}

private fun writeCalendarSheetDraft(
    repoRoot: Path,
    draft: com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsDraft,
) {
    val meta = draft.calendar
    val tomlPath = repoRoot.resolve("calendars/${meta.ref.id}/calendar.toml")
    Files.createDirectories(tomlPath.parent)
    val table: TomlTable = if (Files.isRegularFile(tomlPath)) {
        TomlReader.parse(String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8))
    } else {
        TomlTable().apply {
            putString("id", meta.ref.id)
            putString("name", meta.displayName)
        }
    }
    val existingRoutine = RoutineCalendarConfig.read(table)
    existingRoutine.copy(activeToggle = draft.activeToggle).writeInto(table)
    table.putInt("priority", draft.priority)
    table.scalars.remove("supersedes")
    if (draft.supersedes.isNotEmpty()) {
        table.putStringArray("supersedes", draft.supersedes)
    }
    table.scalars.remove("color_seed")
    table.aotables.remove("active_windows")
    table.aotables.remove("active_hours")
    CalendarActivityConfig(
        colorSeed = meta.colorSeed,
        activeWindows = draft.activeWindows,
        activeHours = draft.activeHours,
    ).writeInto(table)
    val keepSup = SupersedenceConfig.read(table, hostCalendarId = meta.ref.id)
        .copy(supersedes = draft.supersedes)
    keepSup.writeInto(table)
    Files.write(tomlPath, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))
}

private fun parsePronounsPair(flat: String): IdentityPronouns? {
    val trimmed = flat.trim()
    if (trimmed.isBlank()) return null
    return when (trimmed.lowercase()) {
        "he/him", "he" -> IdentityPronouns.HeHim
        "she/her", "she" -> IdentityPronouns.SheHer
        "they/them", "they" -> IdentityPronouns.TheyThem
        else -> {
            val parts = trimmed.split('/').map { it.trim() }
            if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                IdentityPronouns(
                    subject = parts[0],
                    obj = parts[1],
                    possessive = parts.getOrNull(2) ?: (parts[1] + "s"),
                    reflexive = parts.getOrNull(3) ?: (parts[1] + "self"),
                )
            } else null
        }
    }
}

// Silence unused-import warnings on imports kept for visual clarity.
@Suppress("unused")
private fun MutableState<Int>.markUsed() = Unit
