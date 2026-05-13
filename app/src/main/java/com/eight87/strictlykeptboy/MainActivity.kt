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
                if (!ageOk) {
                    AgeGateScreen(
                        onAccept = {
                            graph.ageGatePrefs.confirm()
                            ageOk = true
                        },
                        onDecline = { finish() },
                    )
                } else if (!firstLaunchDone) {
                    // Phase 2.1.I.1 — first-launch wizard. The shell is not
                    // mounted yet, so there's no empty-Schedule flash. Once
                    // the user finishes (or cancels with at-least-one repo
                    // present), we flip `firstLaunchDone = true` and the
                    // next composition mounts the shell.
                    com.eight87.strictlykeptboy.ui.wizard.WizardNavHost(
                        onFinish = { firstLaunchDone = true },
                        onCancel = { firstLaunchDone = true },
                        onScaffold = { draft ->
                            runCatching {
                                val outcome = WizardScaffolder.materialize(
                                    parentDir = filesDir.resolve("repos"),
                                    draft = draft,
                                    author = AuthorIdentity("me", "me@example.com"),
                                )
                                graph.repoStore.add(
                                    RepoConfig(
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
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Fox -> "🦊"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Lion -> "🦁"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Tiger -> "🐯"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Wolf -> "🐺"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.ChooseYourOwn -> null
                                        },
                                        iconSpecies = draft.species.name.takeIf {
                                            draft.species != com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.ChooseYourOwn
                                        },
                                    ),
                                )
                                graph.activeRepoName.value = draft.displayName.ifBlank { "my calendar" }
                                Unit
                            }
                        },
                        neutralMode = graph.neutralModePrefs.isEnabled(),
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
                    val tasksViewState = remember { TasksViewState() }
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
                            tasksViewState.set(
                                cur.copy(
                                    tasks = nonFromEvents + fromEvents,
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
                                val outcome = WizardScaffolder.materialize(
                                    parentDir = filesDir.resolve("repos"),
                                    draft = draft,
                                    author = AuthorIdentity("me", "me@example.com"),
                                )
                                graph.repoStore.add(
                                    RepoConfig(
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
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Fox -> "🦊"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Lion -> "🦁"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Tiger -> "🐯"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.Wolf -> "🐺"
                                            com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.ChooseYourOwn -> null
                                        },
                                        // Per D.88 / F48 — the species drives the per-repo avatar.
                                        // `Sticker(<species>)` falls back to about_bat for bat and
                                        // to AutoInitials for others until Phase WW lands.
                                        iconSpecies = draft.species.name.takeIf {
                                            draft.species != com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice.ChooseYourOwn
                                        },
                                    ),
                                )
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
