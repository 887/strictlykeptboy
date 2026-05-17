package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.task.StubTaskPlaybackSource
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.ProvideWindowSizeClass
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import com.eight87.strictlykeptboy.ui.repos.ReposPane
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.schedule.SchedulePane
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.settings.SettingsAccess
import com.eight87.strictlykeptboy.ui.settings.SettingsPane
import com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest
import com.eight87.strictlykeptboy.ui.tasks.TasksFilter
import com.eight87.strictlykeptboy.ui.tasks.TasksPane
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.together.TogetherPane
import com.eight87.strictlykeptboy.ui.together.TogetherViewModel
import com.eight87.strictlykeptboy.ui.trip.TripDraft
import com.eight87.strictlykeptboy.ui.trip.TripWizardNavHost
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft
import com.eight87.strictlykeptboy.ui.wizard.WizardNavHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase polish — navigation-layout swap (correction to UI-A / Phase F).
 *
 * Previously [com.eight87.strictlykeptboy.ui.scaffold] used
 * `NavigationSuiteScaffold` which put destinations on a left rail and
 * per-pane view-mode tabs in the top bar. The intent (see tonearmboy's
 * library scaffold + the user's polish-pass note) is inverted:
 *
 *   - **LEFT vertical rail** = per-pane view-mode tabs (Schedule:
 *     Day/Week/Month/Agenda/Year; Tasks: Combined/Today/Per-list/
 *     Shopping/Standing). Rotated text labels in a narrow 52dp rail
 *     (same shape as `tonearmboy`'s `LibraryRail`). Implemented in
 *     [SkbScheduleRail.kt] as `RailColumn` + `RailItem`.
 *   - **TOP-RIGHT** = cross-content destination buttons
 *     (Schedule / Tasks / Together / Repos / Wizard / Settings).
 *     Implemented in [SkbTopBar.kt] as `ShellTopBar`.
 *   - **TOP-LEFT** = repo switcher chip (unchanged).
 *   - **TOP rightmost** = sync button + identity avatar (unchanged).
 *
 * Round 2.21 SOLID split (post-1170-LOC threshold): this file now
 * owns ONLY the shell's composition root and destination dispatch.
 * Top-bar chrome lives in `SkbTopBar.kt`; the left rail lives in
 * `SkbScheduleRail.kt`; the bottom-anchored Now-Playing sheet host
 * lives in `NowPlayingSheetHost.kt`. The shell composes the three.
 *
 * SOLID notes:
 *  - **S/I:** the shell owns *only* destination dispatch + the visual
 *    composition. Each pane still owns its own state. The rail is
 *    parameterised by a [RailItem] list; the shell does not know
 *    about Day/Week/etc. constants.
 *  - **O:** new destinations join [TopDestination]; the `when` here is
 *    exhaustive.
 *  - **D:** ViewModels and stores are passed in as before; this shell
 *    is the same composition surface as the old [AppScaffold].
 */

const val TestTagAppShell = "SkbAppShell"
const val TestTagShellTopBar = "ShellTopBar"
const val TestTagShellRail = "ShellRail"
const val TestTagShellContent = "ShellContent"
const val TestTagShellDestPrefix = "ShellDest-"
const val TestTagShellRailItemPrefix = "ShellRail-"
/** Round 2.16.F — global settings cog in the top-bar action row. */
const val TestTagShellSettingsCog = "ShellSettingsCog"

/**
 * Phase U.4 / F11 note: [label] is **wire-format** / stable English fallback
 * for testTag composition and toString. Translatable UI display routes
 * through `TopDestination.labelString()` in `ui/a11y/EnumLabels.kt`.
 */
enum class TopDestination(val label: String, val icon: ImageVector) {
    Schedule("Schedule", Icons.Filled.CalendarMonth),
    // Round 2.26.A — `Tasks` reinstated as a TopDestination after being
    // deleted in 2.16.E. The expanded NowPlayingScreen sheet remains
    // (with a TasksFilter chip-strip now, per Round 2.26.A.6) but the
    // rail-driven destination is canonical (D-2.26.a).
    Tasks("Tasks", Icons.Filled.Checklist),
    Together("Together", Icons.Filled.Groups),
    Repos("Repos", Icons.Filled.Folder),
    Wizard("Wizard", Icons.Filled.AutoAwesome),
    // Phase DDD.13 / UI-SS — dom-/boy-side review feed surface.
    Reviews("Reviews", Icons.Filled.RateReview),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun SkbAppShell(
    activeRepoNameFlow: StateFlow<String>,
    scheduleState: ScheduleViewState,
    modifier: Modifier = Modifier,
    onPersistTab: (ScheduleViewTab) -> Unit = {},
    tasksState: TasksViewState = remember { TasksViewState() },
    onWriteTask: (TaskQuickAddRequest) -> Unit = {},
    reposState: ReposViewState? = null,
    secretsStore: SecretsStore? = null,
    togetherViewModel: TogetherViewModel? = null,
    onSyncClick: () -> Unit = {},
    neutralMode: Boolean = false,
    onWizardScaffold: suspend (WizardDraft) -> Result<Unit> = { Result.success(Unit) },
    onWizardFinish: () -> Unit = {},
    /** Phase CCC.8 — trip-wizard materializer (writes overlay calendar + commits). */
    onTripMaterialize: suspend (TripDraft) -> Result<Unit> = { Result.success(Unit) },
    importExportState: ImportExportViewState? = null,
    onPickImportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit = {},
    onPickExportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit = {},
    settingsAccess: SettingsAccess = SettingsAccess(),
    activeIconKindFlow: StateFlow<com.eight87.strictlykeptboy.ui.theming.RepoIconKind>? = null,
    eventCreateController: com.eight87.strictlykeptboy.ui.schedule.EventCreateController? = null,
    /**
     * Phase 2.1.I.2 — external request to switch to the Wizard destination
     * and pre-position the host at a specific screen (e.g. Roles, from the
     * Settings → Lifestyle entry-point). When non-null, the shell selects
     * [TopDestination.Wizard], passes `initialScreen` down, then clears
     * the request on wizard finish. Null → no auto-routing.
     */
    wizardEntryRequest: kotlinx.coroutines.flow.MutableStateFlow<
        com.eight87.strictlykeptboy.ui.wizard.WizardScreen?
    >? = null,
    /**
     * Phase 2.1.I.4 — share-with-dom CTA from the wizard's last screen.
     * Caller wires this to ShareSheet with the just-scaffolded repo + the
     * `allowWriteBack` checkbox pre-set.
     */
    onShareWithDom: () -> Unit = {},
    /**
     * Round 2.1.B.2 — phone-local calendar visibility powering the
     * overlay-picker button + per-row segmented zoom control.
     */
    calendarVisibility: com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs? = null,
    /**
     * Round 2.1.B.2 / B.4 — long-press handler for calendar chips.
     * Host opens [com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsSheet].
     */
    onLongPressCalendar: ((com.eight87.strictlykeptboy.resolver.CalendarMeta) -> Unit)? = null,
    /**
     * Round 2.22 / Fix 3 — inline priority writer fired from the
     * overlay-picker per-row OutlinedTextField. Default no-op so
     * tests / previews don't have to plumb the writer.
     */
    onOverlayPriorityChange:
        ((com.eight87.strictlykeptboy.resolver.CalendarMeta, Int) -> Unit) = { _, _ -> },
    /**
     * Round 2.23.2 — inline color writer fired from the overlay-picker
     * Color row. Default no-op so tests / previews don't have to
     * plumb the writer.
     */
    onOverlayColorChange:
        ((com.eight87.strictlykeptboy.resolver.CalendarMeta, Int) -> Unit) = { _, _ -> },
    /**
     * Round 2.16.B — task-playback source feeding MiniPlayer +
     * NowPlayingScreen. Defaults to the Phase A stub for previews /
     * tests; MainActivity wires `appGraph.taskTransport`.
     */
    taskPlaybackSource: Any = StubTaskPlaybackSource,
    /**
     * Round 2.16.B — temporary "Start" affordance handler exposed on
     * task rows.
     */
    onStartTask: ((String) -> Unit)? = null,
    /**
     * Round 2.17.D — "Keep inside the app" CTA on the wizard's storage
     * step. MainActivity wires this to write
     * `ParentLocation.Internal(filesDir/strictlykeptboy)` + the `.skb-root`
     * marker. Default is a no-op so previews / tests don't have to plumb
     * it. The wizard auto-advances when prefs flip.
     */
    onPickInternalStorage: () -> Unit = {},
    /**
     * Round 2.22 / Phase B UI follow-up — single-instance drop handler
     * for drag-to-reschedule. MainActivity wires to
     * [com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController].
     */
    onSingleDrop: ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    /** Round 2.22 / Phase B UI follow-up — recurring-rule drop handler with branch choice. */
    onRecurringDrop: ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime, com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController.RecurringChoice) -> Unit)? = null,
    /**
     * Round 2.25 Phase B — Now/Next snapshot stream surfaced on the
     * bottom NowPlayingSheetHost peek row (D-2.25.c). Default null so
     * previews / tests don't need to plumb it; MainActivity wires
     * `graph.nowNextFlow`.
     */
    nowNextFlow: kotlinx.coroutines.flow.StateFlow<
        com.eight87.strictlykeptboy.resolver.NowNextSnapshot
    >? = null,
) {
    ProvideWindowSizeClass(modifier = modifier) { _ ->
        SkbAppShellContent(
            activeRepoNameFlow = activeRepoNameFlow,
            scheduleState = scheduleState,
            onPersistTab = onPersistTab,
            tasksState = tasksState,
            onWriteTask = onWriteTask,
            reposState = reposState,
            secretsStore = secretsStore,
            togetherViewModel = togetherViewModel,
            onSyncClick = onSyncClick,
            neutralMode = neutralMode,
            onWizardScaffold = onWizardScaffold,
            onWizardFinish = onWizardFinish,
            onTripMaterialize = onTripMaterialize,
            importExportState = importExportState,
            onPickImportFile = onPickImportFile,
            onPickExportFile = onPickExportFile,
            settingsAccess = settingsAccess,
            activeIconKindFlow = activeIconKindFlow,
            eventCreateController = eventCreateController,
            wizardEntryRequest = wizardEntryRequest,
            onShareWithDom = onShareWithDom,
            calendarVisibility = calendarVisibility,
            onLongPressCalendar = onLongPressCalendar,
            onOverlayPriorityChange = onOverlayPriorityChange,
            onOverlayColorChange = onOverlayColorChange,
            taskPlaybackSource = taskPlaybackSource,
            onStartTask = onStartTask,
            onPickInternalStorage = onPickInternalStorage,
            onSingleDrop = onSingleDrop,
            onRecurringDrop = onRecurringDrop,
            nowNextFlow = nowNextFlow,
        )
    }
}

@Composable
private fun SkbAppShellContent(
    activeRepoNameFlow: StateFlow<String>,
    scheduleState: ScheduleViewState,
    onPersistTab: (ScheduleViewTab) -> Unit,
    tasksState: TasksViewState,
    onWriteTask: (TaskQuickAddRequest) -> Unit,
    reposState: ReposViewState?,
    secretsStore: SecretsStore?,
    togetherViewModel: TogetherViewModel?,
    onSyncClick: () -> Unit,
    neutralMode: Boolean,
    onWizardScaffold: suspend (WizardDraft) -> Result<Unit>,
    onWizardFinish: () -> Unit,
    onTripMaterialize: suspend (TripDraft) -> Result<Unit>,
    importExportState: ImportExportViewState?,
    onPickImportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    onPickExportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    settingsAccess: SettingsAccess,
    activeIconKindFlow: StateFlow<com.eight87.strictlykeptboy.ui.theming.RepoIconKind>?,
    eventCreateController: com.eight87.strictlykeptboy.ui.schedule.EventCreateController? = null,
    wizardEntryRequest: kotlinx.coroutines.flow.MutableStateFlow<
        com.eight87.strictlykeptboy.ui.wizard.WizardScreen?
    >? = null,
    onShareWithDom: () -> Unit = {},
    calendarVisibility: com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs? = null,
    onLongPressCalendar: ((com.eight87.strictlykeptboy.resolver.CalendarMeta) -> Unit)? = null,
    onOverlayPriorityChange:
        ((com.eight87.strictlykeptboy.resolver.CalendarMeta, Int) -> Unit) = { _, _ -> },
    onOverlayColorChange:
        ((com.eight87.strictlykeptboy.resolver.CalendarMeta, Int) -> Unit) = { _, _ -> },
    taskPlaybackSource: Any = StubTaskPlaybackSource,
    onStartTask: ((String) -> Unit)? = null,
    /** Round 2.17.D — see [SkbAppShell.onPickInternalStorage]. */
    onPickInternalStorage: () -> Unit = {},
    onSingleDrop: ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    onRecurringDrop: ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime, com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController.RecurringChoice) -> Unit)? = null,
    nowNextFlow: kotlinx.coroutines.flow.StateFlow<
        com.eight87.strictlykeptboy.resolver.NowNextSnapshot
    >? = null,
) {
    var selected by rememberSaveable { mutableStateOf(TopDestination.Schedule) }
    // Phase 2.1.I.2 — observe wizard re-entry requests.
    val wizardEntry = wizardEntryRequest?.collectAsState()?.value
    androidx.compose.runtime.LaunchedEffect(wizardEntry) {
        if (wizardEntry != null) {
            selected = TopDestination.Wizard
        }
    }
    val activeRepoName by activeRepoNameFlow.collectAsState()
    // D.88 / F48 — top-bar avatar reflects the active repo's iconKind. Defaults
    // to Sticker("bat") if the caller hasn't wired the flow (e.g. tests, previews).
    val activeIconKind by (activeIconKindFlow
        ?: MutableStateFlow(com.eight87.strictlykeptboy.ui.theming.RepoIconKind.Sticker("bat") as com.eight87.strictlykeptboy.ui.theming.RepoIconKind))
        .collectAsState()

    val scheduleTab by scheduleState.selectedTab.collectAsState()

    // Round 2.23.1 / D.118 — Reviews destination filter state, hoisted
    // here so the left rail (built below) and `ReviewsPane` (rendered
    // by `SkbAppDestinationContent`) share a single source of truth.
    // Default `All` matches the previous in-pane default.
    var reviewsFilter by rememberSaveable {
        mutableStateOf(com.eight87.strictlykeptboy.ui.reviews.ReviewsFilter.All)
    }

    // Round 2.26.A.3 — Tasks destination filter state, hoisted here so
    // the left rail and `TasksPane` share a single source of truth.
    // Default `Today` per D-2.26.b.
    var tasksFilter by rememberSaveable {
        mutableStateOf(TasksFilter.Today)
    }

    // Per-destination rail item set. Each entry maps to either a pane's
    // existing tab enum (Schedule, Tasks) or stays empty (Repos /
    // Together / Wizard / Settings — Settings owns its own master-detail
    // category list and does not contribute to the global rail).
    val railItems: List<RailItem> = when (selected) {
        TopDestination.Schedule -> ScheduleViewTab.entries.map { tab ->
            RailItem(
                key = tab.name,
                labelRes = scheduleTabLabelRes(tab),
                selected = tab == scheduleTab,
                onClick = {
                    scheduleState.setSelectedTab(tab)
                    onPersistTab(tab)
                },
            )
        }
        TopDestination.Tasks -> TasksFilter.entries.map { f ->
            RailItem(
                key = f.name,
                labelRes = tasksFilterLabelRes(f),
                selected = f == tasksFilter,
                onClick = { tasksFilter = f },
            )
        }
        TopDestination.Reviews -> com.eight87.strictlykeptboy.ui.reviews.ReviewsFilter.entries.map { f ->
            RailItem(
                key = f.name,
                labelRes = com.eight87.strictlykeptboy.ui.reviews.reviewsFilterLabelRes(f),
                selected = f == reviewsFilter,
                onClick = { reviewsFilter = f },
            )
        }
        TopDestination.Together,
        TopDestination.Repos,
        TopDestination.Wizard,
        TopDestination.Settings -> emptyList()
    }

    // Phase CCC — quick-trip wizard is an overlay (not a destination) so
    // adding it doesn't invalidate the existing 6-destination test
    // expectations or the top-bar visual budget.
    var tripWizardOpen by rememberSaveable { mutableStateOf(false) }

    // Big top-left title = destination name ("Schedule" / "Tasks" / etc.).
    // The rail already shows the current view-mode (rotated "Day" / "Week"
    // / "Combined" / etc.), so duplicating it as the top-left title reads
    // redundant — destination is the right granularity here.
    val title = selected.labelString()

    // Round 2.22 / Fix 1 — overlay picker overlay state. Hoisted above
    // the shell's Column so the picker can cover the top-bar AND the
    // left rail (previously it was nested inside the destination-
    // content Box, which left both visible — "the overlays go over the
    // settings and it's dumb"). The picker now mirrors the same
    // Surface(fillMaxSize) idiom that the TripWizardNavHost uses.
    var overlayPickerOpen by rememberSaveable { mutableStateOf(false) }
    // Round 2.25 follow-up — full-screen event-detail overlay state.
    // Hoisted here (rather than nested in SchedulePane) so the detail
    // surface covers the rail + top-bar. Mirrors the same outer-Box
    // mount pattern Round 2.22 used for OverlayPickerScreen
    // (Round 2.23 Phase D shipped EventDetailScreen inside the
    // destination-content Box, which left the rail still visible —
    // this hoist closes that loop).
    var pendingEventDetail by remember {
        mutableStateOf<com.eight87.strictlykeptboy.resolver.DayBand?>(null)
    }
    NowPlayingSheetHost(
        source = taskPlaybackSource,
        tasksState = tasksState,
        onWriteTask = onWriteTask,
        onStartTask = onStartTask,
        nowNextFlow = nowNextFlow,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().testTag(TestTagAppShell),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ShellTopBar(
                activeRepoName = activeRepoName,
                activeIconKind = activeIconKind,
                title = title,
                selectedDest = selected,
                onSelectDest = { selected = it },
                onSyncClick = onSyncClick,
                onIdentityClick = { /* UI-L — stubbed */ },
                // D.88: bat avatar IS the repo affordance. Tap navigates to
                // the Repos destination. The Repos `ShellDest-` button stays
                // in the row to satisfy `AppShellNavigationSwapTest`; the
                // avatar is a parallel affordance per user direction.
                onRepoSwitcherClick = { selected = TopDestination.Repos },
                onSettingsTap = { selected = TopDestination.Settings },
                modePrefs = settingsAccess.modePrefs,
            )
            Row(modifier = Modifier.fillMaxSize()) {
                // Left rail only renders when the destination has view-mode
                // tabs to show. Settings/Repos/Wizard have no view-modes so
                // the rail collapses and the pane spans edge-to-edge (user
                // direction 2026-05-13 — "still space on the left").
                if (railItems.isNotEmpty()) {
                    // Round 2.22 / Fix 2 — overlay-picker icon now lives at
                    // the bottom of the rail (tonearmboy LibraryRail
                    // parity). Only the Schedule destination has the
                    // picker wiring; other rail-bearing destinations pass
                    // nulls and the bottom slot collapses.
                    val pickerCalendars = if (selected == TopDestination.Schedule) {
                        scheduleState.calendarsFlow
                    } else null
                    RailColumn(
                        items = railItems,
                        activeIconKind = activeIconKind,
                        onAccountTap = { selected = TopDestination.Repos },
                        onSettingsTap = { selected = TopDestination.Settings },
                        overlayPickerCalendars = pickerCalendars,
                        overlayPickerPrefs = calendarVisibility,
                        onOverlayPickerClick = { overlayPickerOpen = true },
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().testTag(TestTagShellContent),
                ) {
                    SkbAppDestinationContent(
                        reviewsFilter = reviewsFilter,
                        tasksFilter = tasksFilter,
                        tasksState = tasksState,
                        selected = selected,
                        activeRepoName = activeRepoName,
                        scheduleState = scheduleState,
                        onSyncClick = onSyncClick,
                        eventCreateController = eventCreateController,
                        onPlanTrip = { tripWizardOpen = true },
                        calendarVisibility = calendarVisibility,
                        onLongPressCalendar = onLongPressCalendar,
                        togetherViewModel = togetherViewModel,
                        neutralMode = neutralMode,
                        reposState = reposState,
                        secretsStore = secretsStore,
                        settingsAccess = settingsAccess,
                        onSelectDest = { selected = it },
                        onPickInternalStorage = onPickInternalStorage,
                        onWizardScaffold = onWizardScaffold,
                        onWizardFinish = onWizardFinish,
                        wizardEntry = wizardEntry,
                        wizardEntryRequest = wizardEntryRequest,
                        onShareWithDom = onShareWithDom,
                        importExportState = importExportState,
                        onPickImportFile = onPickImportFile,
                        onPickExportFile = onPickExportFile,
                        onSingleDrop = onSingleDrop,
                        onRecurringDrop = onRecurringDrop,
                        onOpenEventDetailFullScreen = { pendingEventDetail = it },
                    )
                    // Phase CCC — overlay the trip wizard above the active pane
                    // when open. Covers the full content area; back/cancel
                    // dismisses without changing the active TopDestination.
                    if (tripWizardOpen) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxSize().testTag("TripWizardOverlay"),
                        ) {
                            TripWizardNavHost(
                                onFinish = { tripWizardOpen = false },
                                onCancel = { tripWizardOpen = false },
                                onMaterialize = onTripMaterialize,
                            )
                        }
                    }
                }
            }
        }
        // Round 2.22 / Fix 1 — full-shell-cover overlay picker. Mounted
        // at the Box root above the (top-bar + rail + content) Column
        // so its own Surface fully covers the chrome. Back navigates
        // to the pane underneath.
        val calsFlow = scheduleState.calendarsFlow
        if (overlayPickerOpen && calendarVisibility != null && calsFlow != null) {
            // Round 2.23.5 / Fix 3 — resolve repo GUID → friendly
            // display name via the live RepoStore flow (already plumbed
            // through `reposState`). Recomputed on each repo-list change.
            val reposList = reposState?.repos?.collectAsState()?.value.orEmpty()
            val repoNameById = remember(reposList) {
                reposList.associate { it.repoId to it.displayName }
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                com.eight87.strictlykeptboy.ui.calendars.OverlayPickerScreen(
                    calendarsFlow = calsFlow,
                    visibilityPrefs = calendarVisibility,
                    onBack = { overlayPickerOpen = false },
                    onEditCalendar = { meta ->
                        onLongPressCalendar?.invoke(meta)
                    },
                    onPriorityChange = onOverlayPriorityChange,
                    onColorChange = onOverlayColorChange,
                    repoDisplayNameFor = { repoId -> repoNameById[repoId] },
                )
            }
        }
        // Round 2.25 follow-up — full-screen EventDetailScreen mount.
        // Mounted at the same outer-Box level as OverlayPickerScreen
        // so the detail surface covers the rail + top-bar (the in-pane
        // mount inside SchedulePane leaked the rail through). The
        // back arrow on EventDetailScreen clears `pendingEventDetail`,
        // restoring the underlying pane with scroll state intact.
        pendingEventDetail?.let { band ->
            val detailCtx = androidx.compose.ui.platform.LocalContext.current
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                com.eight87.strictlykeptboy.ui.schedule.EventDetailScreen(
                    band = band,
                    onBack = { pendingEventDetail = null },
                    onEdit = {
                        android.widget.Toast.makeText(
                            detailCtx,
                            "Event editor coming in Round 3",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
      }  // end outer Box
    }
    }  // end NowPlayingSheetHost
}

/**
 * Round 2.21 SOLID split — destination dispatch. The shell composes
 * the active pane based on [selected]; this is the single
 * exhaustive-`when` site that knows which pane class to call. Kept
 * inside this file (not extracted) because each branch is a *single*
 * call-site and extracting would force a larger argument-bundle.
 */
@Composable
private fun SkbAppDestinationContent(
    reviewsFilter: com.eight87.strictlykeptboy.ui.reviews.ReviewsFilter =
        com.eight87.strictlykeptboy.ui.reviews.ReviewsFilter.All,
    tasksFilter: TasksFilter = TasksFilter.Today,
    tasksState: TasksViewState,
    selected: TopDestination,
    activeRepoName: String,
    scheduleState: ScheduleViewState,
    onSyncClick: () -> Unit,
    eventCreateController: com.eight87.strictlykeptboy.ui.schedule.EventCreateController?,
    onPlanTrip: () -> Unit,
    calendarVisibility: com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs?,
    onLongPressCalendar: ((com.eight87.strictlykeptboy.resolver.CalendarMeta) -> Unit)?,
    togetherViewModel: TogetherViewModel?,
    neutralMode: Boolean,
    reposState: ReposViewState?,
    secretsStore: SecretsStore?,
    settingsAccess: SettingsAccess,
    onSelectDest: (TopDestination) -> Unit,
    onPickInternalStorage: () -> Unit,
    onWizardScaffold: suspend (WizardDraft) -> Result<Unit>,
    onWizardFinish: () -> Unit,
    wizardEntry: com.eight87.strictlykeptboy.ui.wizard.WizardScreen?,
    wizardEntryRequest: kotlinx.coroutines.flow.MutableStateFlow<
        com.eight87.strictlykeptboy.ui.wizard.WizardScreen?
    >?,
    onShareWithDom: () -> Unit,
    importExportState: ImportExportViewState?,
    onPickImportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    onPickExportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    onSingleDrop: ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    onRecurringDrop: ((com.eight87.strictlykeptboy.resolver.DayBand, java.time.OffsetDateTime, com.eight87.strictlykeptboy.ui.schedule.DragRescheduleController.RecurringChoice) -> Unit)? = null,
    /** Round 2.25 follow-up — host-owned full-screen event-detail opener. */
    onOpenEventDetailFullScreen: ((com.eight87.strictlykeptboy.resolver.DayBand) -> Unit)? = null,
) {
    when (selected) {
        TopDestination.Schedule -> SchedulePane(
            activeRepoName = activeRepoName,
            state = scheduleState,
            onSyncClick = onSyncClick,
            eventCreateController = eventCreateController,
            onPlanTrip = onPlanTrip,
            calendarVisibility = calendarVisibility,
            onLongPressCalendar = onLongPressCalendar,
            onSingleDrop = onSingleDrop,
            onRecurringDrop = onRecurringDrop,
            onOpenEventDetailFullScreen = onOpenEventDetailFullScreen,
        )
        TopDestination.Tasks -> TasksPane(
            filter = tasksFilter,
            tasksState = tasksState,
        )
        TopDestination.Together -> if (togetherViewModel != null) {
            TogetherPane(vm = togetherViewModel, neutralMode = neutralMode)
        } else {
            PlaceholderScreen(stringResource(R.string.scaffold_dest_together))
        }
        TopDestination.Repos -> if (reposState != null) {
            ReposPane(
                state = reposState,
                secretsStore = secretsStore,
                onOpenTogether = { onSelectDest(TopDestination.Together) },
                onOpenWizard = { onSelectDest(TopDestination.Wizard) },
                onOpenAppSettings = { onSelectDest(TopDestination.Settings) },
                // Round 2.7.D.2-UI — banner inputs forwarded via SettingsAccess
                // because that's the only narrow surface that already carries
                // RepoStoragePrefs + NotificationPrefs into the shell.
                repoStoragePrefs = settingsAccess.repoStoragePrefs,
                notificationPrefs = settingsAccess.notificationPrefs,
                onPickBackupFolder = settingsAccess.onPickBackupFolder,
                demoModePrefs = settingsAccess.demoModePrefs,
                onPickInternalStorage = onPickInternalStorage,
                safPermissionRevoked = settingsAccess.safPermissionRevokedFlow
                    ?.collectAsState()?.value == true,
            )
        } else {
            PlaceholderScreen(stringResource(R.string.scaffold_dest_repos))
        }
        TopDestination.Wizard -> WizardNavHost(
            onFinish = {
                onWizardFinish()
                wizardEntryRequest?.value = null
                onSelectDest(TopDestination.Schedule)
            },
            onCancel = {
                wizardEntryRequest?.value = null
                onSelectDest(TopDestination.Schedule)
            },
            onScaffold = onWizardScaffold,
            neutralMode = neutralMode,
            initialScreen = wizardEntry
                ?: com.eight87.strictlykeptboy.ui.wizard.WizardScreen.Welcome,
            onShareWithDom = onShareWithDom,
            // Round 2.17.D — storage step wiring.
            repoStoragePrefs = settingsAccess.repoStoragePrefs,
            onPickExternalStorage = settingsAccess.onPickBackupFolder,
            onPickInternalStorage = onPickInternalStorage,
            // Round 2.18 Phase I — default-calendar-app
            // onboarding card backing store.
            systemCalendarPrefs = settingsAccess.systemCalendarPrefs,
        )
        TopDestination.Reviews -> {
            // Round 2.23 Phase E (D-2.23.e) — Reviews destination now
            // renders live items from `ReviewFeedReader` when the host
            // wires `settingsAccess.reviewItemsFlow`. Falls back to the
            // Phase DDD.13 empty-state card otherwise.
            val identityState = settingsAccess.identityPrefs
                ?.state?.collectAsState()?.value
            val items = settingsAccess.reviewItemsFlow
                ?.collectAsState()?.value
                ?: emptyList()
            com.eight87.strictlykeptboy.ui.reviews.ReviewsPane(
                side = com.eight87.strictlykeptboy.ui.reviews.ReviewsSide.Boy,
                items = items,
                boyHonorific = identityState?.honorific?.ifBlank { "Sir" } ?: "Sir",
                boyPraiseTerm = identityState?.praise?.ifBlank { "good boy" } ?: "good boy",
                filter = reviewsFilter,
            )
        }
        TopDestination.Settings -> SettingsPane(
            importExportState = importExportState,
            onPickImportFile = onPickImportFile,
            onPickExportFile = onPickExportFile,
            access = settingsAccess.copy(
                // Phase CCC.10 — Settings → Lifestyle → Plan a trip.
                onPlanTrip = onPlanTrip,
            ),
        )
    }
}

// Wired ambient — read by deeper composables. Kept for parity with the
// previous `AppScaffold.kt` marker.
@Suppress("unused")
private val widthClassMarker: Any = LocalWindowWidthSizeClass
