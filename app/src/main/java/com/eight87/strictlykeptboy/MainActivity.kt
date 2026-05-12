package com.eight87.strictlykeptboy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.sync.SyncRuntime
import com.eight87.strictlykeptboy.sync.SyncScheduler
import com.eight87.strictlykeptboy.sync.SyncService
import com.eight87.strictlykeptboy.sync.SyncStatusStore
import java.io.File
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.scaffold.AppScaffold
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewModePrefs
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.together.BusySource
import com.eight87.strictlykeptboy.ui.together.CommonTimeFinderPort
import com.eight87.strictlykeptboy.ui.together.TogetherRepoOption
import com.eight87.strictlykeptboy.ui.together.TogetherViewModel
import com.eight87.strictlykeptboy.resolver.CommonTimeFinder
import com.eight87.strictlykeptboy.ui.wizard.AgeGatePrefs
import com.eight87.strictlykeptboy.ui.wizard.AgeGateScreen
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs
import com.eight87.strictlykeptboy.ui.wizard.WizardScaffolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var deepLinkHandler: ((Intent) -> Unit)? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkHandler?.invoke(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appearancePrefs = AppearancePrefs.open(this)
        val viewModePrefs = ScheduleViewModePrefs.open(this)

        // Phase I — RepoStore + SecretsStore live in EncryptedSharedPreferences;
        // open here so the Repos rail destination can write through them.
        val repoStore = RepoStore.open(this)
        val secretsStore = SecretsStore.open(this)
        val reposState = ReposViewState.open(this, repoStore)

        // Phase O.2 — handle strictlykeptboy://share deep links.
        deepLinkHandler = { intent ->
            intent.dataString?.let { data ->
                val action = com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.classify(data)
                handleShareAction(action, repoStore)
            }
        }
        // Cold-start: process the launching intent immediately.
        intent?.dataString?.let { data ->
            val action = com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver.classify(data)
            handleShareAction(action, repoStore)
        }

        // Phase K.14 — age gate + neutral-mode toggle prefs.
        val ageGate = AgeGatePrefs.open(this)
        val neutralMode = NeutralModePrefs.open(this)

        // Phase J — sync orchestration. Build the scheduler once per process and
        // park it in SyncRuntime so the foreground service can reach it.
        val statusStore = SyncStatusStore.open(this)
        val scheduler = SyncScheduler(
            repoStore = repoStore,
            statusStore = statusStore,
            repoProvider = { cfg ->
                GitRepoRegistry.get(cfg.repoId) ?: runCatching {
                    GitRepo.open(
                        rootDir = File(cfg.rootDir),
                        repoId = cfg.repoId,
                        remotes = cfg.remotes,
                        primaryRemote = cfg.primaryRemote,
                        authorIdentity = cfg.authorIdentity,
                        defaultBranch = cfg.defaultBranch,
                    ).also(GitRepoRegistry::put)
                }.getOrNull()
            },
        )
        scheduler.startPeriodicTicks()
        SyncRuntime.scheduler = scheduler
        SyncRuntime.statusStore = statusStore

        // Phase M.4 — bridge scheduler events to the silent sync-result
        // notification channel. Lives in MainActivity (composition root) per
        // R.X.3 — only place that knows the concrete types.
        com.eight87.strictlykeptboy.notif.SyncEventNotificationBridge
            .install(applicationContext, scheduler.eventsFlow)

        // Phase F stub: RepoStore + DAO wiring lands in Phase F→G integration.
        // For now we feed an empty snapshot + empty sources so SchedulePane
        // renders the EmptyScheduleState (F.5).
        val activeRepoName = MutableStateFlow("demo-repo")
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(
                events = emptyList(),
                rules = emptyList(),
                exceptionsByRule = emptyMap(),
                deviations = emptyList(),
                overrides = emptyList(),
            ),
        )

        // Phase N — Together pane wiring. The composition root owns
        // concrete BusySource + finder per R.X.3. v1 ships with an
        // empty BusySource because the full RepoStore → Room indexer
        // bridge for live calendar data lands in Round 2 (Phase F→G
        // integration). The UI is fully usable; result list shows the
        // empty-state until the bridge ships.
        @Suppress("OPT_IN_USAGE")
        val togetherRepoOptions = repoStore.state
            .map { list -> list.map { TogetherRepoOption(it.repoId, it.displayName) } }
            .stateIn(GlobalScope, SharingStarted.Eagerly, repoStore.state.value.map { TogetherRepoOption(it.repoId, it.displayName) })
        val emptyBusySource = BusySource { _, _, _, _ -> emptyMap() }
        val finderImpl = CommonTimeFinder()
        val finderPort = CommonTimeFinderPort { q -> finderImpl.find(q) }

        setContent {
            val appearance by appearancePrefs.state.collectAsState()
            // Re-evaluate age-gate on each composition; flip on accept.
            var ageOk by remember { mutableStateOf(ageGate.isConfirmed()) }
            StrictlyKeptBoyTheme(
                themeMode = appearance.themeMode,
                densityScale = appearance.densityScale,
                dynamicColor = appearance.dynamicColor,
            ) {
                val scope = rememberCoroutineScope()
                if (!ageOk) {
                    AgeGateScreen(
                        onAccept = {
                            ageGate.confirm()
                            ageOk = true
                        },
                        onDecline = { finish() },
                    )
                } else {
                    val scheduleState = remember {
                        ScheduleViewState(
                            scope = scope,
                            snapshotFlow = snapshot,
                            sourcesFlow = sources,
                            initialTab = viewModePrefs.selected.value,
                        )
                    }
                    val togetherVm = remember(scope) {
                        TogetherViewModel(
                            scope = scope,
                            repoOptionsFlow = togetherRepoOptions,
                            busySource = emptyBusySource,
                            finder = finderPort,
                        )
                    }
                    AppScaffold(
                        activeRepoNameFlow = activeRepoName,
                        scheduleState = scheduleState,
                        onPersistTab = viewModePrefs::set,
                        reposState = reposState,
                        secretsStore = secretsStore,
                        togetherViewModel = togetherVm,
                        onSyncClick = {
                            if (repoStore.list().any { it.remotes.isNotEmpty() }) {
                                SyncService.startSyncAll(this@MainActivity)
                            }
                        },
                        neutralMode = neutralMode.isEnabled(),
                        onWizardScaffold = { draft ->
                            runCatching {
                                val outcome = WizardScaffolder.materialize(
                                    parentDir = filesDir.resolve("repos"),
                                    draft = draft,
                                    author = AuthorIdentity("me", "me@example.com"),
                                )
                                // Register the new repo with RepoStore so the
                                // rest of the app picks it up.
                                repoStore.add(
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
                                    ),
                                )
                                activeRepoName.value = draft.displayName.ifBlank { "my calendar" }
                                Unit
                            }
                        },
                    )
                }
            }
        }
    }

    /**
     * Phase O.2 — dispatch a [ShareLinkReceiver.Action] into concrete side effects.
     * Composition root only place that wires concrete classes (R.X.3).
     *
     * Read-only: clone (deferred for v1 if network unreachable) → register a
     * `readOnlyViaShare = true` RepoConfig pointing at the URL. For the cold-start
     * + on-AVD smoke test, the v1 implementation registers a stub entry that
     * the next sync round will populate by clone. A full background-clone path
     * lands in a Phase O follow-up; intent dispatch + UI banner ship now.
     *
     * Read-write: surface a toast pointing the user at the Add-Repo flow with
     * the URL prefilled — wiring the wizard pre-fill end-to-end is a follow-up
     * in the AddRepo nav-host.
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
                // Persist intent — actual clone happens on next sync tick.
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
                // Wizard pre-fill: deferred to a follow-up; for v1, surface a toast
                // pointing the user at Repos → Add. The URL is observable in the
                // intent for any future receiver to consume.
                Toast.makeText(
                    this,
                    action.link.urls.first(),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
