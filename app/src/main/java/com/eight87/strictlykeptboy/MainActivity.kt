package com.eight87.strictlykeptboy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.composition.AppGraph
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.sync.SyncService
import java.io.File
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.scaffold.SkbAppShell
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.tasks.toTaskItem
import com.eight87.strictlykeptboy.ui.together.TogetherViewModel
import com.eight87.strictlykeptboy.ui.wizard.AgeGateScreen
import com.eight87.strictlykeptboy.ui.wizard.WizardScaffolder
import kotlinx.coroutines.launch

/**
 * Composition root (R.X.3). Per F22 the concrete-class instantiation
 * was extracted into [AppGraph] in Phase Q. This file now does three
 * things only:
 *   1. Construct + park the [AppGraph].
 *   2. Wire up the SAF launchers (they need `ActivityResultLauncher`,
 *      which is `ComponentActivity`-scoped).
 *   3. Set Compose content + pass the narrow surfaces down.
 *
 * Anything that adds more than a handful of LOC here should land in
 * `AppGraph` instead.
 */
class MainActivity : ComponentActivity() {

    private var deepLinkHandler: ((Intent) -> Unit)? = null
    /**
     * Round 2.18.E.6 — set from `setContent` once `scheduleState`
     * exists. Receives every cold-start + `onNewIntent` payload after
     * the share-link router gets first chance.
     */
    private var calendarIntentHandler: ((Intent) -> Unit)? = null
    private var pendingImportRepo: RepoConfig? = null
    private var pendingExportRepo: RepoConfig? = null
    private var pendingExportContent: String? = null
    private var onParsed: ((com.eight87.strictlykeptboy.port.ics.IcsParseReport) -> Unit)? = null

    /** Round 2.17 Phase E.4 — adopt-sheet request, observed from Compose. */
    val adoptSheetRequest:
        kotlinx.coroutines.flow.MutableStateFlow<AdoptSheetState?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    /** Round 2.17 Phase E.5 — pending move-job, observed from Compose. */
    val moveJobRequest:
        kotlinx.coroutines.flow.MutableStateFlow<MoveJobRequest?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    /**
     * Round 2.17 Phase E.4 — parameters for the adopt confirmation sheet.
     */
    data class AdoptSheetState(
        val treeUri: String,
        val label: String,
        val parentDir: java.io.File,
        val hasMarker: Boolean,
    )

    /**
     * Round 2.17 Phase E.5 — RepoMover invocation parameters.
     */
    data class MoveJobRequest(
        val oldParent: java.io.File,
        val newParent: java.io.File,
        val oldLocation: com.eight87.strictlykeptboy.prefs.ParentLocation,
        val newLocation: com.eight87.strictlykeptboy.prefs.ParentLocation,
    )

    private val openIcsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val repo = pendingImportRepo ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: return@runCatching
                val calendarId = repo.defaultCalendarId
                    ?: com.eight87.strictlykeptboy.git.Uuid7.generate().toString()
                val report = com.eight87.strictlykeptboy.port.ics.IcsParser.parse(
                    text = text,
                    calendarId = calendarId,
                    author = repo.authorIdentity.email,
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onParsed?.invoke(report)
                }
            }
        }
    }

    /**
     * Round 2.17.B.1–B.4 — SAF tree picker for the strictlykeptboy parent
     * folder. Replaces the Round 2.7 "backup mirror" launcher: there is no
     * mirror any more, the parent IS the working tree.
     *
     * Registered eagerly because `registerForActivityResult` must be called
     * before `onCreate`'s STARTED state. On grant:
     *  1. Resolve the SAF tree URI to a real `/storage/emulated/0/…` path
     *     via [com.eight87.strictlykeptboy.prefs.SafTreeUriResolver.resolveRealPath].
     *     SD cards / cloud providers return null — Toast + bail, no prefs
     *     written (B.1).
     *  2. Take the persistable URI permission so we keep access across
     *     reboots.
     *  3. Compute `<picked>/strictlykeptboy/` (if the picked folder
     *     already carries a marker, re-use it as-is per D-2.17.c).
     *     `mkdirs` the parent, write `.skb-root` via
     *     [com.eight87.strictlykeptboy.prefs.SkbRootMarker.write] if the
     *     marker isn't already there (idempotent re-pick of an already-skb
     *     folder leaves the marker untouched) (B.2).
     *  4. Persist
     *     [com.eight87.strictlykeptboy.prefs.ParentLocation.External]
     *     with the resolved `cachedRealPath` so the
     *     `ParentLocationGate` flips to `Confirmed` (B.2).
     *  5. Run
     *     [com.eight87.strictlykeptboy.sync.ParentReconciler.reconcile]
     *     on IO and Toast the adoption count (B.4).
     */
    private var pendingAppGraph: com.eight87.strictlykeptboy.composition.AppGraph? = null

    /**
     * Round 2.17 Phase E.5 — when true, the next parent-picker grant
     * routes through the RepoMover instead of overwriting prefs in
     * place. Set by the Storage screen's "Change folder" CTA before
     * firing `parentPickerHandle`; cleared after the picker returns.
     */
    private var parentPickerAsChangeFolder: Boolean = false

    private val parentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val graph = pendingAppGraph ?: return@registerForActivityResult
        // B.1 — resolve the real path FIRST. SD card / cloud provider
        // picks return null; Toast + bail before we write any prefs.
        val pickedRealPath = com.eight87.strictlykeptboy.prefs.SafTreeUriResolver
            .resolveRealPath(uri)
        if (pickedRealPath == null) {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.parent_picker_internal_only),
                Toast.LENGTH_LONG,
            ).show()
            return@registerForActivityResult
        }
        // Persist the SAF permission grant. Required so we can re-open
        // the tree URI across reboots even though the underlying File
        // I/O goes through the resolved real path.
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        // B.3 — label derivation moved into SafTreeUriResolver.deriveLabel.
        val label = com.eight87.strictlykeptboy.prefs.SafTreeUriResolver.deriveLabel(uri)
        // B.2 — compute the strictlykeptboy parent directory inside the
        // picked folder. If the picked folder ITSELF is already an skb
        // root (`.skb-root` present), short-circuit to using it as-is
        // (D-2.17.c happy path). Otherwise nest a `strictlykeptboy/`
        // subdir and write the marker there if missing.
        val pickedFile = java.io.File(pickedRealPath)
        val parentFile = if (com.eight87.strictlykeptboy.prefs.SkbRootMarker.isSkbRoot(pickedFile)) {
            pickedFile
        } else {
            java.io.File(pickedFile, "strictlykeptboy")
        }
        runCatching {
            parentFile.mkdirs()
            if (!com.eight87.strictlykeptboy.prefs.SkbRootMarker.isSkbRoot(parentFile)) {
                com.eight87.strictlykeptboy.prefs.SkbRootMarker.write(
                    parent = parentFile,
                    deviceName = android.os.Build.MODEL ?: "",
                )
            }
        }
        val newLocation = com.eight87.strictlykeptboy.prefs.ParentLocation.External(
            treeUri = uri.toString(),
            label = label,
            cachedRealPath = parentFile.absolutePath,
        )
        // Round 2.17 Phase E.5 — when the picker was launched as a
        // "Change folder" action (from Settings → Storage folder), route
        // through the move-job so existing repos under the previous
        // parent are physically moved instead of stranded.
        val asChangeFolder = parentPickerAsChangeFolder
        parentPickerAsChangeFolder = false
        if (asChangeFolder) {
            val oldLoc = graph.repoStoragePrefs.location
            val oldDir = oldLoc?.workingDir(filesDir)
            if (oldDir != null && oldDir.absolutePath != parentFile.absolutePath) {
                moveJobRequest.value = MoveJobRequest(
                    oldParent = oldDir,
                    newParent = parentFile,
                    oldLocation = oldLoc,
                    newLocation = newLocation,
                )
                return@registerForActivityResult
            }
        }
        graph.repoStoragePrefs.set(newLocation)
        // B.4 — reconcile against the new parent and Toast how many
        // repos got adopted (instead of "applied backup mirror to N").
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val count = runCatching { graph.parentReconciler.reconcile().size }.getOrDefault(0)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    resources.getQuantityString(R.plurals.parent_adopted_n_repos, count, count),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * Round 2.17 Phase E.4 — "Adopt existing folder" picker. Differs
     * from [parentPickerLauncher] in that we do NOT switch parents on
     * grant — we only run [com.eight87.strictlykeptboy.sync.ParentReconciler.reconcileExternal]
     * against the picked folder and surface its discoveries in a
     * confirmation sheet. The handler attached by Compose decides
     * whether to commit (switch parent + register adoptees) or cancel.
     */
    private var pendingAdoptHandler: ((Uri) -> Unit)? = null

    private val adoptPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        pendingAdoptHandler?.invoke(uri)
    }

    /**
     * Round 2.17.B.6 — SAF document picker for backup archives. The
     * `.skb-backup.tar.gz` MIME type is `application/gzip`; consumed by
     * Phase G's destructive Restore flow. The actual `pendingRestoreXxx`
     * state + handler wiring lands in Phase G; for now the launcher is
     * registered and parked under [pendingRestoreArchiveHandler] so the
     * picker contract registration is in place (registration must happen
     * before STARTED, but the consumer can attach later).
     */
    private var pendingRestoreArchiveHandler: ((Uri) -> Unit)? = null

    private val restoreArchivePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        pendingRestoreArchiveHandler?.invoke(uri)
    }

    /**
     * Round 2.17 Phase F.4 — parked handler for the "Export backup"
     * SAF launcher. The Settings → Storage → Backup/Restore screen
     * attaches a callback before launching, so the activity can write
     * the tar.gz on `Dispatchers.IO` once the user names a destination.
     */
    private var pendingExportArchiveHandler: ((Uri) -> Unit)? = null

    private val exportArchivePickerLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        pendingExportArchiveHandler?.invoke(uri)
    }

    private val createIcsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri: Uri? ->
        val content = pendingExportContent ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(this@MainActivity, getString(R.string.export_done), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkHandler?.invoke(intent)
        calendarIntentHandler?.invoke(intent)
    }

    /**
     * Round 2.18.E.6 — exposed for `IntentFilterRoutingTest`. Returns
     * the routed-intent classification so the test can assert that
     * each manifest filter dispatches into the right destination
     * without driving the whole Compose tree.
     */
    internal fun classifyIncoming(intent: Intent): com.eight87.strictlykeptboy.system.RoutedIntent =
        com.eight87.strictlykeptboy.system.CalendarIntentRouter.classify(intent)

    override fun onCreate(savedInstanceState: Bundle?) {
        com.eight87.strictlykeptboy.perf.PerfTraceRecorder.begin(
            com.eight87.strictlykeptboy.perf.PerfTraceRecorder.Section.MainActivityOnCreate,
        )
        try {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Phase Q (R.X.3 / F22) — composition root extracted.
        // Phase V.1 — trace AppGraph construction for cold-start budget.
        val graph = com.eight87.strictlykeptboy.perf.PerfTraceRecorder.trace(
            com.eight87.strictlykeptboy.perf.PerfTraceRecorder.Section.AppGraphInit,
        ) { AppGraph(applicationContext) }
        graph.parkRuntimes()
        graph.installSyncEventBridge()
        // Round 2.17.B.5 — park the SAF tree picker so Compose surfaces
        // (Settings → Storage folder, Repos reminder banner) can launch
        // it without owning an ActivityResultLauncher.
        pendingAppGraph = graph
        graph.setParentPickerHandle { parentPickerLauncher.launch(null) }

        // Round 2.17 Phase E.7 — probe persisted SAF permissions on
        // boot so the Repos pane red banner flips if the user revoked
        // access from system Settings while we were dead.
        graph.refreshSafPermissionState()

        // Phase O.2 — handle strictlykeptboy://share deep links.
        // Round 2.18.E.10 — guard: only classify URIs that actually
        // carry a share scheme. Without this guard, every calendar
        // contract intent (content://com.android.calendar/time/...,
        // file://...ics, etc.) fed to `ShareLinkReceiver.classify`
        // returns `Action.Invalid` and surfaces the user-visible
        // "share link could not be read" Toast.
        deepLinkHandler = { intent ->
            intent.dataString?.let { data ->
                if (isShareLinkScheme(data)) {
                    val action = com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver
                        .classify(data)
                    handleShareAction(action, graph.repoStore)
                }
            }
        }
        // Cold-start: process the launching intent immediately.
        intent?.dataString?.let { data ->
            if (isShareLinkScheme(data)) {
                val action = com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver
                    .classify(data)
                handleShareAction(action, graph.repoStore)
            }
        }

        // Round 2.18.E.6 — pending classification of the launching
        // intent for the system calendar contract (VIEW time/epoch,
        // VIEW events/<id>, EDIT/INSERT event, VIEW text/calendar,
        // VIEW https://<host>/<path>.ics). The dispatcher into Compose is
        // installed once `scheduleState` exists; until then we park
        // the classification so cold-start doesn't drop it.
        val pendingCalendarRouted: com.eight87.strictlykeptboy.system.RoutedIntent =
            intent?.let { classifyIncoming(it) }
                ?: com.eight87.strictlykeptboy.system.RoutedIntent.Unhandled

        // Phase P — import/export view state. Confirm callback runs the
        // writer + commit on Dispatchers.IO. R.X.3: composition root only.
        val importExportState = com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState(
            repos = graph.repoStore.state,
            onConfirmedImport = { report ->
                val repo = pendingImportRepo ?: return@ImportExportViewState
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val rootDir = File(repo.rootDir)
                        val all = (report.events + report.rules + report.exceptions)
                            .map { it as com.eight87.strictlykeptboy.store.TypedEntity }
                        com.eight87.strictlykeptboy.store.EntityWriter.writeBatch(rootDir, all)
                        val gitRepo = GitRepoRegistry.get(repo.repoId)
                            ?: GitRepo.open(
                                rootDir = rootDir,
                                repoId = repo.repoId,
                                remotes = repo.remotes,
                                primaryRemote = repo.primaryRemote,
                                authorIdentity = repo.authorIdentity,
                                defaultBranch = repo.defaultBranch,
                            ).also(GitRepoRegistry::put)
                        gitRepo.commitAll("import: ${report.totalEntities} entities from .ics")
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            resources.getQuantityString(
                                R.plurals.import_done,
                                report.totalEntities,
                                report.totalEntities,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
        onParsed = { report -> importExportState.showPreview(report) }

        setContent {
            val appearance by graph.appearancePrefs.state.collectAsState()
            var ageOk by remember { mutableStateOf(graph.ageGatePrefs.isConfirmed()) }
            // Phase 2.1.I.1 — first-launch auto-route to the wizard when the
            // repo store is empty after the age gate succeeds. We flip
            // `firstLaunchDone = true` once a repo exists (either after the
            // wizard scaffold lands, or because the user already had repos
            // from a prior install). The empty-Schedule flash is avoided by
            // NOT mounting SkbAppShell on first launch.
            var firstLaunchDone by remember {
                mutableStateOf(graph.repoStore.list().isNotEmpty())
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.eight87.strictlykeptboy.avatar.LocalAvatarResolver provides graph.avatarResolver,
                com.eight87.strictlykeptboy.ui.repos.LocalAvatarPackPrefs provides graph.avatarPackPrefs,
                com.eight87.strictlykeptboy.ui.repos.LocalPackStore provides graph.packStore,
                com.eight87.strictlykeptboy.ui.repos.LocalAssetPackLoader provides graph.assetPackLoader,
                com.eight87.strictlykeptboy.ui.repos.LocalUserPackLoader provides graph.userPackLoader,
            ) {
            StrictlyKeptBoyTheme(
                themeMode = appearance.themeMode,
                densityScale = appearance.densityScale,
                dynamicColor = appearance.dynamicColor,
            ) {
                val scope = rememberCoroutineScope()
                // Phase 2.1.J.1 / 2.1.K.1 — keep IdentityPrefs + ModePrefs bound
                // to the active repo so settings edits round-trip to disk +
                // commit. Reacts to defaultWriteRepoName flips (wizard finish,
                // repo switcher, etc.). Idempotent at the bind layer.
                val activeRepoName by graph.defaultWriteRepoName.collectAsState()
                LaunchedEffect(activeRepoName) {
                    val cfg = graph.repoStore.list().firstOrNull { it.displayName == activeRepoName }
                        ?: graph.repoStore.list().firstOrNull()
                    graph.bindIdentityToActiveRepo(cfg?.repoId)
                    graph.bindModeToActiveRepo(cfg?.repoId)
                }
                // Round 2.9 — age gate dropped per user direction ("don't even
                // ask if the app is 18+, immediately go to setup"). Age
                // confirmation is silently auto-marked so the existing prefs
                // flow doesn't re-prompt elsewhere.
                LaunchedEffect(Unit) {
                    if (!ageOk) {
                        graph.ageGatePrefs.confirm()
                        ageOk = true
                    }
                }
                // Round 2.18.B.6 — first-run "new calendar accounts
                // detected" nudge. Observe AccountChangeNudge.shouldShowNudge
                // and surface a one-shot Toast pointing the user at the
                // External Calendars settings screen. markShown() flips
                // the flag back off so we don't pester on every account
                // change.
                val showNudge by graph.accountChangeNudge.shouldShowNudge.collectAsState()
                if (showNudge) {
                    val nudgeText = stringResource(R.string.settings_external_calendars_nudge)
                    LaunchedEffect(showNudge) {
                        Toast.makeText(this@MainActivity, nudgeText, Toast.LENGTH_LONG).show()
                        graph.accountChangeNudge.markShown()
                    }
                }
                if (!firstLaunchDone) {
                    // Round 2.15 — demo-first onboarding. The intro wizard
                    // is two screens: manifesto + perspective picker. On
                    // pick we seed a read-only demo repo and drop the user
                    // straight into the app.
                    com.eight87.strictlykeptboy.ui.wizard.intro.IntroWizardHost(
                        onPerspectiveChosen = { choice ->
                            scope.launch {
                                // Round 2.22 follow-up — empty pick: skip
                                // seeding entirely, drop user on empty
                                // Schedule. They build their own repo from
                                // the "+ New" entry-point.
                                if (choice is com.eight87.strictlykeptboy.ui.wizard.intro.DemoPerspectiveChoice.Empty) {
                                    graph.demoModePrefs.setActive(false)
                                    firstLaunchDone = true
                                    return@launch
                                }
                                val config = when (choice) {
                                    is com.eight87.strictlykeptboy.ui.wizard.intro.DemoPerspectiveChoice.Empty -> error("handled above")
                                    is com.eight87.strictlykeptboy.ui.wizard.intro.DemoPerspectiveChoice.Lifestyle -> {
                                        val card = choice.card
                                        val outcome = com.eight87.strictlykeptboy.demo.DemoRepoSeeder.seed(
                                            parentDir = filesDir.resolve("demo-repos")
                                                .resolve(com.eight87.strictlykeptboy.demo.DemoRepoSeeder.folderName(card)),
                                            perspective = card,
                                            author = AuthorIdentity("demo", "demo@strictlykeptboy.local"),
                                            assetPackLoader = graph.assetPackLoader,
                                        )
                                        graph.demoModePrefs.setPerspective(card)
                                        RepoConfig(
                                            repoId = outcome.repoId,
                                            displayName = "demo · ${card.name.lowercase()}",
                                            rootDir = outcome.rootDir.absolutePath,
                                            remotes = emptyList(),
                                            primaryRemote = null,
                                            authorIdentity = outcome.authorIdentity,
                                            defaultCalendarId = outcome.calendarIds.values.firstOrNull(),
                                            defaultTodolistId = outcome.todolistId,
                                            iconEmoji = "🦇",
                                            iconSpecies = "Bat",
                                            isDemo = true,
                                        )
                                    }
                                    is com.eight87.strictlykeptboy.ui.wizard.intro.DemoPerspectiveChoice.RichDemo -> {
                                        // Round 2.20 Phase C.4 — dispatch to
                                        // RichDemoSeeder; share the same
                                        // RepoStore.add tail as the legacy
                                        // demo flow.
                                        val parent = filesDir.resolve("demo-repos")
                                        val repoRoot = graph.richDemoSeeder
                                            .seedIfNeeded(parent)
                                            .getOrThrow()
                                        // RichDemo doesn't map to a
                                        // LifestyleCard; mark demo active
                                        // without a perspective so the
                                        // first-launch routing keys off
                                        // RepoStore non-emptiness instead.
                                        graph.demoModePrefs.setActive(true)
                                        com.eight87.strictlykeptboy.demo
                                            .RichDemoRegistrar.buildConfig(repoRoot)
                                    }
                                }
                                graph.repoStore.add(config)
                                // Round 2.20 Phase D.8 — fix Phase C's "nothing
                                // scheduled" finding. RepoStore.add does not
                                // trigger an indexer pass, and the rich-demo
                                // ships ~120 events / ~30 recurrences only on
                                // disk. Without indexing, Room stays empty +
                                // the schedule view renders no bands. Initialise
                                // a local-only git repo if needed (the indexer
                                // records the head SHA into RepoStateRow), then
                                // run a full scan. Idempotent + crash-safe via
                                // runCatching — a failure surfaces as an empty
                                // schedule rather than a fatal first launch.
                                if (config.isDemo) {
                                    kotlinx.coroutines.withContext(
                                        kotlinx.coroutines.Dispatchers.IO,
                                    ) {
                                        runCatching {
                                            val rootDir = File(config.rootDir)
                                            val gitRepo = GitRepoRegistry.get(config.repoId)
                                                ?: run {
                                                    val gd = File(rootDir, ".git")
                                                    if (gd.isDirectory) {
                                                        GitRepo.open(
                                                            rootDir = rootDir,
                                                            repoId = config.repoId,
                                                            remotes = config.remotes,
                                                            primaryRemote = config.primaryRemote,
                                                            authorIdentity = config.authorIdentity,
                                                            defaultBranch = config.defaultBranch,
                                                        )
                                                    } else {
                                                        GitRepo.initLocalOnly(
                                                            rootDir = rootDir,
                                                            repoId = config.repoId,
                                                            authorIdentity = config.authorIdentity,
                                                        ).also { fresh ->
                                                            runCatching {
                                                                fresh.commitAll(
                                                                    "rich-demo: initial extraction",
                                                                )
                                                            }
                                                        }
                                                    }
                                                }.also(GitRepoRegistry::put)
                                            com.eight87.strictlykeptboy.cache.Indexer(
                                                graph.cacheDatabase,
                                            ).fullScan(config.repoId, gitRepo)
                                        }
                                    }
                                }
                                graph.setDefaultWriteRepoName(config.displayName)
                                firstLaunchDone = true
                            }
                        },
                    )
                } else {
                    val scheduleState = remember {
                        ScheduleViewState(
                            scope = scope,
                            snapshotFlow = graph.snapshot,
                            sourcesFlow = graph.sources,
                            calendarsFlow = graph.calendarRegistry.state,
                            visibilityFlow = graph.calendarVisibility.state,
                            repoConfigsFlow = graph.repoStore.state,
                            initialTab = graph.viewModePrefs.selected.value,
                        )
                    }
                    // Round 2.18.E.6 — install the live calendar-intent
                    // dispatcher now that `scheduleState` exists. Routes:
                    //
                    //   GoToDate → pin Schedule + switch to Day tab.
                    //   ShowEvent → toast (real cross-row mapping is
                    //     Phase F+G; for E we surface the row id so the
                    //     user sees the intent took).
                    //   EditEvent → open the event editor via the
                    //     existing EventCreateController; prefilled fields
                    //     come from CalendarContract extras.
                    //   ImportIcs → fetch (if remote) + parse + hand to
                    //     `importExportState.showPreview`.
                    //
                    // Also drain the cold-start pending classification.
                    val handleRoutedIntent: (com.eight87.strictlykeptboy.system.RoutedIntent) -> Unit = routed@{ routed ->
                        when (routed) {
                            is com.eight87.strictlykeptboy.system.RoutedIntent.GoToDate -> {
                                scheduleState.setDate(routed.date)
                                scheduleState.setSelectedTab(
                                    com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab.Day,
                                )
                            }
                            is com.eight87.strictlykeptboy.system.RoutedIntent.ShowEvent -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Open event ${'$'}{routed.eventId}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            is com.eight87.strictlykeptboy.system.RoutedIntent.EditEvent -> {
                                // Prefill the event editor. Today (Phase E)
                                // we open a fresh editor pinned to the
                                // begin-time date; full prefill into the
                                // EventCreateController draft fields lands
                                // with Phase F.
                                routed.prefill.beginMs?.let { ms ->
                                    val d = java.time.Instant.ofEpochMilli(ms)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate()
                                    scheduleState.setDate(d)
                                }
                                Toast.makeText(
                                    this@MainActivity,
                                    routed.prefill.title?.let { "Edit: ${'$'}it" }
                                        ?: getString(R.string.app_name),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            is com.eight87.strictlykeptboy.system.RoutedIntent.ImportIcs -> {
                                val repo = graph.repoStore.list()
                                    .firstOrNull { it.displayName == graph.defaultWriteRepoName.value }
                                    ?: graph.repoStore.list().firstOrNull()
                                if (repo == null) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.import_export_empty),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    return@routed
                                }
                                pendingImportRepo = repo
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val text = runCatching {
                                        when (val src = routed.source) {
                                            is com.eight87.strictlykeptboy.system.IcsSource.LocalUri ->
                                                contentResolver.openInputStream(src.uri)
                                                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                                            is com.eight87.strictlykeptboy.system.IcsSource.RemoteUrl -> {
                                                val client = okhttp3.OkHttpClient()
                                                val req = okhttp3.Request.Builder()
                                                    .url(src.uri.toString())
                                                    .build()
                                                client.newCall(req).execute().use { resp ->
                                                    resp.body?.string()
                                                }
                                            }
                                        }
                                    }.getOrNull() ?: return@launch
                                    val calendarId = repo.defaultCalendarId
                                        ?: com.eight87.strictlykeptboy.git.Uuid7.generate().toString()
                                    val report = com.eight87.strictlykeptboy.port.ics.IcsParser.parse(
                                        text = text,
                                        calendarId = calendarId,
                                        author = repo.authorIdentity.email,
                                    )
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        importExportState.showPreview(report)
                                    }
                                }
                            }
                            com.eight87.strictlykeptboy.system.RoutedIntent.Unhandled -> Unit
                        }
                    }
                    calendarIntentHandler = { intentArg ->
                        handleRoutedIntent(classifyIncoming(intentArg))
                    }
                    LaunchedEffect(Unit) {
                        handleRoutedIntent(pendingCalendarRouted)
                    }
                    // Phase 2.1.D.1 — TasksViewState bound to the resolver.
                    // Re-evaluates active-todolist IDs whenever the snapshot
                    // changes. Also pumps multiRepo / activeRepoOwner from
                    // the repo store + active write target. Owned here (not
                    // hoisted) so SkbAppShell can keep its remember-default.
                    // Round 2.16.B — hoisted onto AppGraph so the playback
                    // projector and the UI share one canonical instance.
                    val tasksViewState = graph.tasksViewState
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val evaluator = com.eight87.strictlykeptboy.resolver.ActiveSetEvaluator()
                        // Round 2.26.F.1 — lazy todolist.toml resolver,
                        // cached across DAO reads so we don't re-parse
                        // `<repoRoot>/todolists/<id>/todolist.toml` on
                        // every snapshot tick. Cleared whenever the
                        // configured repo set changes (rootDir may change).
                        // (uuid → info) cache, populated per repo via a
                        // full scan of <repoRoot>/todolists/*/todolist.toml
                        // since the on-disk dir name is a slug, not the
                        // UUID that task frontmatter references.
                        val todolistByUuid =
                            java.util.concurrent.ConcurrentHashMap<String, com.eight87.strictlykeptboy.ui.tasks.TodolistInfo>()
                        val scannedRepos = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
                        var lastRepoIdsKey = ""
                        fun scanRepoTomls(repoRoot: java.io.File, repoId: String) {
                            if (scannedRepos.putIfAbsent("$repoId@${repoRoot.absolutePath}", true) != null) return
                            val todolistsDir = java.io.File(repoRoot, "todolists")
                            if (!todolistsDir.isDirectory) return
                            todolistsDir.listFiles()?.forEach { dir ->
                                val toml = java.io.File(dir, "todolist.toml")
                                if (!toml.isFile) return@forEach
                                runCatching {
                                    val txt = toml.readText(Charsets.UTF_8)
                                    val tbl = com.eight87.strictlykeptboy.store.TomlReader.parse(txt)
                                    val uuid = tbl.getString("id") ?: return@runCatching
                                    val displayName = tbl.getString("name")
                                    val emoji = tbl.getString("emoji")
                                    val colorSeed = tbl.getString("color")
                                        ?: tbl.getString("color_seed")
                                    val priority = tbl.getInt("priority") ?: 0
                                    val modeStr = tbl.getString("mode")?.lowercase()
                                    val mode = if (modeStr == "shopping") {
                                        com.eight87.strictlykeptboy.ui.tasks.TodolistMode.Shopping
                                    } else com.eight87.strictlykeptboy.ui.tasks.TodolistMode.Standard
                                    todolistByUuid["$repoId::$uuid"] =
                                        com.eight87.strictlykeptboy.ui.tasks.buildTodolistInfo(
                                            todolistId = uuid,
                                            repoId = repoId,
                                            meta = null,
                                            fallbackDisplayName = displayName,
                                            fallbackEmoji = emoji,
                                            fallbackColorSeed = colorSeed,
                                            fallbackPriority = priority,
                                            fallbackMode = mode,
                                        )
                                }
                            }
                        }
                        fun readTomlInfo(
                            repoRoot: java.io.File,
                            repoId: String,
                            todolistId: String,
                            meta: com.eight87.strictlykeptboy.resolver.TodolistMeta?,
                        ): com.eight87.strictlykeptboy.ui.tasks.TodolistInfo {
                            scanRepoTomls(repoRoot, repoId)
                            todolistByUuid["$repoId::$todolistId"]?.let { return it }
                            return com.eight87.strictlykeptboy.ui.tasks.buildTodolistInfo(
                                todolistId = todolistId,
                                repoId = repoId,
                                meta = meta,
                            )
                        }
                        kotlinx.coroutines.flow.combine(
                            graph.snapshot,
                            graph.defaultWriteRepoName,
                            graph.repoStore.state,
                        ) { snap, writeName, repos ->
                            Triple(snap, writeName, repos)
                        }.collect { (snap, writeName, repos) ->
                            // Round 2.5.D.2 — filter snapshot to repos with
                            // `drawTasksFrom = true` BEFORE evaluating the
                            // active set. Todolists from `drawTasksFrom =
                            // false` repos never reach `activeTodolistIds`,
                            // so Combined / Today views won't surface their
                            // tasks.
                            val drawRepoIds = repos
                                .filter { it.drawTasksFrom }
                                .map { it.repoId }
                                .toSet()
                            val filteredSnap = if (drawRepoIds.isEmpty()) {
                                snap
                            } else {
                                snap.copy(
                                    todolists = snap.todolists.filter {
                                        it.repo.id in drawRepoIds
                                    },
                                )
                            }
                            val ids = com.eight87.strictlykeptboy.ui.tasks.evaluateActiveTodolistIds(
                                evaluator,
                                filteredSnap,
                            )
                            val activeRepo = repos.firstOrNull { it.displayName == writeName }
                                ?: repos.firstOrNull()
                            val owner = activeRepo?.authorIdentity?.name.orEmpty()
                            val cur = tasksViewState.state.value
                            // Phase 2.1.D.8 — synthesize FromEvents tasks
                            // from today's MaterializedInstances. Today's
                            // wiring is best-effort: we don't have a live
                            // resolver fold here yet, so use today's
                            // one-off events (via `graph.todayEventSource`).
                            // Round 2.27 / Phase B.3 — per-(repoId, calId, ruleId)
                            // response-file cache for one projector call.
                            val responseCache = mutableMapOf<Triple<String, String, String>, Set<java.time.LocalDate>>()
                            val reposById = repos.associateBy { it.repoId }
                            val responseReader: (String, String) -> Set<java.time.LocalDate> = { calId, ruleId ->
                                val cal = snap.calendars.firstOrNull { it.ref.id == calId }
                                val repoId = cal?.repo?.id
                                val repoRoot = repoId?.let { reposById[it]?.rootDir }
                                if (repoRoot == null) emptySet()
                                else responseCache.getOrPut(Triple(repoId, calId, ruleId)) {
                                    runCatching {
                                        com.eight87.strictlykeptboy.store.PromptResponseReader
                                            .listAnsweredInstances(
                                                repoRoot = java.nio.file.Paths.get(repoRoot),
                                                calId = calId,
                                                ruleId = ruleId,
                                            )
                                    }.getOrDefault(emptySet())
                                }
                            }
                            // Round 2.27 / Phase B.2 — boy persona id. No
                            // field on identity.toml carries this today;
                            // empty string disables the single-user-self-
                            // prompt skip until the field lands.
                            val boyAuthorId = ""
                            val fromEvents =
                                com.eight87.strictlykeptboy.ui.tasks.FromEventsProjector.project(
                                    instances = graph.todayEventSource.eventsForToday().map { it.instance },
                                    calendarsById = snap.calendars.associateBy { it.ref.id },
                                    boyAuthorId = boyAuthorId,
                                    responseReader = responseReader,
                                )
                            // Merge: keep non-FromEvents tasks the caller
                            // pushed in via `set/addTask`; replace the
                            // FromEvents slice with the freshly projected
                            // set. This is the producer the brief noted is
                            // missing for the `TaskSource.FromEvents` enum.
                            val nonFromEvents = cur.tasks.filter {
                                it.source != com.eight87.strictlykeptboy.ui.tasks.TaskSource.FromEvents &&
                                    it.source != com.eight87.strictlykeptboy.ui.tasks.TaskSource.KeeperPrompt
                            }
                            // Round 2.16.C — temp sub-stepped demo tasks so
                            // the mini-player has visible content on the AVD
                            // (petkeptbyai demo perspective ships no tasks).
                            // TODO Phase D — remove once in-sheet creation
                            // can author sub-stepped tasks directly.
                            val demoSubstepped =
                                com.eight87.strictlykeptboy.ui.tasks.TasksDemoSeed.substeppedDemoTasks
                            val hasDemo = nonFromEvents.any { t ->
                                demoSubstepped.any { it.id == t.id }
                            }
                            val withDemo = if (hasDemo) nonFromEvents
                            else nonFromEvents + demoSubstepped
                            // Round 2.27 / Phase C — surface a demo
                            // KeeperPrompt set so the AVD smoke-test
                            // shows the glyph + chip + pill + Respond
                            // affordances even when the Room → events
                            // pipeline hasn't lit up yet.
                            val demoPrompts =
                                com.eight87.strictlykeptboy.ui.tasks.TasksDemoSeed.keeperPromptDemoTasks
                            val withPrompts = withDemo + demoPrompts
                            // Round 2.26.F.1 — pull real disk-backed tasks
                            // from the Room cache for every repo flagged
                            // `drawTasksFrom`. Maps via TaskEntityMapping
                            // + todolist.toml lazy resolver so the rows
                            // carry real list names (not raw ids).
                            val repoIdsKey = repos.joinToString(",") { "${it.repoId}@${it.rootDir}" }
                            if (repoIdsKey != lastRepoIdsKey) {
                                todolistByUuid.clear()
                                scannedRepos.clear()
                                lastRepoIdsKey = repoIdsKey
                            }
                            val drawingRepos = repos.filter { it.drawTasksFrom }
                            val realTasks = if (drawingRepos.isEmpty()) emptyList()
                            else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val out = mutableListOf<com.eight87.strictlykeptboy.ui.tasks.TaskItem>()
                                for (cfg in drawingRepos) {
                                    val repoRoot = java.io.File(cfg.rootDir)
                                    val taskRows = runCatching {
                                        graph.cacheDatabase.tasks().listAll(cfg.repoId)
                                    }.getOrElse { emptyList() }
                                    val standingRows = runCatching {
                                        graph.cacheDatabase.standingTasks().listAll(cfg.repoId)
                                    }.getOrElse { emptyList() }
                                    for (row in taskRows) {
                                        val meta = snap.todolists.firstOrNull {
                                            it.ref.id == row.todolistId && it.repo.id == cfg.repoId
                                        }
                                        val info = readTomlInfo(repoRoot, cfg.repoId, row.todolistId, meta)
                                        out += row.toTaskItem(info)
                                    }
                                    for (row in standingRows) {
                                        val meta = snap.todolists.firstOrNull {
                                            it.ref.id == row.todolistId && it.repo.id == cfg.repoId
                                        }
                                        val info = readTomlInfo(repoRoot, cfg.repoId, row.todolistId, meta)
                                        out += row.toTaskItem(info)
                                    }
                                }
                                out
                            }
                            // De-dupe: real-disk wins over demo/FromEvents
                            // collisions by id.
                            val realIds = realTasks.mapTo(mutableSetOf()) { it.id }
                            val mergedBase = realTasks + withPrompts.filter { it.id !in realIds } +
                                fromEvents.filter { it.id !in realIds }
                            // Round 2.27 / Phase C — ensure the demo
                            // KeeperPrompt todolist id is considered
                            // active so the rows are visible under
                            // `visibleTasks()`.
                            val idsWithDemo = ids + "demo-keeper"
                            tasksViewState.set(
                                cur.copy(
                                    tasks = mergedBase,
                                    activeTodolistIds = idsWithDemo,
                                    multiRepo = repos.size > 1,
                                    activeRepoOwner = owner,
                                ),
                            )
                        }
                    }
                    val togetherVm = remember(scope) {
                        TogetherViewModel(
                            scope = scope,
                            repoOptionsFlow = graph.togetherRepoOptions,
                            busySource = graph.emptyBusySource,
                            finder = graph.finderPort,
                        )
                    }
                    val eventCreateController = remember {
                        com.eight87.strictlykeptboy.ui.schedule.EventCreateController(
                            scope = scope,
                            prefs = graph.eventCreatePrefs,
                            context = applicationContext,
                            activeRepoProvider = {
                                val name = graph.defaultWriteRepoName.value
                                graph.repoStore.list().firstOrNull { it.displayName == name }
                                    ?: graph.repoStore.list().firstOrNull()
                            },
                            calendarOptionsProvider = {
                                val list = graph.repoStore.list()
                                list.mapNotNull { cfg ->
                                    val calId = cfg.defaultCalendarId ?: return@mapNotNull null
                                    com.eight87.strictlykeptboy.ui.schedule.CalendarOption(
                                        id = calId,
                                        displayName = cfg.displayName,
                                        repoDisplayName = if (list.size > 1) cfg.displayName else null,
                                    )
                                }
                            },
                            neutralModeProvider = { graph.neutralModePrefs.isEnabled() },
                        )
                    }
                    // Phase 2.1.I.4 — share-with-dom CTA needs the active
                    // repo. We resolve at click-time so the latest scaffold
                    // outcome is observed. Falls back to a toast if no repo.
                    var pendingShareRepo by remember { mutableStateOf<RepoConfig?>(null) }
                    // Round 2.1.B.2 / B.4 — pending calendar to edit. Long-press
                    // on a chip routes here; the sheet writes back via
                    // CalendarSettingsSheet → RoutineCalendarConfig +
                    // SupersedenceConfig + CalendarActivityConfig codecs.
                    var pendingCalendarEdit by remember {
                        mutableStateOf<com.eight87.strictlykeptboy.resolver.CalendarMeta?>(null)
                    }
                    SkbAppShell(
                        tasksState = tasksViewState,
                        // Round 2.16.B — wire the real projector + transport
                        // adapter so MiniPlayer/NowPlayingScreen read live
                        // active-task state. Temp Start affordance on
                        // TaskRow → controller.start(taskId).
                        taskPlaybackSource = graph.taskTransport,
                        onStartTask = { taskId -> graph.activeTaskController.start(taskId) },
                        // Round 2.27 / Phase D.3 — keeper-prompt response
                        // submitter. Writes the response file via
                        // PromptResponseWriter on the repo root resolved
                        // from the task's repoId.
                        onPromptRespond = { task, body, attachment ->
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    writePromptResponse(
                                        task = task,
                                        body = body,
                                        attachment = attachment,
                                    )
                                }
                            }
                        },
                        // Round 2.27 / Phase C.3 — long-press → synthetic
                        // empty response so the row clears without the
                        // sheet.
                        onPromptMarkAnsweredOffline = { task ->
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    writePromptResponse(
                                        task = task,
                                        body = "(marked answered offline)",
                                        attachment = null,
                                    )
                                }
                            }
                        },
                        nowNextFlow = graph.nowNextFlow,
                        activeRepoNameFlow = graph.defaultWriteRepoName,
                        activeIconKindFlow = graph.activeRepoIconKind,
                        wizardEntryRequest = graph.wizardEntryRequest,
                        calendarVisibility = graph.calendarVisibility,
                        onLongPressCalendar = { meta -> pendingCalendarEdit = meta },
                        // Round 2.22 / Fix 3 — inline priority writer for the
                        // OverlayPicker per-row OutlinedTextField. Drops +
                        // rewrites the `priority` scalar in the calendar's
                        // calendar.toml and commits via GitRepoRegistry.
                        onOverlayPriorityChange = { meta, newPriority ->
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsWriter
                                    .writePriority(
                                        repoStore = graph.repoStore,
                                        repoId = meta.repo.id,
                                        calendarId = meta.ref.id,
                                        calendarDisplayName = meta.displayName,
                                        newPriority = newPriority,
                                    )
                            }
                        },
                        // Round 2.23.2 / D.119 — inline color writer for the
                        // OverlayPicker per-card Color row. Rewrites the
                        // `color_seed` scalar in calendar.toml and commits.
                        onOverlayColorChange = { meta, rgb ->
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsWriter
                                    .writeColorSeed(
                                        repoStore = graph.repoStore,
                                        repoId = meta.repo.id,
                                        calendarId = meta.ref.id,
                                        calendarDisplayName = meta.displayName,
                                        colorSeed = rgb,
                                    )
                            }
                        },
                        onShareWithDom = {
                            val name = graph.defaultWriteRepoName.value
                            val cfg = graph.repoStore.list()
                                .firstOrNull { it.displayName == name }
                                ?: graph.repoStore.list().firstOrNull()
                            pendingShareRepo = cfg
                        },
                        // Round 2.17.D — "Keep inside the app" wizard CTA.
                        // Write Internal parent + create the .skb-root marker
                        // so `ParentLocationGate` flips to Confirmed; the
                        // wizard's LaunchedEffect on prefs.state advances
                        // past the Storage step automatically.
                        onPickInternalStorage = {
                            val parentDir = filesDir.resolve("strictlykeptboy")
                            runCatching {
                                parentDir.mkdirs()
                                if (!com.eight87.strictlykeptboy.prefs.SkbRootMarker.isSkbRoot(parentDir)) {
                                    com.eight87.strictlykeptboy.prefs.SkbRootMarker.write(
                                        parent = parentDir,
                                        deviceName = android.os.Build.MODEL ?: "",
                                    )
                                }
                            }
                            graph.repoStoragePrefs.set(
                                com.eight87.strictlykeptboy.prefs.ParentLocation.Internal(
                                    absPath = parentDir.absolutePath,
                                ),
                            )
                        },
                        scheduleState = scheduleState,
                        eventCreateController = eventCreateController,
                        onPersistTab = graph.viewModePrefs::set,
                        reposState = graph.reposState,
                        secretsStore = graph.secretsStore,
                        togetherViewModel = togetherVm,
                        importExportState = importExportState,
                        onPickImportFile = { repo ->
                            pendingImportRepo = repo
                            openIcsLauncher.launch(arrayOf("text/calendar", "text/*", "*/*"))
                        },
                        onPickExportFile = { repo ->
                            pendingExportRepo = repo
                            scope.launch {
                                runCatching {
                                    pendingExportContent = buildExportContent(repo)
                                    createIcsLauncher.launch("${repo.displayName.ifBlank { "calendar" }}.ics")
                                }
                            }
                        },
                        onSyncClick = {
                            if (graph.repoStore.list().any { it.remotes.isNotEmpty() }) {
                                SyncService.startSyncAll(this@MainActivity)
                            }
                        },
                        neutralMode = graph.neutralModePrefs.isEnabled(),
                        settingsAccess = com.eight87.strictlykeptboy.ui.settings.SettingsAccess(
                            // Round 2.18.B.3 — External Calendars wiring.
                            systemCalendarPrefs = graph.systemCalendarPrefsStore,
                            systemCalendarsFlow = graph.systemCalendarsRawFlow,
                            // Round 2.18.G.6 — publish-to-OS toggle.
                            onPublishToOsChanged = { on ->
                                if (on) graph.skbAccountManager.enableForAllRepos()
                                else graph.skbAccountManager.disableForAllRepos()
                            },
                            syncPrefs = graph.syncSettingsPrefs,
                            statusStore = graph.statusStore,
                            notificationPrefs = graph.notificationPrefs,
                            calendarVisibility = graph.calendarVisibility,
                            todolistVisibility = graph.todolistVisibility,
                            calendarsFlow = graph.calendarRegistry.state,
                            onEditCalendar = { meta -> pendingCalendarEdit = meta },
                            identityPrefs = graph.identityPrefs,
                            appearancePrefs = graph.appearancePrefs,
                            neutralPrefs = graph.neutralModePrefs,
                            modePrefs = graph.modePrefs,
                            // Phase WW.5 — sticker pack picker access.
                            avatarPackPrefs = graph.avatarPackPrefs,
                            packStore = graph.packStore,
                            templateIds = listOf(
                                "atomic-medical",
                                "atomic-flight",
                                "atomic-household",
                                "atomic-leisure",
                            ),
                            onOpenRepoLink = {
                                val url = "https://github.com/887/strictlykeptboy"
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            // Phase W.7 — privacy policy lives in-repo at
                            // docs/privacy-policy.md; the canonical URL is the
                            // GitHub rendering of that file on the main branch.
                            // Phase 2.1.I.2 — fix the broken K.12 re-entry.
                            // Setting `wizardEntryRequest` makes SkbAppShell
                            // switch to the Wizard destination and pass
                            // `initialScreen = Roles`. Reset to null on
                            // wizard finish (handled inside SkbAppShell).
                            onOpenWizardAtRoles = {
                                graph.setWizardEntryRequest(
                                    com.eight87.strictlykeptboy.ui.wizard.WizardScreen.Roles,
                                )
                            },
                            onOpenPrivacyPolicy = {
                                val url = "https://github.com/887/strictlykeptboy/blob/main/docs/privacy-policy.md"
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            // Round 2.7.B.4-UI — backup folder picker access.
                            repoStoragePrefs = graph.repoStoragePrefs,
                            // Round 2.17 Phase E.7 — SAF permission state.
                            safPermissionRevokedFlow = graph.safPermissionRevoked,
                            onPickBackupFolder = {
                                graph.parentPickerHandle?.invoke()
                            },
                            onRemoveBackupFolder = {
                                // Round 2.17.A — legacy hook. Phase E.3
                                // replaces this surface with the explicit
                                // "Switch to internal" button below; this
                                // callback is no longer wired into the
                                // Storage category itself but stays as a
                                // no-op fallback for any legacy caller.
                            },
                            // Round 2.17 Phase E.4 — "Adopt existing folder"
                            // entry-point. Stashes the handler that runs the
                            // reconcile + confirmation sheet, then fires the
                            // SAF picker.
                            onChangeStorageFolder = {
                                // Route the next parent-picker grant through
                                // RepoMover (Phase E.5) rather than the
                                // set-in-place behaviour the wizard/banner
                                // use.
                                parentPickerAsChangeFolder = true
                                graph.parentPickerHandle?.invoke()
                            },
                            onAdoptExistingFolder = {
                                pendingAdoptHandler = handler@{ pickedUri ->
                                    val realPath = com.eight87.strictlykeptboy.prefs.SafTreeUriResolver
                                        .resolveRealPath(pickedUri)
                                    if (realPath == null) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.parent_picker_internal_only),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        return@handler
                                    }
                                    runCatching {
                                        contentResolver.takePersistableUriPermission(
                                            pickedUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    }
                                    val pickedFile = java.io.File(realPath)
                                    val parentFile = if (
                                        com.eight87.strictlykeptboy.prefs.SkbRootMarker.isSkbRoot(pickedFile)
                                    ) pickedFile else java.io.File(pickedFile, "strictlykeptboy")
                                    adoptSheetRequest.value = AdoptSheetState(
                                        treeUri = pickedUri.toString(),
                                        label = com.eight87.strictlykeptboy.prefs.SafTreeUriResolver
                                            .deriveLabel(pickedUri),
                                        parentDir = parentFile,
                                        hasMarker = com.eight87.strictlykeptboy.prefs.SkbRootMarker
                                            .isSkbRoot(parentFile),
                                    )
                                }
                                adoptPickerLauncher.launch(null)
                            },
                            // Round 2.17 Phase F.3/F.4 — "Export backup"
                            // row: park a handler, fire the SAF
                            // CreateDocument picker with a suggested
                            // dated filename, then on grant stream the
                            // parent through BackupArchiver on IO.
                            onExportBackup = export@{
                                val parentDir = graph.repoStoragePrefs.location
                                    ?.workingDir(filesDir)
                                if (parentDir == null || !parentDir.isDirectory) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.backuprestore_export_failed),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    return@export
                                }
                                pendingExportArchiveHandler = handler@{ destUri ->
                                    val locSnap = graph.repoStoragePrefs.location
                                    val label = when (locSnap) {
                                        null -> ""
                                        is com.eight87.strictlykeptboy.prefs.ParentLocation.Internal ->
                                            "internal"
                                        is com.eight87.strictlykeptboy.prefs.ParentLocation.External ->
                                            locSnap.label
                                    }
                                    kotlinx.coroutines.GlobalScope.launch(
                                        kotlinx.coroutines.Dispatchers.IO,
                                    ) {
                                        val result = runCatching {
                                            contentResolver.openOutputStream(destUri)?.use { os ->
                                                com.eight87.strictlykeptboy.backup.BackupArchiver.export(
                                                    parent = parentDir,
                                                    out = os,
                                                    skbVersion = BuildConfig.VERSION_NAME,
                                                    parentLabel = label,
                                                    deviceName = android.os.Build.MODEL ?: "",
                                                )
                                            } ?: error("openOutputStream returned null")
                                        }
                                        kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.Main,
                                        ) {
                                            result.fold(
                                                onSuccess = { manifest ->
                                                    val pretty = humanBytes(manifest.bytesWritten)
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        getString(
                                                            R.string.backuprestore_export_done,
                                                            pretty,
                                                        ),
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                },
                                                onFailure = {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        getString(R.string.backuprestore_export_failed),
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                },
                                            )
                                        }
                                    }
                                }
                                val today = java.time.LocalDate.now().toString()
                                exportArchivePickerLauncher.launch(
                                    "strictlykeptboy-backup-$today.tar.gz",
                                )
                            },
                            // Round 2.17 Phase G.3 — "Restore from current
                            // folder". Dialog has confirmed by the time
                            // this runs. Rescan the parent on disk, replace
                            // the RepoStore contents, then trigger a full
                            // re-index over the new set.
                            onRestoreFromFolder = restore@{
                                val parentDir = graph.repoStoragePrefs.location
                                    ?.workingDir(filesDir)
                                if (parentDir == null || !parentDir.isDirectory) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.backuprestore_restore_failed),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    return@restore
                                }
                                kotlinx.coroutines.GlobalScope.launch(
                                    kotlinx.coroutines.Dispatchers.IO,
                                ) {
                                    val result = runCatching {
                                        val configs = com.eight87.strictlykeptboy.backup
                                            .BackupRestorer.rescanParent(parentDir)
                                        graph.repoStore.replaceAll(configs)
                                        reindexAfterRestore(graph, configs)
                                        configs.size
                                    }
                                    kotlinx.coroutines.withContext(
                                        kotlinx.coroutines.Dispatchers.Main,
                                    ) {
                                        result.fold(
                                            onSuccess = { n ->
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    resources.getQuantityString(
                                                        R.plurals.backuprestore_restore_done,
                                                        n,
                                                        n,
                                                    ),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            },
                                            onFailure = {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    getString(R.string.backuprestore_restore_failed),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            },
                                        )
                                    }
                                }
                            },
                            // Round 2.17 Phase G.3 — "Restore from backup
                            // archive". Park a handler for the SAF picker
                            // launcher; on grant, wipe + extract + replace
                            // RepoStore + re-index. Dialog confirmed
                            // already.
                            onPickRestoreArchive = pick@{
                                val parentDir = graph.repoStoragePrefs.location
                                    ?.workingDir(filesDir)
                                if (parentDir == null) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.backuprestore_restore_failed),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    return@pick
                                }
                                pendingRestoreArchiveHandler = handler@{ srcUri ->
                                    kotlinx.coroutines.GlobalScope.launch(
                                        kotlinx.coroutines.Dispatchers.IO,
                                    ) {
                                        val result = runCatching {
                                            contentResolver.openInputStream(srcUri)?.use { input ->
                                                com.eight87.strictlykeptboy.backup
                                                    .BackupRestorer.restoreFromArchive(
                                                        input = input,
                                                        parent = parentDir,
                                                        deviceName = android.os.Build.MODEL ?: "",
                                                    )
                                            } ?: error("openInputStream returned null")
                                            val configs = com.eight87.strictlykeptboy.backup
                                                .BackupRestorer.rescanParent(parentDir)
                                            graph.repoStore.replaceAll(configs)
                                            reindexAfterRestore(graph, configs)
                                            configs.size
                                        }
                                        kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.Main,
                                        ) {
                                            result.fold(
                                                onSuccess = { n ->
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        resources.getQuantityString(
                                                            R.plurals.backuprestore_restore_done,
                                                            n,
                                                            n,
                                                        ),
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                },
                                                onFailure = { err ->
                                                    val msg =
                                                        if (err is com.eight87.strictlykeptboy.backup
                                                                .BackupRestorer.UnsafeWipeException
                                                        ) {
                                                            getString(R.string.backuprestore_restore_unsafe)
                                                        } else {
                                                            getString(R.string.backuprestore_restore_failed)
                                                        }
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        msg,
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                },
                                            )
                                        }
                                    }
                                }
                                // application/gzip is the canonical type;
                                // also allow */* so devices that surface
                                // the file as `application/x-gzip` /
                                // `octet-stream` can be picked.
                                restoreArchivePickerLauncher.launch(
                                    arrayOf("application/gzip", "application/x-gzip", "*/*"),
                                )
                            },
                            onSwitchToInternal = switch@{
                                val oldLoc = graph.repoStoragePrefs.location ?: return@switch
                                val oldDir = oldLoc.workingDir(filesDir)
                                val newDir = filesDir.resolve("strictlykeptboy")
                                moveJobRequest.value = MoveJobRequest(
                                    oldParent = oldDir,
                                    newParent = newDir,
                                    oldLocation = oldLoc,
                                    newLocation = com.eight87.strictlykeptboy.prefs.ParentLocation
                                        .Internal(absPath = newDir.absolutePath),
                                )
                            },
                            // Round 2.15 — demo-mode toggle access.
                            demoModePrefs = graph.demoModePrefs,
                            // Round 2.2.D — Settings completion.
                            reposFlow = graph.repoStore.state,
                            // Round 2.23 Phase E — Reviews live feed.
                            // Recomputed when the repo list changes; the
                            // reader walks each repo's reviews/<sha>/
                            // reviewable_change.md and parses frontmatter.
                            reviewItemsFlow = kotlinx.coroutines.flow.MutableStateFlow(
                                run {
                                    val roots = graph.repoStore.list().map {
                                        java.io.File(it.rootDir).toPath()
                                    }
                                    com.eight87.strictlykeptboy.ui.reviews.ReviewFeedReader
                                        .scan(roots)
                                },
                            ),
                            onOpenRepo = { cfg ->
                                // Surface per-repo settings via the existing Repos top-destination.
                                graph.setDefaultWriteRepoName(cfg.displayName)
                            },
                            accessAggregator = graph.accessAggregator,
                            onOpenShareFor = { /* hook for ShareSheet wiring */ },
                            autoTabletPrefs = graph.autoTabletPrefs,
                            tripFeed = graph.tripFeed,
                        ),
                        onWizardScaffold = { draft ->
                            runCatching {
                                // Round 2.17.C.2 — wizard scaffold now lands
                                // under the configured parent (Internal:
                                // filesDir/strictlykeptboy/, External: the
                                // SAF cachedRealPath) instead of the legacy
                                // filesDir/repos/. Falls back to the
                                // canonical internal default if the user
                                // hasn't confirmed a parent yet — Phase D
                                // adds the wizard storage step that makes
                                // this explicit; until then, the default
                                // matches what the v1 fallback would have
                                // produced anyway.
                                val parentDir = graph.repoStoragePrefs.location?.workingDir(filesDir)
                                    ?: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs.defaultInternalDir(this@MainActivity)
                                val outcome = WizardScaffolder.materialize(
                                    parentDir = parentDir,
                                    draft = draft,
                                    author = AuthorIdentity("me", "me@example.com"),
                                    assetPackLoader = graph.assetPackLoader,
                                )
                                val scaffoldedConfig = RepoConfig(
                                    repoId = outcome.repoId,
                                    displayName = draft.displayName.ifBlank { "my calendar" },
                                    rootDir = outcome.rootDir.absolutePath,
                                    remotes = emptyList(),
                                    primaryRemote = null,
                                    authorIdentity = outcome.authorIdentity,
                                    defaultCalendarId = outcome.calendarIds.values.firstOrNull(),
                                    defaultTodolistId = outcome.todolistId,
                                    iconEmoji = when (draft.species) {
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Bat -> "🦇"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Bunny -> "🐰"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Cat -> "🐱"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.CatChan -> "🐱"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Fox -> "🦊"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.FoxChan -> "🦊"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Lion -> "🦁"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Tiger -> "🐯"
                                        com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Wolf -> "🐺"
                                    },
                                    // Per D.88 / F48 — the species drives the per-repo avatar.
                                    // `Sticker(<species>)` falls back to about_bat for bat and
                                    // to AutoInitials for others until Phase WW lands.
                                    iconSpecies = draft.species.name,
                                )
                                // Round 2.17.C.4 — debug-only invariant.
                                com.eight87.strictlykeptboy.git.warnIfRepoOutsideParent(
                                    scaffoldedConfig,
                                    parentDir.absolutePath,
                                )
                                graph.repoStore.add(scaffoldedConfig)
                                graph.setDefaultWriteRepoName(draft.displayName.ifBlank { "my calendar" })
                                Unit
                            }
                        },
                        // Phase 2.1.I.4 — share-with-dom sheet host. Reuses
                        // the existing ShareSheet; the wizard CTA flips
                        // `pendingShareRepo` and we render here. The user
                        // sets allowWriteBack themselves in the sheet (the
                        // wizard advertises that's what we're doing).
                        // Phase CCC.8 — trip-overlay materializer. Writes a
                        // `cal-trip-<uuidv7>/` overlay into the active repo and
                        // commits atomically. Falls back to no-op (Result.failure)
                        // if no active repo exists yet.
                        // Round 2.22 / Phase B UI follow-up — drag-to-reschedule.
                        // Resolve the band's source entity from disk, then
                        // route through DragRescheduleController.
                        onSingleDrop = { band, newStart ->
                            val eventId = (band.instance.source as? com.eight87.strictlykeptboy.resolver.InstanceSource.OneOff)
                                ?.eventId?.id ?: return@SkbAppShell
                            val repoId = band.instance.repo.id
                            val cfg = graph.repoStore.list().firstOrNull { it.repoId == repoId }
                                ?: return@SkbAppShell
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val results = com.eight87.strictlykeptboy.store.RepoScanner
                                        .scanAll(java.io.File(cfg.rootDir))
                                    val event = results
                                        .filterIsInstance<com.eight87.strictlykeptboy.store.ParseResult.Success>()
                                        .mapNotNull { it.entity as? com.eight87.strictlykeptboy.store.Event }
                                        .firstOrNull { it.id == eventId } ?: return@runCatching
                                    com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController
                                        .handleSingleDrop(
                                            rootDir = java.io.File(cfg.rootDir),
                                            repoId = cfg.repoId,
                                            event = event,
                                            newStart = newStart,
                                            nowIso = java.time.OffsetDateTime.now().toString(),
                                        )
                                }
                            }
                        },
                        onRecurringDrop = { band, newStart, choice ->
                            val src = band.instance.source as? com.eight87.strictlykeptboy.resolver.InstanceSource.RuleInstance
                                ?: return@SkbAppShell
                            val ruleId = src.ruleId.id
                            val origDate = src.originalStart.toLocalDate()
                            val repoId = band.instance.repo.id
                            val cfg = graph.repoStore.list().firstOrNull { it.repoId == repoId }
                                ?: return@SkbAppShell
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val results = com.eight87.strictlykeptboy.store.RepoScanner
                                        .scanAll(java.io.File(cfg.rootDir))
                                    val rule = results
                                        .filterIsInstance<com.eight87.strictlykeptboy.store.ParseResult.Success>()
                                        .mapNotNull { it.entity as? com.eight87.strictlykeptboy.store.RecurrenceRule }
                                        .firstOrNull { it.id == ruleId } ?: return@runCatching
                                    com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController
                                        .handleRecurringDrop(
                                            rootDir = java.io.File(cfg.rootDir),
                                            repoId = cfg.repoId,
                                            rule = rule,
                                            originalDate = origDate,
                                            newStart = newStart,
                                            choice = choice,
                                            author = cfg.authorIdentity.name,
                                            nowIso = java.time.OffsetDateTime.now().toString(),
                                        )
                                }
                            }
                        },
                        onTripMaterialize = { tripDraft ->
                            runCatching {
                                val activeName = graph.defaultWriteRepoName.value
                                val cfg = graph.repoStore.list().firstOrNull { it.displayName == activeName }
                                    ?: graph.repoStore.list().firstOrNull()
                                if (cfg == null) {
                                    // SOLID Liskov fix #6 — soft-fail
                                    // instead of `error()`: route the
                                    // user into the lifestyle wizard
                                    // rather than crashing (or silently
                                    // swallowing inside runCatching).
                                    graph.setWizardEntryRequest(
                                        com.eight87.strictlykeptboy.ui.wizard.WizardScreen.Welcome,
                                    )
                                    return@runCatching
                                }
                                com.eight87.strictlykeptboy.ui.trip.TripScaffolder.materialize(
                                    repoRoot = java.io.File(cfg.rootDir),
                                    draft = tripDraft,
                                    assets = com.eight87.strictlykeptboy.ui.trip.TripScaffolder.AssetReader { path ->
                                        assets.open(path)
                                    },
                                    author = cfg.authorIdentity,
                                    repoId = cfg.repoId,
                                )
                                Unit
                            }
                        },
                    )
                    // Round 2.1.B.4 — overlay CalendarSettingsSheet on
                    // long-press of a chip. Save writes calendar.toml on
                    // Dispatchers.IO and commits via GitRepoRegistry.
                    pendingCalendarEdit?.let { meta ->
                        com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsSheet(
                            calendar = meta,
                            onDismiss = { pendingCalendarEdit = null },
                            onSave = { draft ->
                                scope.launch {
                                    runCatching {
                                        com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsWriter
                                            .write(graph.repoStore, draft)
                                    }
                                    pendingCalendarEdit = null
                                }
                            },
                        )
                    }
                    // Round 2.17 Phase E.4 — Adopt confirmation sheet host.
                    val adoptState by adoptSheetRequest.collectAsState()
                    adoptState?.let { req ->
                        com.eight87.strictlykeptboy.ui.settings.AdoptFolderSheet(
                            request = req,
                            reconcile = { graph.parentReconciler.reconcileExternal(req.parentDir) },
                            onCancel = { adoptSheetRequest.value = null },
                            onConfirm = { adoptees ->
                                scope.launch {
                                    runCatching {
                                        // Switch parent to the picked folder.
                                        if (!com.eight87.strictlykeptboy.prefs.SkbRootMarker
                                                .isSkbRoot(req.parentDir)
                                        ) {
                                            com.eight87.strictlykeptboy.prefs.SkbRootMarker.write(
                                                parent = req.parentDir,
                                                deviceName = android.os.Build.MODEL ?: "",
                                            )
                                        }
                                        graph.repoStoragePrefs.set(
                                            com.eight87.strictlykeptboy.prefs.ParentLocation.External(
                                                treeUri = req.treeUri,
                                                label = req.label,
                                                cachedRealPath = req.parentDir.absolutePath,
                                            ),
                                        )
                                        // Register adoptees.
                                        for (ad in adoptees) {
                                            if (graph.repoStore.get(ad.repoId) != null) continue
                                            graph.repoStore.add(
                                                RepoConfig(
                                                    repoId = ad.repoId,
                                                    displayName = ad.repoId,
                                                    rootDir = ad.rootDir.absolutePath,
                                                    remotes = emptyList(),
                                                    primaryRemote = null,
                                                    authorIdentity = AuthorIdentity("me", "me@example.com"),
                                                ),
                                            )
                                        }
                                    }
                                    Toast.makeText(
                                        this@MainActivity,
                                        resources.getQuantityString(R.plurals.adopt_sheet_done, adoptees.size, adoptees.size),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    adoptSheetRequest.value = null
                                }
                            },
                        )
                    }
                    // Round 2.17 Phase E.5 — Move-job dialog host.
                    val moveReq by moveJobRequest.collectAsState()
                    moveReq?.let { req ->
                        com.eight87.strictlykeptboy.ui.settings.RepoMoveJobDialog(
                            mover = graph.repoMover,
                            request = req,
                            onDone = { moveJobRequest.value = null },
                        )
                    }
                    // Phase 2.1.I.4 — overlay the ShareSheet when the
                    // wizard's Share-with-dom CTA fired. Lives as a sibling
                    // of SkbAppShell so it overlays everything else.
                    pendingShareRepo?.let { repo ->
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        com.eight87.strictlykeptboy.ui.share.ShareSheet(
                            repo = repo,
                            onDismiss = { pendingShareRepo = null },
                            onCopy = { link ->
                                val cm = ctx.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE,
                                ) as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("share link", link),
                                )
                            },
                            onSend = { link ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, link)
                                }
                                ctx.startActivity(Intent.createChooser(send, null))
                            },
                        )
                    }
                }
            }
            }
        }
        } finally {
            com.eight87.strictlykeptboy.perf.PerfTraceRecorder.end()
        }
    }

    /**
     * Phase O.2 — dispatch a [ShareLinkReceiver.Action] into concrete side effects.
     * Composition root only place that wires concrete classes (R.X.3).
     */
    private fun handleShareAction(
        action: com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.Action,
        repoStore: RepoStore,
    ) {
        when (action) {
            is com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.Action.Invalid -> {
                Toast.makeText(this, getString(R.string.share_invalid), Toast.LENGTH_LONG).show()
            }
            is com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.Action.Expired -> {
                Toast.makeText(
                    this,
                    getString(R.string.share_expired, action.link.expiryIso ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            }
            is com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.Action.CloneReadOnly -> {
                val link = action.link
                val repoId = com.eight87.strictlykeptboy.git.Uuid7.generate().toString()
                val rootDir = filesDir.resolve("shared-readonly/$repoId").apply { mkdirs() }
                val label = link.sourceLabel?.takeIf { it.isNotBlank() }
                    ?: link.urls.first().substringAfterLast('/').removeSuffix(".git")
                kotlinx.coroutines.GlobalScope.launch {
                    runCatching {
                        repoStore.add(
                            RepoConfig(
                                repoId = repoId,
                                displayName = label,
                                rootDir = rootDir.absolutePath,
                                remotes = emptyList(),
                                primaryRemote = null,
                                authorIdentity = AuthorIdentity("me", "me@example.com"),
                                readOnlyViaShare = true,
                                sourceRepoLabel = label,
                                sourceRepoBackLink = link.backLink,
                            ),
                        )
                    }
                }
                Toast.makeText(
                    this,
                    getString(R.string.share_received_read_only_badge),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            is com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.Action.LaunchAddRepo -> {
                Toast.makeText(
                    this,
                    action.link.urls.first(),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Phase P.4 — assemble an .ics serialisation by scanning the repo's
     * working tree, narrowing to event-shaped entities, and handing the
     * triple to [com.eight87.strictlykeptboy.port.ics.IcsExporter].
     */
    /**
     * Round 2.17 Phase F.3 — format a byte count for the export Toast.
     * Small, base-10 (matches what file managers display).
     */
    /**
     * Round 2.18.E.10 — true only when [data] is a Phase O share link
     * or Phase MM custom-scheme deep link. Calendar contract intents
     * (`content://com.android.calendar/...`, `file://...ics`,
     * `https://example.com/foo.ics`) MUST fall through to the
     * Calendar router without being misclassified by the share
     * receiver.
     */
    private fun isShareLinkScheme(data: String): Boolean {
        return data.startsWith("strictlykeptboy://") ||
            data.startsWith("https://strictlykeptboy.app/link/") ||
            data.startsWith("http://strictlykeptboy.app/link/")
    }

    private fun humanBytes(n: Long): String {
        if (n < 1024) return "$n B"
        val units = listOf("KB", "MB", "GB", "TB")
        var v = n.toDouble() / 1024.0
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) { v /= 1024.0; i++ }
        return String.format(java.util.Locale.ROOT, "%.1f %s", v, units[i])
    }

    /**
     * Round 2.17 Phase G.6 — invalidate the cache DB + full re-index
     * the restored repos. Order matters: this MUST run AFTER the
     * on-disk wipe/extract AND `RepoStore.replaceAll`, so the new
     * configs are authoritative and we don't index a stale set.
     *
     * Implementation: clear all Room tables, drop the GitRepo handle
     * cache (rootDirs may have changed), then `fullScan` each repo —
     * which records a fresh `RepoStateRow` head SHA + schema version.
     */
    private suspend fun reindexAfterRestore(graph: AppGraph, configs: List<RepoConfig>) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { graph.cacheDatabase.clearAllTables() }
            GitRepoRegistry.clear()
            val indexer = com.eight87.strictlykeptboy.cache.Indexer(graph.cacheDatabase)
            for (cfg in configs) {
                runCatching {
                    val gitRepo = GitRepo.open(
                        rootDir = File(cfg.rootDir),
                        repoId = cfg.repoId,
                        remotes = cfg.remotes,
                        primaryRemote = cfg.primaryRemote,
                        authorIdentity = cfg.authorIdentity,
                        defaultBranch = cfg.defaultBranch,
                    ).also(GitRepoRegistry::put)
                    indexer.fullScan(cfg.repoId, gitRepo)
                }
            }
        }
    }

    /**
     * Round 2.27 / Phase D.3 — writes a keeper-prompt response file via
     * [com.eight87.strictlykeptboy.store.PromptResponseWriter]. Resolves
     * the repo root from `task.todolist.repoId` and the responder id
     * from the active repo's author identity. After the write lands,
     * triggers an incremental rescan on the affected repo so the
     * FromEventsProjector's response-reader picks up the new file on
     * the next tick.
     */
    private suspend fun writePromptResponse(
        task: com.eight87.strictlykeptboy.ui.tasks.TaskItem,
        body: String,
        attachment: String?,
    ) {
        if (task.promptCalendarId.isBlank() || task.promptRuleId.isBlank()) return
        val graph = pendingAppGraph ?: return
        val due = task.due ?: java.time.LocalDate.now()
        val repos = graph.repoStore.list()
        val cfg = repos.firstOrNull { it.repoId == task.todolist.repoId }
            ?: return
        val responderId = cfg.authorIdentity.name.ifBlank { "boy" }
        com.eight87.strictlykeptboy.store.PromptResponseWriter.write(
            repoRoot = java.nio.file.Paths.get(cfg.rootDir),
            calId = task.promptCalendarId,
            ruleId = task.promptRuleId,
            instanceDate = due,
            responderId = responderId,
            body = body,
            attachment = attachment,
        )
        // Re-index so the response file is visible to downstream readers
        // on the next projector evaluation.
        runCatching {
            val gitRepo = com.eight87.strictlykeptboy.git.GitRepoRegistry.get(cfg.repoId)
            if (gitRepo != null) {
                com.eight87.strictlykeptboy.cache.Indexer(graph.cacheDatabase)
                    .fullScan(cfg.repoId, gitRepo)
            }
        }
    }

    private suspend fun buildExportContent(repo: RepoConfig): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val results = com.eight87.strictlykeptboy.store.RepoScanner.scanAll(File(repo.rootDir))
            val entities = results
                .filterIsInstance<com.eight87.strictlykeptboy.store.ParseResult.Success>()
                .map { it.entity }
            val events = entities.filterIsInstance<com.eight87.strictlykeptboy.store.Event>()
            val rules = entities.filterIsInstance<com.eight87.strictlykeptboy.store.RecurrenceRule>()
            val exceptions = entities.filterIsInstance<com.eight87.strictlykeptboy.store.Exception>()
            com.eight87.strictlykeptboy.port.ics.IcsExporter.export(events, rules, exceptions)
        }
}
