package com.eight87.strictlykeptboy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
            StrictlyKeptBoyTheme(
                themeMode = appearance.themeMode,
                densityScale = appearance.densityScale,
                dynamicColor = appearance.dynamicColor,
            ) {
                val scope = rememberCoroutineScope()
                if (!ageOk) {
                    AgeGateScreen(
                        onAccept = {
                            graph.ageGatePrefs.confirm()
                            ageOk = true
                        },
                        onDecline = { finish() },
                    )
                } else {
                    val scheduleState = remember {
                        ScheduleViewState(
                            scope = scope,
                            snapshotFlow = graph.snapshot,
                            sourcesFlow = graph.sources,
                            initialTab = graph.viewModePrefs.selected.value,
                        )
                    }
                    val togetherVm = remember(scope) {
                        TogetherViewModel(
                            scope = scope,
                            repoOptionsFlow = graph.togetherRepoOptions,
                            busySource = graph.emptyBusySource,
                            finder = graph.finderPort,
                        )
                    }
                    SkbAppShell(
                        activeRepoNameFlow = graph.activeRepoName,
                        activeIconKindFlow = graph.activeRepoIconKind,
                        scheduleState = scheduleState,
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
                            identityPrefs = graph.identityPrefs,
                            appearancePrefs = graph.appearancePrefs,
                            neutralPrefs = graph.neutralModePrefs,
                            modePrefs = graph.modePrefs,
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
                            onOpenPrivacyPolicy = {
                                val url = "https://github.com/887/strictlykeptboy/blob/main/docs/privacy-policy.md"
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
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
                                graph.activeRepoName.value = draft.displayName.ifBlank { "my calendar" }
                                Unit
                            }
                        },
                    )
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
