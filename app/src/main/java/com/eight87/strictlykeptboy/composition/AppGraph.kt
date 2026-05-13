package com.eight87.strictlykeptboy.composition

import android.content.Context
import com.eight87.strictlykeptboy.auto.CarAppRuntime
import com.eight87.strictlykeptboy.auto.TodayEventSource
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.avatar.AvatarPackPrefs
import com.eight87.strictlykeptboy.avatar.AvatarResolver
import com.eight87.strictlykeptboy.avatar.CompositePackStore
import com.eight87.strictlykeptboy.avatar.DefaultAvatarResolver
import com.eight87.strictlykeptboy.avatar.DefaultStickerResolver
import com.eight87.strictlykeptboy.avatar.StickerBitmapCache
import com.eight87.strictlykeptboy.avatar.StickerResolver
import com.eight87.strictlykeptboy.avatar.UserPackLoader
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.notif.SyncEventNotificationBridge
import com.eight87.strictlykeptboy.resolver.CommonTimeFinder
import com.eight87.strictlykeptboy.resolver.DateRange
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

    /** Phase WW.5 — per-species active pack + per-activity sticker overrides. */
    val avatarPackPrefs: AvatarPackPrefs by lazy { AvatarPackPrefs.open(appContext) }

    /** Phase WW.1 — bundled-pack registry loaded from APK assets. */
    val assetPackLoader: AssetPackLoader by lazy { AssetPackLoader(appContext) }

    /** Phase WW.4 — user-installed pack registry (`<filesDir>/avatar-packs/`). */
    val userPackLoader: UserPackLoader by lazy { UserPackLoader.openFor(appContext) }

    /** Phase WW.2 — composite store: user packs first, then bundled defaults. */
    val packStore: CompositePackStore by lazy {
        CompositePackStore(
            sources = listOf(
                userPackLoader.loadAll(),
                assetPackLoader.loadAll(),
            ),
        )
    }

    /** Phase WW.2 — 6-rung D.66 resolver. */
    val stickerResolver: StickerResolver by lazy {
        DefaultStickerResolver(
            packStore = packStore,
            activePackProvider = { species -> avatarPackPrefs.activePackFor(species) },
        )
    }

    /** Phase WW.6 (MVP) — shared LRU cache for decoded sticker bitmaps. */
    val stickerBitmapCache: StickerBitmapCache by lazy { StickerBitmapCache() }

    /**
     * Phase WW — top-level avatar facade for top-bar / Repos rows / NowCard.
     *
     * Dispatches bitmap loading to whichever loader owns the resolved pack
     * (user vs bundled), falling back to `R.drawable.about_bat` when a pack
     * references a file that hasn't shipped yet (the initial bundled-pack
     * manifests are scaffolds — artwork lands in a follow-up).
     */
    val avatarResolver: AvatarResolver by lazy {
        DefaultAvatarResolver(
            stickerResolver = stickerResolver,
            cache = stickerBitmapCache,
            loader = { packId, file ->
                val species = if (packId.startsWith("default-")) packId.removePrefix("default-") else null
                if (species != null) {
                    assetPackLoader.loadBitmap(species, file)
                } else {
                    userPackLoader.loadBitmap(packId, file)
                }
            },
        )
    }

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

    /**
     * Phase 2.1.J.1 — bind [IdentityPrefs] to the active repo so Settings
     * edits round-trip to `<rootDir>/identity.toml` and commit. Call this
     * after the wizard finishes scaffolding or when the active repo flips
     * (see [activeRepoName]). Idempotent. Passing `null` unbinds the
     * write-back path and reverts IdentityPrefs to in-memory only.
     */
    fun bindIdentityToActiveRepo(repoId: String?) {
        val cfg = repoId?.let { repoStore.get(it) }
        val root = cfg?.let { java.io.File(it.rootDir).toPath() }
        identityPrefs.bindActiveRepo(
            rootDir = root,
            repoId = cfg?.repoId,
            strictlyKept = {
                modePrefs.state.value.mode == com.eight87.strictlykeptboy.ui.settings.AppMode.StrictlyKept
            },
        )
    }

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
    val defaultWriteRepoName: MutableStateFlow<String> by lazy {
        // Lazy so AppGraph construction doesn't touch EncryptedSharedPreferences
        // (Robolectric can't init those — see `ColdStartBudgetTest`).
        MutableStateFlow(
            runCatching { repoStore.list().firstOrNull()?.displayName }.getOrNull().orEmpty(),
        )
    }

    /**
     * Compatibility alias. The rename in 2.1.B.6 is gradual — UI surfaces
     * still use the old name internally (parameter naming inside scaffold
     * composables remains `activeRepoName` because that parameter encodes
     * "the avatar's current label", which is still meaningful). Removing
     * the alias is a 2.1.L follow-on.
     */
    @Deprecated(
        message = "Use defaultWriteRepoName (2.1.B.6 rename).",
        replaceWith = ReplaceWith("defaultWriteRepoName"),
    )
    val activeRepoName: MutableStateFlow<String> get() = defaultWriteRepoName

    /**
     * Phase 2.1.I.2 — wizard re-entry request. Set to a non-null
     * [com.eight87.strictlykeptboy.ui.wizard.WizardScreen] when a settings
     * surface (e.g. Lifestyle → "Open wizard at Roles") wants the shell to
     * switch to the Wizard destination and pre-position the host at a
     * specific screen. Shell observes; resets back to null on finish.
     */
    val wizardEntryRequest: MutableStateFlow<com.eight87.strictlykeptboy.ui.wizard.WizardScreen?> =
        MutableStateFlow(null)

    /** Phase D — read-through cache. Owned here so publishers can share it. */
    val cacheDatabase: CacheDatabase by lazy { CacheDatabase.open(appContext) }

    /**
     * Round 2.1.A.2 — visible schedule-pane date range. 90-day window
     * centered on today by default; downstream view-models can write to
     * this flow when the user scrolls / changes pane mode.
     */
    val visibleDateRange: MutableStateFlow<DateRange> = MutableStateFlow(
        run {
            val today = java.time.LocalDate.now()
            DateRange(start = today.minusDays(45), endInclusive = today.plusDays(45))
        },
    )

    /** Round 2.1.A.1 — Room → [RepoSnapshot] bridge. */
    val snapshotPublisher: IndexerSnapshotPublisher by lazy {
        IndexerSnapshotPublisher(
            db = cacheDatabase,
            repoStore = repoStore,
            scope = appScope,
        )
    }

    /** Round 2.1.A.2 — Room → [Renderer.Sources] bridge (windowed). */
    val sourcesPublisher: SourcesPublisher by lazy {
        SourcesPublisher(
            db = cacheDatabase,
            repoStore = repoStore,
            visibleRange = visibleDateRange,
            scope = appScope,
        )
    }

    val snapshot: StateFlow<RepoSnapshot> get() = snapshotPublisher.state
    val sources: StateFlow<Renderer.Sources> get() = sourcesPublisher.state

    /**
     * Round 2.1.B.1 — calendars-first aggregator. Reads
     * `calendars/<id>/calendar.toml` from each configured repo and
     * overlays parsed fields onto the synthesized [snapshot].
     */
    val calendarRegistry: com.eight87.strictlykeptboy.resolver.CalendarRegistry by lazy {
        com.eight87.strictlykeptboy.resolver.CalendarRegistry(
            repoStore = repoStore,
            synthesizedSnapshot = snapshot,
            scope = appScope,
        )
    }

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
            GlobalScope,
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
