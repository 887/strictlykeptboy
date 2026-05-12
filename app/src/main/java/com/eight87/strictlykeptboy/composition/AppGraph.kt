package com.eight87.strictlykeptboy.composition

import android.content.Context
import com.eight87.strictlykeptboy.auto.CarAppRuntime
import com.eight87.strictlykeptboy.auto.TodayEventSource
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.notif.SyncEventNotificationBridge
import com.eight87.strictlykeptboy.resolver.CommonTimeFinder
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.sync.SyncRuntime
import com.eight87.strictlykeptboy.sync.SyncScheduler
import com.eight87.strictlykeptboy.sync.SyncStatusStore
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewModePrefs
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import com.eight87.strictlykeptboy.ui.settings.IdentityPrefs
import com.eight87.strictlykeptboy.ui.settings.ListKind
import com.eight87.strictlykeptboy.ui.settings.ModePrefs
import com.eight87.strictlykeptboy.ui.settings.SyncSettingsPrefs
import com.eight87.strictlykeptboy.ui.together.BusySource
import com.eight87.strictlykeptboy.ui.together.CommonTimeFinderPort
import com.eight87.strictlykeptboy.ui.together.TogetherRepoOption
import com.eight87.strictlykeptboy.ui.wizard.AgeGatePrefs
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Phase Q (R.X.3 / F22) — the composition root.
 *
 * Earlier phases inlined every concrete-class instantiation in
 * `MainActivity.onCreate`. Once `MainActivity` crossed the F22
 * threshold (~310 LOC + still growing per Phase Q wiring), per the
 * standing CLAUDE.md rule we extracted this `AppGraph`.
 *
 * Responsibilities:
 * - One place where concrete classes meet (`RepoStore.open`,
 *   `SyncScheduler(...)`, `CommonTimeFinder(...)`, etc.).
 * - Hand out narrow interfaces to consumers — composables, ViewModels,
 *   the [com.eight87.strictlykeptboy.auto.SkbCarAppService] — so they
 *   don't import the concrete classes themselves (R.X.1 / R.X.7).
 * - Park process-wide handles where decoupled subsystems can find them
 *   (the [SyncRuntime] pattern + the new [CarAppRuntime]).
 *
 * Single instance per process; safe to recreate inside `MainActivity`
 * because every member is idempotent (`RepoStore.open` returns a
 * singleton, `SyncRuntime` already debounces overwrite).
 */
class AppGraph(private val appContext: Context) {

    /** Phase F — appearance prefs (theme mode + density + dynamic color). */
    val appearancePrefs: AppearancePrefs by lazy { AppearancePrefs.open(appContext) }

    /** Phase G — schedule view mode (week / day / month / agenda / todolist). */
    val viewModePrefs: ScheduleViewModePrefs by lazy { ScheduleViewModePrefs.open(appContext) }

    /** Phase I — encrypted repo configs. */
    val repoStore: RepoStore by lazy { RepoStore.open(appContext) }

    /** Phase I — per-(repoId, remoteName) credential vault. */
    val secretsStore: SecretsStore by lazy { SecretsStore.open(appContext) }

    /** Phase I — Compose-side repo list state, including draft form. */
    val reposState: ReposViewState by lazy { ReposViewState.open(appContext, repoStore) }

    /** Phase K.14 — age gate. */
    val ageGatePrefs: AgeGatePrefs by lazy { AgeGatePrefs.open(appContext) }

    /** Phase K.14 — neutral-mode toggle (D.58). */
    val neutralModePrefs: NeutralModePrefs by lazy { NeutralModePrefs.open(appContext) }

    /** Phase J — sync status (Idle / Running / Error / Conflicted). */
    val statusStore: SyncStatusStore by lazy { SyncStatusStore.open(appContext) }

    /** Phase S.3 — global sync settings. */
    val syncSettingsPrefs: SyncSettingsPrefs by lazy { SyncSettingsPrefs.open(appContext) }

    /** Phase S.4 — notification prefs (per-channel + briefings master + per-category lead times). */
    val notificationPrefs: NotificationPrefs by lazy { NotificationPrefs.open(appContext) }

    /** Phase S.5 — calendar visibility + priority. */
    val calendarVisibility: CalendarVisibilityPrefs by lazy {
        CalendarVisibilityPrefs.open(appContext, ListKind.Calendars)
    }

    /** Phase S.6 — todolist visibility + priority. */
    val todolistVisibility: CalendarVisibilityPrefs by lazy {
        CalendarVisibilityPrefs.open(appContext, ListKind.Todolists)
    }

    /** Phase S.8b — Identity (HV-R.3 / DDD.9). */
    val identityPrefs: IdentityPrefs by lazy { IdentityPrefs.open(appContext) }

    /** Phase S.11 — Mode (HV-Q.1 / DDD.1 / D.86). */
    val modePrefs: ModePrefs by lazy { ModePrefs.open(appContext) }

    /** Phase J — per-process sync scheduler. */
    val scheduler: SyncScheduler by lazy {
        SyncScheduler(
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
    }

    // ----------------------------------------------------------------
    // Phase F→G integration is still pending: live RepoStore → Room
    // bridge has not landed. Until then, the schedule pane and the
    // Auto surface both observe these empty flows. When the bridge
    // lands, only this section changes.
    // ----------------------------------------------------------------

    /** Mutable so wizard scaffolding can flip the displayed active repo. */
    val activeRepoName: MutableStateFlow<String> = MutableStateFlow("demo-repo")

    val snapshot: MutableStateFlow<RepoSnapshot> =
        MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))

    val sources: MutableStateFlow<Renderer.Sources> = MutableStateFlow(
        Renderer.Sources(
            events = emptyList(),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        ),
    )

    /** Phase N — Together repo options (id + label) derived from RepoStore. */
    @Suppress("OPT_IN_USAGE")
    val togetherRepoOptions: StateFlow<List<TogetherRepoOption>> by lazy {
        repoStore.state
            .map { list -> list.map { TogetherRepoOption(it.repoId, it.displayName) } }
            .stateIn(
                GlobalScope,
                SharingStarted.Eagerly,
                repoStore.state.value.map { TogetherRepoOption(it.repoId, it.displayName) },
            )
    }

    /** Phase N — busy-source. Empty until the Room indexer bridge lands. */
    val emptyBusySource: BusySource = BusySource { _, _, _, _ -> emptyMap() }

    /** Phase N — concrete finder + ISP-narrow port adapter. */
    val finderPort: CommonTimeFinderPort by lazy {
        val finder = CommonTimeFinder()
        CommonTimeFinderPort { q -> finder.find(q) }
    }

    /**
     * Phase Q.2 — ISP-narrow data source for the Auto today list (R.X.1).
     *
     * Returns events that fall inside today (system tz) by running the
     * [Renderer] against the current [snapshot] + [sources]. Today the
     * inputs are empty stubs, so this returns an empty list; once the
     * Room → snapshot bridge ships, the Auto surface lights up with the
     * same data the phone schedule sees, no extra wiring needed.
     */
    val todayEventSource: TodayEventSource by lazy {
        TodayEventSource { renderTodaySync() }
    }

    private fun renderTodaySync(): List<MaterializedInstance> {
        // Read-once snapshot of the inputs. The Renderer is async, but
        // for v1 the empty-stub path is synchronous; once live data
        // lands we can switch to a suspending overload + caching.
        val src = sources.value
        // One-off-only fast path: full Renderer wiring stays available
        // through `AppGraph.snapshot/sources`, but for read-only Auto
        // we only need today's instances, not the lane / overlay /
        // off-schedule annotations. Materializing one-offs that start
        // today is enough until live data + recurrence flow through.
        val today = java.time.LocalDate.now()
        return src.events
            .asSequence()
            .filter { evt ->
                val date = evt.start.toLocalDate()
                !date.isBefore(today) && !date.isAfter(today)
            }
            .map { evt ->
                MaterializedInstance(
                    source = com.eight87.strictlykeptboy.resolver.InstanceSource.OneOff(evt.ref),
                    calendar = evt.calendar,
                    repo = evt.repo,
                    originalStart = evt.start,
                    originalEnd = evt.end,
                    effectiveStart = evt.start,
                    effectiveEnd = evt.end,
                    title = evt.title,
                    body = evt.body,
                    emoji = evt.emoji,
                    isAllDay = evt.isAllDay,
                )
            }
            .toList()
    }

    /**
     * Phase J.1 — install the sync→notification bridge.
     *
     * Idempotent at the bridge level; called once at process start.
     */
    fun installSyncEventBridge() {
        SyncEventNotificationBridge.install(appContext, scheduler.eventsFlow)
    }

    /**
     * Park process-wide handles so decoupled services
     * ([com.eight87.strictlykeptboy.sync.SyncService],
     * [com.eight87.strictlykeptboy.auto.SkbCarAppService]) can locate
     * the singletons created here without a DI framework.
     */
    fun parkRuntimes() {
        scheduler.startPeriodicTicks()
        SyncRuntime.scheduler = scheduler
        SyncRuntime.statusStore = statusStore
        CarAppRuntime.todayEventSource = todayEventSource
    }

    /**
     * Optional helper for tests / future scopes: composition root
     * exposes a coroutine scope hook. v1 uses [GlobalScope] inline; this
     * stub lets a future refactor inject a SupervisorJob without
     * changing call sites.
     */
    val appScope: CoroutineScope get() = GlobalScope
}
