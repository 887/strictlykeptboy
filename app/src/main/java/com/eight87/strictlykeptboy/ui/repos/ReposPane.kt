package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
) {
    var mode by remember { mutableStateOf<Mode>(Mode.List) }
    val repos by state.repos.collectAsState()
    val activeRepoId by state.activeRepoId.collectAsState()
    var showShareSheetForRepo by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val widthClass = LocalWindowWidthSizeClass.current

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
                state = state,
                scope = scope,
                onSelect = { state.setActive(it) },
                onAddRepo = { mode = Mode.Add },
                onOpenSettings = { repoId -> mode = Mode.Settings(repoId) },
                onOpenTogether = onOpenTogether,
                onOpenWizard = onOpenWizard,
                onOpenAppSettings = onOpenAppSettings,
                onSyncRepo = resolvedOnSyncRepo,
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
            // Round 2.4 — global app settings cog. Moved from the top-bar
            // into the Repos header per user direction. Per-repo settings
            // still open via row-tap → Mode.Settings(repoId) above.
            androidx.compose.material3.IconButton(
                onClick = onOpenAppSettings,
                modifier = Modifier.testTag("ReposAppSettings"),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "App settings",
                )
            }
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
            onSyncRepo = onSyncRepo,
            onToggleShowOnSchedule = { repoId, value ->
                scope.launch { state.store.setShowOnSchedule(repoId, value) }
            },
            onToggleDrawTasksFrom = { repoId, value ->
                scope.launch { state.store.setDrawTasksFrom(repoId, value) }
            },
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
