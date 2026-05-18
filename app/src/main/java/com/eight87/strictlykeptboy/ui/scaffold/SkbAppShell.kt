package com.eight87.strictlykeptboy.ui.scaffold

import androidx.activity.compose.BackHandler
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
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.ProvideWindowSizeClass
import com.eight87.strictlykeptboy.ui.tasks.TasksFilter
import com.eight87.strictlykeptboy.ui.trip.TripWizardNavHost
import kotlinx.coroutines.flow.MutableStateFlow

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
 * owns the shell's composition root only. Top-bar chrome lives in
 * `SkbTopBar.kt`; the left rail lives in `SkbScheduleRail.kt`; the
 * bottom-anchored Now-Playing sheet host lives in
 * `NowPlayingSheetHost.kt`. The destination-dispatch `when` lives
 * in `SkbAppDestinationContent.kt` (Round 2.28 SOLID fix #8c).
 *
 * Round 2.28 SOLID fix #9: the previous ~50-param flat signature is
 * grouped into [ShellContext] (services / providers), [ShellCallbacks]
 * (actions), and [ShellSelections] (parent-owned mode / selection
 * state). The shell now takes three grouped params instead of fifty
 * individual ones. Behaviour is byte-identical at every call site.
 *
 * SOLID notes:
 *  - **S/I:** the shell owns *only* destination dispatch + the visual
 *    composition. Each pane still owns its own state. The rail is
 *    parameterised by a [RailItem] list; the shell does not know
 *    about Day/Week/etc. constants.
 *  - **O:** new destinations join [TopDestination]; the `when` in
 *    [SkbAppDestinationContent] is exhaustive.
 *  - **D:** ViewModels and stores are passed in as before; this shell
 *    is the same composition surface as the old [AppScaffold].
 */

const val TestTagAppShell = "SkbAppShell"
const val TestTagShellTopBar = "ShellTopBar"
const val TestTagShellRail = "ShellRail"
const val TestTagEditScheduleRailButton = "EditScheduleRailButton"
const val TestTagEditScheduleScreen = "EditScheduleScreen"
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

/**
 * Round 2.28 / SOLID fix #9 — grouped-signature entry point. See
 * [ShellContext], [ShellCallbacks], [ShellSelections] for the three
 * argument bundles. Tests / previews that only need the bare minimum
 * pass `SkbAppShell(ShellContext(activeRepoNameFlow = …, scheduleState
 * = …, tasksState = TasksViewState()))` and let all other defaults
 * carry the rest.
 */
@Composable
fun SkbAppShell(
    context: ShellContext,
    callbacks: ShellCallbacks = ShellCallbacks(),
    selections: ShellSelections = ShellSelections(),
    modifier: Modifier = Modifier,
) {
    ProvideWindowSizeClass(modifier = modifier) { _ ->
        SkbAppShellContent(
            context = context,
            callbacks = callbacks,
            selections = selections,
        )
    }
}

@Composable
private fun SkbAppShellContent(
    context: ShellContext,
    callbacks: ShellCallbacks,
    selections: ShellSelections,
) {
    var selected by rememberSaveable { mutableStateOf(TopDestination.Schedule) }
    // Phase 2.1.I.2 — observe wizard re-entry requests.
    val wizardEntry = selections.wizardEntryRequest?.collectAsState()?.value
    androidx.compose.runtime.LaunchedEffect(wizardEntry) {
        if (wizardEntry != null) {
            selected = TopDestination.Wizard
        }
    }
    val activeRepoName by context.activeRepoNameFlow.collectAsState()
    // D.88 / F48 — top-bar avatar reflects the active repo's iconKind. Defaults
    // to Sticker("bat") if the caller hasn't wired the flow (e.g. tests, previews).
    val activeIconKind by (context.activeIconKindFlow
        ?: MutableStateFlow(com.eight87.strictlykeptboy.ui.theming.RepoIconKind.Sticker("bat") as com.eight87.strictlykeptboy.ui.theming.RepoIconKind))
        .collectAsState()

    val scheduleTab by context.scheduleState.selectedTab.collectAsState()

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
                    context.scheduleState.setSelectedTab(tab)
                    callbacks.onPersistTab(tab)
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
    // Full-screen Edit Schedule overlay — Monday-anchored agenda list.
    // Pen icon in the rail bottom slot opens it.
    var editScheduleOpen by rememberSaveable { mutableStateOf(false) }
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
        source = context.taskPlaybackSource,
        tasksState = context.tasksState,
        onWriteTask = callbacks.onWriteTask,
        onStartTask = callbacks.onStartTask,
        nowNextFlow = context.nowNextFlow,
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
                onSyncClick = callbacks.onSyncClick,
                onIdentityClick = { /* UI-L — stubbed */ },
                // D.88: bat avatar IS the repo affordance. Tap navigates to
                // the Repos destination. The Repos `ShellDest-` button stays
                // in the row to satisfy `AppShellNavigationSwapTest`; the
                // avatar is a parallel affordance per user direction.
                onRepoSwitcherClick = { selected = TopDestination.Repos },
                onSettingsTap = { selected = TopDestination.Settings },
                modePrefs = context.settingsAccess.modePrefs,
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
                        context.scheduleState.calendarsFlow
                    } else null
                    RailColumn(
                        items = railItems,
                        activeIconKind = activeIconKind,
                        onAccountTap = { selected = TopDestination.Repos },
                        onSettingsTap = { selected = TopDestination.Settings },
                        overlayPickerCalendars = pickerCalendars,
                        overlayPickerPrefs = context.calendarVisibility,
                        onOverlayPickerClick = { overlayPickerOpen = true },
                        onEditScheduleClick = if (selected == TopDestination.Schedule) {
                            { editScheduleOpen = true }
                        } else null,
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().testTag(TestTagShellContent),
                ) {
                    SkbAppDestinationContent(
                        reviewsFilter = reviewsFilter,
                        tasksFilter = tasksFilter,
                        tasksState = context.tasksState,
                        selected = selected,
                        activeRepoName = activeRepoName,
                        scheduleState = context.scheduleState,
                        onSyncClick = callbacks.onSyncClick,
                        eventCreateController = context.eventCreateController,
                        onPlanTrip = { tripWizardOpen = true },
                        calendarVisibility = context.calendarVisibility,
                        onLongPressCalendar = callbacks.onLongPressCalendar,
                        togetherViewModel = context.togetherViewModel,
                        neutralMode = selections.neutralMode,
                        reposState = context.reposState,
                        secretsStore = context.secretsStore,
                        settingsAccess = context.settingsAccess,
                        onSelectDest = { selected = it },
                        onPickInternalStorage = callbacks.onPickInternalStorage,
                        onWizardScaffold = callbacks.onWizardScaffold,
                        onWizardFinish = callbacks.onWizardFinish,
                        onWizardFinished = callbacks.onWizardFinished,
                        wizardEntry = wizardEntry,
                        onShareWithDom = callbacks.onShareWithDom,
                        importExportState = context.importExportState,
                        onPickImportFile = callbacks.onPickImportFile,
                        onPickExportFile = callbacks.onPickExportFile,
                        onSingleDrop = callbacks.onSingleDrop,
                        onRecurringDrop = callbacks.onRecurringDrop,
                        onOpenEventDetailFullScreen = { pendingEventDetail = it },
                        onPromptRespond = callbacks.onPromptRespond,
                        onPromptMarkAnsweredOffline = callbacks.onPromptMarkAnsweredOffline,
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
                                onMaterialize = callbacks.onTripMaterialize,
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
        val calsFlow = context.scheduleState.calendarsFlow
        if (overlayPickerOpen && context.calendarVisibility != null && calsFlow != null) {
            BackHandler { overlayPickerOpen = false }
            // Round 2.23.5 / Fix 3 — resolve repo GUID → friendly
            // display name via the live RepoStore flow (already plumbed
            // through `reposState`). Recomputed on each repo-list change.
            val reposList = context.reposState?.repos?.collectAsState()?.value.orEmpty()
            val repoNameById = remember(reposList) {
                reposList.associate { it.repoId to it.displayName }
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                com.eight87.strictlykeptboy.ui.calendars.OverlayPickerScreen(
                    calendarsFlow = calsFlow,
                    visibilityPrefs = context.calendarVisibility,
                    onBack = { overlayPickerOpen = false },
                    onEditCalendar = { meta ->
                        callbacks.onLongPressCalendar?.invoke(meta)
                    },
                    onPriorityChange = callbacks.onOverlayPriorityChange,
                    onColorChange = callbacks.onOverlayColorChange,
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
            BackHandler { pendingEventDetail = null }
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
        if (editScheduleOpen) {
            BackHandler { editScheduleOpen = false }
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize().testTag(TestTagEditScheduleScreen),
            ) {
                com.eight87.strictlykeptboy.ui.schedule.EditScheduleScreen(
                    scheduleState = context.scheduleState,
                    onBack = { editScheduleOpen = false },
                    onBandTap = { band -> pendingEventDetail = band },
                    eventCreateController = context.eventCreateController,
                )
            }
        }
        // Full-screen event-create surface — hoisted from SchedulePane
        // so it covers the rail + top-bar. Same outer-Box mount pattern
        // as OverlayPickerScreen / pendingEventDetail above.
        context.eventCreateController?.let { controller ->
            val sheetOpen by controller.sheetOpen.collectAsState()
            if (sheetOpen) {
                BackHandler { controller.closeSheet() }
                val sheetState by controller.state.collectAsState()
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    com.eight87.strictlykeptboy.ui.schedule.EventCreateSheet(
                        state = sheetState,
                        onDismiss = { controller.closeSheet() },
                        onTabChange = controller::setTab,
                        onDraftChange = controller::setDraft,
                        onConfirmFreeForm = controller::confirmFreeForm,
                        onPickTemplate = controller::pickTemplate,
                        onConfirmTemplate = controller::confirmTemplate,
                        onCancelTemplate = controller::cancelTemplate,
                        onOverlapScheduleAnyway = controller::overlapScheduleAnyway,
                        onOverlapPickDifferent = controller::overlapPickDifferent,
                        onOverlapCancel = controller::overlapCancel,
                    )
                }
            }
        }
      }  // end outer Box
    }
    }  // end NowPlayingSheetHost
}

// Wired ambient — read by deeper composables. Kept for parity with the
// previous `AppScaffold.kt` marker.
@Suppress("unused")
private val widthClassMarker: Any = LocalWindowWidthSizeClass
