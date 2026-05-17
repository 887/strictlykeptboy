package com.eight87.strictlykeptboy.composition

import android.content.Context
import com.eight87.strictlykeptboy.auto.AutoEvent
import com.eight87.strictlykeptboy.auto.CarAppRuntime
import com.eight87.strictlykeptboy.auto.TodayEventSource
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.notif.BriefingRuntime
import com.eight87.strictlykeptboy.notif.BriefingSource
import com.eight87.strictlykeptboy.notif.NowNextNotificationProvider
import com.eight87.strictlykeptboy.notif.SyncEventNotificationBridge
import com.eight87.strictlykeptboy.resolver.CommonTimeFinder
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.NowNextResolver
import com.eight87.strictlykeptboy.resolver.NowNextSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.sync.SyncRuntime
import com.eight87.strictlykeptboy.sync.SyncScheduler
import com.eight87.strictlykeptboy.sync.SyncStatusStore
import com.eight87.strictlykeptboy.ui.theming.toIconKind
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.prefs.ParentLocation
import com.eight87.strictlykeptboy.prefs.ParentLocationMigrator
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs
import com.eight87.strictlykeptboy.sync.ParentReconciler
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
import com.eight87.strictlykeptboy.ui.together.TogetherCalendarOption
import com.eight87.strictlykeptboy.ui.together.TogetherRepoOption
import com.eight87.strictlykeptboy.ui.wizard.AgeGatePrefs
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    /** Phase FFF / EC-A.3 — last-used event-create tab. */
    val eventCreatePrefs: com.eight87.strictlykeptboy.ui.schedule.EventCreatePrefs by lazy {
        com.eight87.strictlykeptboy.ui.schedule.EventCreatePrefs.open(appContext)
    }

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

    /**
     * [M] #8d (audit pass 2026-05-17) — avatar pack loaders + resolvers
     * + bitmap cache, extracted into a cohesive sub-graph. Consumers
     * reach the underlying fields via `graph.avatarGraph.<field>`.
     */
    val avatarGraph: AvatarGraph by lazy { AvatarGraph(appContext) }

    /** Phase J — sync status (Idle / Running / Error / Conflicted). */
    val statusStore: SyncStatusStore by lazy { SyncStatusStore.open(appContext) }

    /** Phase S.3 — global sync settings. */
    val syncSettingsPrefs: SyncSettingsPrefs by lazy { SyncSettingsPrefs.open(appContext) }

    /** Round 2.2.D.7 — Android Auto + tablet master-detail prefs. */
    val autoTabletPrefs: com.eight87.strictlykeptboy.ui.settings.AutoTabletPrefs by lazy {
        com.eight87.strictlykeptboy.ui.settings.AutoTabletPrefs.open(appContext)
    }

    /** Round 2.2.D.6 — Access aggregator (read-only across configured repos). */
    val accessAggregator: com.eight87.strictlykeptboy.store.AccessAggregator by lazy {
        com.eight87.strictlykeptboy.store.AccessAggregator(repoStore)
    }

    /** Round 2.2.D.13 — Trip-summary feed; empty default until Phase CCC wires the real resolver. */
    val tripFeed: com.eight87.strictlykeptboy.ui.trip.TripFeed by lazy {
        com.eight87.strictlykeptboy.ui.trip.InMemoryTripFeed()
    }

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

    /**
     * Phase 2.1.J.1 — bind [IdentityPrefs] to the active repo so Settings
     * edits round-trip to `<rootDir>/identity.toml` and commit. Call this
     * after the wizard finishes scaffolding or when the active repo flips
     * (see [activeRepoName]). Idempotent. Passing `null` unbinds the
     * write-back path and reverts IdentityPrefs to in-memory only.
     */
    fun bindIdentityToActiveRepo(repoId: String?) {
        val cfg = repoId?.let { repoStore.get(it) }
        val root = ensureRegistered(cfg)
        identityPrefs.bindActiveRepo(
            rootDir = root,
            repoId = cfg?.repoId,
            strictlyKept = {
                modePrefs.state.value.mode == com.eight87.strictlykeptboy.ui.settings.AppMode.StrictlyKept
            },
        )
    }

    /**
     * Phase 2.1.K.1 — bind [ModePrefs] to the active repo so Settings
     * edits round-trip to `<rootDir>/mode.toml` and commit. Sibling of
     * [bindIdentityToActiveRepo]; call after wizard scaffolding or when
     * the active repo flips. Idempotent; null unbinds.
     */
    fun bindModeToActiveRepo(repoId: String?) {
        val cfg = repoId?.let { repoStore.get(it) }
        val root = ensureRegistered(cfg)
        modePrefs.bindActiveRepo(rootDir = root, repoId = cfg?.repoId)
    }

    /**
     * [L]#12 — shared helper for [bindIdentityToActiveRepo] +
     * [bindModeToActiveRepo]. Resolves the `rootDir` Path (or null when
     * unbinding) and ensures [GitRepoRegistry] has a live handle for the
     * supplied [cfg] so the commit step in IdentityPrefs / ModePrefs
     * doesn't silently no-op. Idempotent — `GitRepoRegistry.put`
     * overwrites by `repoId` and we only open when no handle exists.
     *
     * TODO(#12): ANR-risk `runBlocking` — `GitRepo.open` is suspend, and
     * these binders are called from the UI thread on wizard finish /
     * repo flip. Re-evaluate via `appScope.async { ... }.await()` once
     * the call sites can tolerate the async signature.
     */
    private fun ensureRegistered(cfg: com.eight87.strictlykeptboy.git.RepoConfig?): java.nio.file.Path? {
        if (cfg == null) return null
        val root = java.io.File(cfg.rootDir).toPath()
        if (GitRepoRegistry.get(cfg.repoId) == null) {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    GitRepo.open(
                        rootDir = File(cfg.rootDir),
                        repoId = cfg.repoId,
                        remotes = cfg.remotes,
                        primaryRemote = cfg.primaryRemote,
                        authorIdentity = cfg.authorIdentity,
                        defaultBranch = cfg.defaultBranch,
                    ).also(GitRepoRegistry::put)
                }
            }
        }
        return root
    }

    /**
     * Round 2.7.B.1 — app-wide mirror location prefs (SAF tree URI).
     */
    val repoStoragePrefs: RepoStoragePrefs by lazy { RepoStoragePrefs.open(appContext) }

    /**
     * Round 2.15 — demo-mode prefs (drives demo-first onboarding routing
     * in MainActivity + the Repositories demo toggle).
     */
    val demoModePrefs: com.eight87.strictlykeptboy.prefs.DemoModePrefs by lazy {
        com.eight87.strictlykeptboy.prefs.DemoModePrefs.open(appContext)
    }

    /**
     * Round 2.20 Phase B — rich-demo seeder. Lazy singleton, no
     * auto-call (Phase C wires the picker that triggers seeding).
     * Uses a plain `SharedPreferences` (not Encrypted) — the seed flag
     * is non-sensitive and we don't want first-launch encryption
     * overhead on a pref this hot.
     */
    val richDemoSeeder: com.eight87.strictlykeptboy.demo.RichDemoSeeder by lazy {
        val prefs = appContext.getSharedPreferences(
            "rich_demo_seeder_v1",
            android.content.Context.MODE_PRIVATE,
        )
        com.eight87.strictlykeptboy.demo.RichDemoSeeder(appContext, prefs)
    }

    /**
     * Round 2.17.A.8 — reconciler over the configured parent folder.
     * Replaces the Round 2.7.C `MirrorReconciler` (renamed via `git mv`).
     * No longer participates in the per-sync push fan-out — the parent
     * IS the working tree, so there's no separate mirror to keep
     * coherent. Surface is used by Phase E's adoption flow + the
     * one-time `pruneStaleMirrorRemotes` call after migration.
     */
    val parentReconciler: ParentReconciler by lazy {
        ParentReconciler(repoStore = repoStore, storagePrefs = repoStoragePrefs)
    }

    /**
     * Round 2.17 Phase E.7 — boot-time check for "the SAF tree URI we
     * persisted is no longer granted". The user can revoke it via
     * system Settings → Apps → permissions; when they do, our File-API
     * access still works (the underlying real path is cached) but new
     * processes can't re-establish the persisted permission and the
     * picker MUST be re-driven to get back into a sane state. Read
     * by the Repos pane to flip the red banner; observable so a future
     * settings-screen poll could refresh it without a process restart.
     */
    private val _safPermissionRevoked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val safPermissionRevoked: StateFlow<Boolean> = _safPermissionRevoked.asStateFlow()

    /** [L]#11 — narrow setter for [safPermissionRevoked]. */
    fun setSafPermissionRevoked(value: Boolean) {
        _safPermissionRevoked.value = value
    }

    /**
     * Round 2.17 Phase E.7 — single-shot SAF permission probe. Called
     * from `MainActivity.onCreate` after the graph is constructed.
     * Sets [safPermissionRevoked] to true iff the configured parent is
     * External AND its `treeUri` is no longer in
     * `contentResolver.persistedUriPermissions`.
     */
    fun refreshSafPermissionState() {
        val loc = repoStoragePrefs.location
        if (loc !is ParentLocation.External) {
            _safPermissionRevoked.value = false
            return
        }
        val target = runCatching { android.net.Uri.parse(loc.treeUri) }.getOrNull()
        if (target == null) {
            _safPermissionRevoked.value = true
            return
        }
        val granted = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == target && it.isReadPermission && it.isWritePermission
        }
        _safPermissionRevoked.value = !granted
    }

    /**
     * Round 2.17 Phase E.5 — single canonical RepoMover. Owned here so
     * the move-job dialog UI can observe its `progress` flow across
     * recompositions / Settings-pane category switches.
     */
    val repoMover: com.eight87.strictlykeptboy.sync.RepoMover by lazy {
        com.eight87.strictlykeptboy.sync.RepoMover(
            repoStore = repoStore,
            storagePrefs = repoStoragePrefs,
            deviceName = android.os.Build.MODEL ?: "",
        )
    }

    /**
     * Round 2.17.A.5 — fire-once migrator from D-2.7.b's
     * `filesDir/repos/` layout to D-2.17.a's `<parent>/<repoId>/` layout.
     * Idempotent at the prefs-flag level; safe to call repeatedly.
     *
     * Called from [runOneShotMigrations] on the IO dispatcher during
     * [parkRuntimes] so process-start doesn't block on disk work.
     */
    val parentLocationMigrator: ParentLocationMigrator by lazy {
        ParentLocationMigrator(
            storagePrefs = repoStoragePrefs,
            repoStore = repoStore,
            filesDir = appContext.filesDir,
            deviceName = android.os.Build.MODEL ?: "",
        )
    }

    /**
     * Round 2.17.B.5 — parked handle so Compose surfaces can fire the
     * SAF "pick parent" launcher that's `ComponentActivity`-scoped.
     *
     * MainActivity sets this to `{ parentPickerLauncher.launch(null) }`
     * after registering the launcher in `onCreate`. Composables call
     * `appGraph.parentPickerHandle?.invoke()`. Null = not yet wired
     * (previews / tests).
     *
     * (Renamed from `backupPickerHandle` per Phase B.5 — the legacy
     * compile-shim alias was dropped once the only two call-sites
     * landed in this phase.)
     */
    @Volatile
    private var _parentPickerHandle: (() -> Unit)? = null
    val parentPickerHandle: (() -> Unit)? get() = _parentPickerHandle

    /** [L]#11 — narrow setter for [parentPickerHandle]. */
    fun setParentPickerHandle(handle: (() -> Unit)?) {
        _parentPickerHandle = handle
    }

    /** Phase J — per-process sync scheduler. */
    val scheduler: SyncScheduler by lazy {
        SyncScheduler(
            repoStore = repoStore,
            statusStore = statusStore,
            repoProvider = { cfg ->
                // Round 2.17.A.8 — the per-sync mirror reconcile is gone.
                // The parent IS the working tree, so push fan-out only
                // covers the user-configured remotes; the legacy `mirror`
                // remote was pruned during migration.
                val fresh = repoStore.get(cfg.repoId) ?: cfg
                GitRepoRegistry.get(fresh.repoId) ?: runCatching {
                    GitRepo.open(
                        rootDir = File(fresh.rootDir),
                        repoId = fresh.repoId,
                        remotes = fresh.remotes,
                        primaryRemote = fresh.primaryRemote,
                        authorIdentity = fresh.authorIdentity,
                        defaultBranch = fresh.defaultBranch,
                    ).also(GitRepoRegistry::put)
                }.getOrNull()
            },
        )
    }

    // ----------------------------------------------------------------
    // Round 2.1.A — live RepoStore + Room cache → resolver bridge.
    //
    // The two publishers below own the data hand-off into the resolver
    // pipeline. The schedule pane and Auto surface both observe these
    // flows. Calendar / todolist metadata is synthesized from row IDs
    // until the per-repo `calendar.toml` / `todolist.toml` reader is
    // wired (follow-on phase).
    // ----------------------------------------------------------------

    /**
     * Round 2.1.B.6 / D-2.1.d — write-target repo display name.
     *
     * Renamed from `activeRepoName` to make the calendars-first split
     * explicit: the schedule reads from all repos by default, the avatar
     * drives only the *write* target. Backed by a `MutableStateFlow` so
     * wizard scaffolding + the repo switcher can flip it.
     *
     * **No more `"demo-repo"` literal.** Defaults to `""` when no repos
     * are configured; the shell renders the empty/placeholder avatar.
     * The wizard sets this to the first configured repo's `displayName`
     * after scaffolding completes (D-2.1.g).
     */
    private val _defaultWriteRepoName: MutableStateFlow<String> by lazy {
        // Lazy so AppGraph construction doesn't touch EncryptedSharedPreferences
        // (Robolectric can't init those — see `ColdStartBudgetTest`).
        MutableStateFlow(
            runCatching { repoStore.list().firstOrNull()?.displayName }.getOrNull().orEmpty(),
        )
    }
    val defaultWriteRepoName: StateFlow<String> get() = _defaultWriteRepoName.asStateFlow()

    /** [L]#11 — narrow setter for [defaultWriteRepoName]. */
    fun setDefaultWriteRepoName(name: String) {
        _defaultWriteRepoName.value = name
    }

    /**
     * Phase 2.1.I.2 — wizard re-entry request. Set to a non-null
     * [com.eight87.strictlykeptboy.ui.wizard.WizardScreen] when a settings
     * surface (e.g. Lifestyle → "Open wizard at Roles") wants the shell to
     * switch to the Wizard destination and pre-position the host at a
     * specific screen. Shell observes; resets back to null on finish.
     */
    // [L]#11 — left as `MutableStateFlow` on the public surface because
    // `SkbAppDestinationContent` still clears the request to null after
    // the wizard finishes (`wizardEntryRequest?.value = null`). The
    // `SkbAppShell` split (#9) shipped, but the clear path landed on
    // `SkbAppDestinationContent` rather than moving into a callback.
    // Tightening to `StateFlow` is gated on routing the clear through a
    // `ShellCallbacks.onWizardFinished` lambda. `setWizardEntryRequest(...)`
    // is the canonical write path from outside `composition/`.
    val wizardEntryRequest: MutableStateFlow<com.eight87.strictlykeptboy.ui.wizard.WizardScreen?> =
        MutableStateFlow(null)

    /** [L]#11 — narrow setter for [wizardEntryRequest]. */
    fun setWizardEntryRequest(screen: com.eight87.strictlykeptboy.ui.wizard.WizardScreen?) {
        wizardEntryRequest.value = screen
    }

    /** Phase D — read-through cache. Owned here so publishers can share it. */
    val cacheDatabase: CacheDatabase by lazy { CacheDatabase.open(appContext) }

    /**
     * Round 2.1.A.2 — visible schedule-pane date range. 90-day window
     * centered on today by default; downstream view-models can write to
     * this flow when the user scrolls / changes pane mode.
     */
    private val _visibleDateRange: MutableStateFlow<DateRange> = MutableStateFlow(
        run {
            val today = java.time.LocalDate.now()
            DateRange(start = today.minusDays(45), endInclusive = today.plusDays(45))
        },
    )
    val visibleDateRange: StateFlow<DateRange> = _visibleDateRange.asStateFlow()

    /** [L]#11 — narrow setter for [visibleDateRange]. */
    fun setVisibleDateRange(range: DateRange) {
        _visibleDateRange.value = range
    }

    /**
     * [M] #8d (audit pass 2026-05-17) — Round 2.18 CalendarContract
     * bridges + system-calendar prefs + AccountManager facade extracted
     * into a cohesive sub-graph. Consumers reach the underlying fields
     * via `graph.systemCalendarGraph.<field>`.
     */
    val systemCalendarGraph: SystemCalendarGraph by lazy {
        SystemCalendarGraph(
            appContext = appContext,
            appScope = appScope,
            repoStore = repoStore,
        )
    }

    /** Round 2.1.A.1 — Room → [RepoSnapshot] bridge. */
    val snapshotPublisher: IndexerSnapshotPublisher by lazy {
        IndexerSnapshotPublisher(
            db = cacheDatabase,
            repoStore = repoStore,
            scope = appScope,
            // Round 2.18.A.8 — fold external calendars into the snapshot.
            externalCalendars = systemCalendarGraph.systemCalendarsRepository.state,
        )
    }

    /** Round 2.1.A.2 — Room → [Renderer.Sources] bridge (windowed). */
    val sourcesPublisher: SourcesPublisher by lazy {
        SourcesPublisher(
            db = cacheDatabase,
            repoStore = repoStore,
            visibleRange = visibleDateRange,
            scope = appScope,
            // Round 2.18.C.0 — fold CalendarContract instances into the
            // resolver's event stream. Provider takes the resolver window
            // + the current set of system calendars and emits a list of
            // `EventInput`s. Re-emits whenever the system events bridge
            // notifies (provider-side ContentObserver) or the visible
            // calendar set changes.
            externalEventsProvider = { range ->
                @Suppress("OPT_IN_USAGE")
                systemCalendarGraph.buildExternalEventsFlow(range)
            },
        )
    }

    val snapshot: StateFlow<RepoSnapshot> get() = snapshotPublisher.state
    val sources: StateFlow<Renderer.Sources> get() = sourcesPublisher.state

    /**
     * Round 2.1.B.1 — calendars-first aggregator. Reads
     * `calendars/<id>/calendar.toml` from each configured repo and
     * overlays parsed fields onto the synthesized [snapshot].
     */
    val calendarRegistry: com.eight87.strictlykeptboy.composition.CalendarRegistry by lazy {
        com.eight87.strictlykeptboy.composition.CalendarRegistry(
            repoStore = repoStore,
            synthesizedSnapshot = snapshot,
            scope = appScope,
        )
    }

    /**
     * Round 2.1.B.9 — Together picker options at the calendar grain.
     * Derived from [calendarRegistry]: every active calendar across
     * every repo gets its own option; the source repo's display name
     * is the subtitle.
     */
    @Suppress("OPT_IN_USAGE")
    val togetherCalendarOptions: StateFlow<List<TogetherCalendarOption>> by lazy {
        kotlinx.coroutines.flow.combine(
            calendarRegistry.state,
            repoStore.state,
        ) { cals, repos ->
            val repoLabels = repos.associate { it.repoId to it.displayName }
            cals.map { cal ->
                TogetherCalendarOption(
                    calendarId = cal.ref.id,
                    repoId = cal.repo.id,
                    displayName = cal.displayName,
                    repoLabel = repoLabels[cal.repo.id] ?: cal.repo.id,
                )
            }
        }.stateIn(appScope, SharingStarted.Eagerly, emptyList())
    }

    /** Phase N — Together repo options (id + label) derived from RepoStore. */
    @Suppress("OPT_IN_USAGE")
    val togetherRepoOptions: StateFlow<List<TogetherRepoOption>> by lazy {
        repoStore.state
            .map { list -> list.map { TogetherRepoOption(it.repoId, it.displayName) } }
            .stateIn(
                appScope,
                SharingStarted.Eagerly,
                repoStore.state.value.map { TogetherRepoOption(it.repoId, it.displayName) },
            )
    }

    /**
     * D.88 / F48 — IconKind for the ACTIVE repo. Drives the top-bar leading
     * slot (`IdentityAvatar`) so the avatar reflects the chosen species /
     * photo / emoji of whichever repo is currently active. Switching active
     * repo updates this flow downstream.
     *
     * Resolution order: `iconKind` is derived from the RepoConfig's
     * `iconSpecies` (preferred — drives `Sticker(species)`), else from
     * `iconEmoji` (drives `Emoji(glyph)`), else falls back to
     * `AutoInitials` of the display name with a hash-derived seed colour.
     */
    @Suppress("OPT_IN_USAGE")
    val activeRepoIconKind: StateFlow<com.eight87.strictlykeptboy.ui.theming.RepoIconKind> by lazy {
        combine(defaultWriteRepoName, repoStore.state) { name, list ->
            val cfg = list.firstOrNull { it.displayName == name } ?: list.firstOrNull()
            cfg?.toIconKind() ?: com.eight87.strictlykeptboy.ui.theming.RepoIconKind.Sticker("bat")
        }.stateIn(
            appScope,
            SharingStarted.Eagerly,
            com.eight87.strictlykeptboy.ui.theming.RepoIconKind.Sticker("bat"),
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
        TodayEventSource {
            // Wrap each `MaterializedInstance` into an `AutoEvent` (2.1.G).
            // The one-off fast path doesn't run the off-schedule resolver
            // (RV-Q lives on `DayBand`), so `offSchedule = false` here
            // until the full snapshot bridge ships.
            renderTodaySync().map { AutoEvent(instance = it, offSchedule = false) }
        }
    }

    /**
     * Phase 2.1.G.2 / 2.1.G.4 — identity snapshot for the Auto surface.
     *
     * Reads from the default-write repo (`defaultWriteRepoName` → first
     * matching `RepoConfig`) and returns its `identity.toml`, falling
     * back to `null` when no repo is bound or the file is missing /
     * malformed. Resolved on every call so wizard edits + repo flips
     * are picked up without restarting the Auto session.
     */
    fun loadActiveIdentity(): com.eight87.strictlykeptboy.store.IdentityTomlData? = runCatching {
        val name = defaultWriteRepoName.value
        val cfg = repoStore.list().firstOrNull { it.displayName == name }
            ?: repoStore.list().firstOrNull()
            ?: return@runCatching null
        val root = File(cfg.rootDir).toPath()
        com.eight87.strictlykeptboy.store.IdentityTomlCodec.readOrDefault(root)
    }.getOrNull()

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
                // Round 2.27 / Phase D.3 — keeper-prompt events carry
                // over from past dates until cleared (D-2.27.d). Other
                // events stay strict same-day.
                if (evt.requiresResponse) {
                    !date.isAfter(today)
                } else {
                    !date.isBefore(today) && !date.isAfter(today)
                }
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
                    // Round 2.27 / Phase D.3 — propagate keeper-prompt
                    // flags so FromEventsProjector can route the
                    // instance through TaskSource.KeeperPrompt.
                    requiresResponse = evt.requiresResponse,
                    promptKind = evt.promptKind,
                    promptTarget = evt.promptTarget,
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
        // Round 2.17.A.5 — one-shot migration of D-2.7.b's
        // `filesDir/repos/` layout into D-2.17.a's `<parent>/<repoId>/`
        // layout. Idempotent at the prefs-flag level. Runs on IO,
        // fire-and-forget — the migrator's own [migrate] uses
        // `withContext(Dispatchers.IO)` internally. After migrating we
        // also drop the dead `mirror` remote from every repo so the
        // post-2.17 push fan-out doesn't try to publish to it.
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val result = parentLocationMigrator.migrate()
                if (result is ParentLocationMigrator.Result.Migrated) {
                    runCatching { parentReconciler.pruneStaleMirrorRemotes() }
                }
            }
        }
        scheduler.startPeriodicTicks()
        SyncRuntime.scheduler = scheduler
        SyncRuntime.statusStore = statusStore
        CarAppRuntime.todayEventSource = todayEventSource
        CarAppRuntime.identityProvider = { loadActiveIdentity() }
        // Phase 2.2.E.6 — parked-handle for BriefingWorker. Mirrors the
        // CarAppRuntime contract: a narrow source the worker collects
        // against on every fire, plus a lazy identity provider so wizard
        // edits + repo flips are picked up without restarting the worker.
        BriefingRuntime.source = briefingSource
        BriefingRuntime.identityProvider = { loadActiveIdentity() }

        // Round 2.18.G.4 — park handles the sync adapter needs at runtime.
        // The adapter runs in this process, so static lookup is safe.
        com.eight87.strictlykeptboy.system.SkbSyncRuntime.repoStore = repoStore
        com.eight87.strictlykeptboy.system.SkbSyncRuntime.cacheDatabase = cacheDatabase
        com.eight87.strictlykeptboy.system.SkbSyncRuntime.systemCalendarPrefs = systemCalendarGraph.systemCalendarPrefsStore

        // Round 2.18.G.7 — every successful `GitRepo.commitAll` requests a
        // sync against the matching repo's account. No-op when the toggle
        // is off or no account exists for that repoId.
        com.eight87.strictlykeptboy.system.SkbCommitNotifier.hook = { repoId ->
            systemCalendarGraph.skbAccountManager.requestSyncFor(repoId)
        }

        // Round 2.18.F deferred — wire externalEventsFlow into the
        // ExternalReminderScheduler so reminders mirrored from
        // CalendarContract get armed alongside file-backed events.
        // Reuses SourcesPublisher's external feed (sources.events) but
        // filters to entries whose `external` sidecar is non-null.
        appScope.launch {
            sources.collect { src ->
                val externals = src.events.filter { it.external != null }
                runCatching { systemCalendarGraph.externalReminderScheduler.refresh(externals) }
            }
        }

        // Round 2.25 Phase D — install the Now/Next provider into the
        // WidgetGraph singleton + ask both widget providers to refresh
        // on every snapshot change. The provider closure reads the
        // StateFlow value, so the snapshot stays fresh without holding
        // a strong reference in the widget process.
        com.eight87.strictlykeptboy.widget.WidgetGraph.installNowNextProvider { nowNextFlow.value }
        appScope.launch {
            nowNextFlow.collect {
                runCatching { com.eight87.strictlykeptboy.widget.now.NowWidgetProvider.forceRefresh(appContext) }
            }
        }

        // Round 2.25 Phase C — re-post the ongoing Now/Next notification
        // on every snapshot tick (indexer pulse + 60s relative-time
        // refresh per D-2.25.d / D-2.25.f). `setOnlyAlertOnce(true)`
        // suppresses any alert noise; the user only sees the line copy
        // animate.
        appScope.launch {
            nowNextFlow.collect { snap ->
                runCatching { nowNextNotifications.post(snap) }
            }
        }
    }

    /**
     * Phase 2.2.E.6 — narrow [BriefingSource] adapter on the AppGraph's
     * live snapshot. Returns materialized one-off events that overlap
     * the requested date in the requested zone. Recurrence-rule
     * instances flow through this same path once the full Renderer is
     * wired into the snapshot (see [renderTodaySync]).
     */
    val briefingSource: BriefingSource by lazy {
        BriefingSource { date, zone ->
            val src = sources.value
            src.events
                .asSequence()
                .filter { evt -> evt.start.withZoneSameInstant(zone).toLocalDate() == date }
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
    }

    /**
     * Optional helper for tests / future scopes: composition root
     * exposes a coroutine scope hook. v1 uses [GlobalScope] inline; this
     * stub lets a future refactor inject a SupervisorJob without
     * changing call sites.
     */
    val appScope: CoroutineScope get() = GlobalScope

    // ----------------------------------------------------------------
    // Round 2.16.B — active task playback (in-memory only).
    //
    // [M] #8d (audit pass 2026-05-17) — extracted into a cohesive
    // [TaskPlaybackGraph] sub-graph. The single canonical
    // `tasksViewState` is owned by the sub-graph and shared between
    // the schedule shell's task views and the playback projector
    // (consumers in MainActivity read it as `graph.taskPlaybackGraph.tasksViewState`).
    // ----------------------------------------------------------------

    val taskPlaybackGraph: TaskPlaybackGraph by lazy { TaskPlaybackGraph(appScope) }

    /**
     * Round 2.25 Phase A.2 — `Flow<NowNextSnapshot>` consumed by the
     * bottom NowPlayingSheetHost peek row (B), the ongoing notification
     * (C), and the homescreen / lockscreen widget(s) (D). Driven by
     * the same `briefingSource` the briefing worker uses + a 60-second
     * ticker so relative copy ("in 2h 15m") stays fresh.
     */
    @Suppress("OPT_IN_USAGE")
    val nowNextFlow: StateFlow<NowNextSnapshot> by lazy {
        val zone = java.time.ZoneId.systemDefault()
        val ticker = kotlinx.coroutines.flow.flow {
            while (true) {
                emit(java.time.Instant.now())
                kotlinx.coroutines.delay(60_000L)
            }
        }
        // Combine the briefing-source pulse (re-emits on indexer
        // refresh because [sources] is the underlying driver) with the
        // 60s ticker. We re-read briefingSource on every tick so
        // tomorrow's first event becomes "next" at the day boundary
        // without needing a separate scheduler.
        kotlinx.coroutines.flow.combine(sources, ticker) { _, at ->
            val today = java.time.LocalDate.now(zone)
            val todays = runCatching { briefingSource.instancesForDate(today, zone) }.getOrElse { emptyList() }
            val tomorrows = runCatching { briefingSource.instancesForDate(today.plusDays(1), zone) }
                .getOrElse { emptyList() }
            NowNextResolver.derive(todays, at = at, tomorrow = tomorrows)
        }.stateIn(appScope, SharingStarted.Eagerly, NowNextSnapshot.Empty)
    }

    /** Round 2.25 Phase C — ongoing-notification builder + poster. */
    val nowNextNotifications: NowNextNotificationProvider by lazy {
        NowNextNotificationProvider(appContext)
    }

}
