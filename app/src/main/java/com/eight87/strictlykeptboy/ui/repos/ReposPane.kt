package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import kotlinx.coroutines.launch

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
internal fun StickerPacksHost(
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
