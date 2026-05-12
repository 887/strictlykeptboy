package com.eight87.strictlykeptboy

import android.os.Bundle
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
import com.eight87.strictlykeptboy.ui.wizard.AgeGatePrefs
import com.eight87.strictlykeptboy.ui.wizard.AgeGateScreen
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs
import com.eight87.strictlykeptboy.ui.wizard.WizardScaffolder
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

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
                    AppScaffold(
                        activeRepoNameFlow = activeRepoName,
                        scheduleState = scheduleState,
                        onPersistTab = viewModePrefs::set,
                        reposState = reposState,
                        secretsStore = secretsStore,
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
}
