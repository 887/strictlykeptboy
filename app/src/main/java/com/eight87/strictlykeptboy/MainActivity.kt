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
    private var pendingImportRepo: RepoConfig? = null
    private var pendingExportRepo: RepoConfig? = null
    private var pendingExportContent: String? = null
    private var onParsed: ((com.eight87.strictlykeptboy.port.ics.IcsParseReport) -> Unit)? = null

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
        graph.repoStoragePrefs.set(
            com.eight87.strictlykeptboy.prefs.ParentLocation.External(
                treeUri = uri.toString(),
                label = label,
                cachedRealPath = parentFile.absolutePath,
            ),
        )
        // B.4 — reconcile against the new parent and Toast how many
        // repos got adopted (instead of "applied backup mirror to N").
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val count = runCatching { graph.parentReconciler.reconcile().size }.getOrDefault(0)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.parent_adopted_n_repos, count),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
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
    }

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
        graph.parentPickerHandle = { parentPickerLauncher.launch(null) }

        // Phase O.2 — handle strictlykeptboy://share deep links.
        deepLinkHandler = { intent ->
            intent.dataString?.let { data ->
                val action = com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.classify(data)
                handleShareAction(action, graph.repoStore)
            }
        }
        // Cold-start: process the launching intent immediately.
        intent?.dataString?.let { data ->
            val action = com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.classify(data)
            handleShareAction(action, graph.repoStore)
        }

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
                            getString(R.string.import_done, report.totalEntities),
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
                if (!firstLaunchDone) {
                    // Round 2.15 — demo-first onboarding. The intro wizard
                    // is two screens: manifesto + perspective picker. On
                    // pick we seed a read-only demo repo and drop the user
                    // straight into the app.
                    com.eight87.strictlykeptboy.ui.wizard.intro.IntroWizardHost(
                        onPerspectiveChosen = { card ->
                            scope.launch {
                                val outcome = com.eight87.strictlykeptboy.demo.DemoRepoSeeder.seed(
                                    parentDir = filesDir.resolve("demo-repos")
                                        .resolve(com.eight87.strictlykeptboy.demo.DemoRepoSeeder.folderName(card)),
                                    perspective = card,
                                    author = AuthorIdentity("demo", "demo@strictlykeptboy.local"),
                                    assetPackLoader = graph.assetPackLoader,
                                )
                                val displayName = "demo · ${card.name.lowercase()}"
                                graph.repoStore.add(
                                    RepoConfig(
                                        repoId = outcome.repoId,
                                        displayName = displayName,
                                        rootDir = outcome.rootDir.absolutePath,
                                        remotes = emptyList(),
                                        primaryRemote = null,
                                        authorIdentity = outcome.authorIdentity,
                                        defaultCalendarId = outcome.calendarIds.values.firstOrNull(),
                                        defaultTodolistId = outcome.todolistId,
                                        iconEmoji = "🦇",
                                        iconSpecies = "Bat",
                                        isDemo = true,
                                    ),
                                )
                                graph.demoModePrefs.setPerspective(card)
                                graph.activeRepoName.value = displayName
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
                            val fromEvents =
                                com.eight87.strictlykeptboy.ui.tasks.FromEventsProjector.project(
                                    instances = graph.todayEventSource.eventsForToday().map { it.instance },
                                    calendarsById = snap.calendars.associateBy { it.ref.id },
                                )
                            // Merge: keep non-FromEvents tasks the caller
                            // pushed in via `set/addTask`; replace the
                            // FromEvents slice with the freshly projected
                            // set. This is the producer the brief noted is
                            // missing for the `TaskSource.FromEvents` enum.
                            val nonFromEvents = cur.tasks.filter {
                                it.source != com.eight87.strictlykeptboy.ui.tasks.TaskSource.FromEvents
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
                            tasksViewState.set(
                                cur.copy(
                                    tasks = withDemo + fromEvents,
                                    activeTodolistIds = ids,
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
                        activeRepoNameFlow = graph.defaultWriteRepoName,
                        activeIconKindFlow = graph.activeRepoIconKind,
                        wizardEntryRequest = graph.wizardEntryRequest,
                        calendarVisibility = graph.calendarVisibility,
                        onLongPressCalendar = { meta -> pendingCalendarEdit = meta },
                        onShareWithDom = {
                            val name = graph.activeRepoName.value
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
                                graph.wizardEntryRequest.value =
                                    com.eight87.strictlykeptboy.ui.wizard.WizardScreen.Roles
                            },
                            onOpenPrivacyPolicy = {
                                val url = "https://github.com/887/strictlykeptboy/blob/main/docs/privacy-policy.md"
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            // Round 2.7.B.4-UI — backup folder picker access.
                            repoStoragePrefs = graph.repoStoragePrefs,
                            onPickBackupFolder = {
                                graph.parentPickerHandle?.invoke()
                            },
                            onRemoveBackupFolder = {
                                // Round 2.17.A — "Remove backup" now means
                                // "switch parent back to internal". Phase E
                                // replaces this surface with the proper
                                // "Switch to internal" flow + move-job; for
                                // now we just flip the prefs so the build
                                // is green.
                                graph.repoStoragePrefs.set(
                                    com.eight87.strictlykeptboy.prefs.ParentLocation.Internal(
                                        absPath = filesDir.resolve("strictlykeptboy").absolutePath,
                                    ),
                                )
                            },
                            // Round 2.15 — demo-mode toggle access.
                            demoModePrefs = graph.demoModePrefs,
                            // Round 2.2.D — Settings completion.
                            reposFlow = graph.repoStore.state,
                            onOpenRepo = { cfg ->
                                // Surface per-repo settings via the existing Repos top-destination.
                                graph.defaultWriteRepoName.value = cfg.displayName
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
                                graph.defaultWriteRepoName.value = draft.displayName.ifBlank { "my calendar" }
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
                        onTripMaterialize = { tripDraft ->
                            runCatching {
                                val activeName = graph.defaultWriteRepoName.value
                                val cfg = graph.repoStore.list().firstOrNull { it.displayName == activeName }
                                    ?: graph.repoStore.list().firstOrNull()
                                    ?: error("no active repo — run the lifestyle wizard first")
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
                                            .write(graph, draft)
                                    }
                                    pendingCalendarEdit = null
                                }
                            },
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
