package com.eight87.strictlykeptboy.ui.scaffold

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.task.StubTaskPlaybackSource
import com.eight87.strictlykeptboy.task.TaskNowPlayingState
import com.eight87.strictlykeptboy.task.TaskQueueCommands
import com.eight87.strictlykeptboy.task.TaskTransportCommands
import com.eight87.strictlykeptboy.ui.playing.ExpandedNowPlayingTaskBody
import com.eight87.strictlykeptboy.ui.playing.MiniPlayer
import com.eight87.strictlykeptboy.ui.playing.NowPlayingScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.ProvideWindowSizeClass
import com.eight87.strictlykeptboy.ui.components.IdentityAvatar
import com.eight87.strictlykeptboy.ui.components.RepoSwitcherChip
import com.eight87.strictlykeptboy.ui.components.SyncButton
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import com.eight87.strictlykeptboy.ui.repos.ReposPane
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.schedule.SchedulePane
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.settings.SettingsAccess
import com.eight87.strictlykeptboy.ui.settings.SettingsPane
import com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest
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
 * Previously [AppScaffold] used `NavigationSuiteScaffold` which put
 * destinations on a left rail and per-pane view-mode tabs in the top
 * bar. The intent (see tonearmboy's library scaffold + the user's
 * polish-pass note) is inverted:
 *
 *   - **LEFT vertical rail** = per-pane view-mode tabs (Schedule:
 *     Day/Week/Month/Agenda/Year; Tasks: Combined/Today/Per-list/
 *     Shopping/Standing). Rotated text labels in a narrow 72dp rail
 *     (same shape as `tonearmboy`'s `LibraryRail`).
 *   - **TOP-RIGHT** = cross-content destination buttons
 *     (Schedule / Tasks / Together / Repos / Wizard / Settings).
 *   - **TOP-LEFT** = repo switcher chip (unchanged).
 *   - **TOP rightmost** = sync button + identity avatar (unchanged).
 *
 * SOLID notes:
 *  - **S/I:** the shell owns *only* destination dispatch + the visual
 *    chrome (top bar + left rail). Each pane still owns its own
 *    state. The rail is parameterised by a [RailItem] list; the
 *    shell does not know about Day/Week/etc. constants.
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
    // Round 2.16.E — `Tasks` destination deleted. All todolist surface
    // area now lives inside the expanded NowPlayingScreen sheet
    // (reachable via the Schedule "Open tasks" FAB or the mini-player
    // peek when a task is active).
    Together("Together", Icons.Filled.Groups),
    Repos("Repos", Icons.Filled.Folder),
    Wizard("Wizard", Icons.Filled.AutoAwesome),
    // Phase DDD.13 / UI-SS — dom-/boy-side review feed surface.
    Reviews("Reviews", Icons.Filled.RateReview),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * Narrow data interface for a left-rail entry (R.X.1 / R.X.7).
 *
 * The shell renders these with rotated text labels, à la tonearmboy's
 * `LibraryRail` — see `RailColumn`. Panes that have no view-mode
 * sub-navigation simply return an empty list and the rail collapses.
 *
 * @param key stable identity for testTag composition + `rememberSaveable`
 *            round-tripping (must be ASCII-safe).
 * @param labelRes the localised label, fed to `stringResource`.
 * @param selected true if this is the currently-active rail entry.
 * @param onClick fired when the user taps this entry.
 */
data class RailItem(
    val key: String,
    @StringRes val labelRes: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

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
     * [CalendarFilterChipStrip] above SchedulePane. Null suppresses the
     * strip (previews / tests).
     */
    calendarVisibility: com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs? = null,
    /**
     * Round 2.1.B.2 / B.4 — long-press handler for calendar chips.
     * Host opens [com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsSheet].
     */
    onLongPressCalendar: ((com.eight87.strictlykeptboy.resolver.CalendarMeta) -> Unit)? = null,
    /**
     * Round 2.16.B — task-playback source feeding MiniPlayer +
     * NowPlayingScreen. Defaults to the Phase A stub for previews /
     * tests; MainActivity wires `appGraph.taskTransport`.
     */
    taskPlaybackSource: Any = StubTaskPlaybackSource,
    /**
     * Round 2.16.B — temporary "Start" affordance handler exposed on
     * task rows. TODO Phase D — replace with proper start-from-mini-
     * player flow inside the expanded sheet.
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
            taskPlaybackSource = taskPlaybackSource,
            onStartTask = onStartTask,
            onPickInternalStorage = onPickInternalStorage,
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
    taskPlaybackSource: Any = StubTaskPlaybackSource,
    onStartTask: ((String) -> Unit)? = null,
    /** Round 2.17.D — see [SkbAppShell.onPickInternalStorage]. */
    onPickInternalStorage: () -> Unit = {},
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

    // Round 2.16.E — `tasksTab` removed along with the Tasks destination.
    // Task view-mode selection now lives inside ExpandedNowPlayingTaskBody.

    val scheduleTab by scheduleState.selectedTab.collectAsState()

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
        TopDestination.Together,
        TopDestination.Repos,
        TopDestination.Wizard,
        TopDestination.Reviews,
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

    NowPlayingSheetHost(
        source = taskPlaybackSource,
        tasksState = tasksState,
        onWriteTask = onWriteTask,
        onStartTask = onStartTask,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().testTag(TestTagAppShell),
    ) {
        // Round 2.21 Phase C — overlay picker overlay state. When true, the
        // full-screen [OverlayPickerScreen] sits on top of whatever the
        // active destination is (back navigates to it).
        var overlayPickerOpen by rememberSaveable { mutableStateOf(false) }
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
                // Round 2.21 Phase C.1 — overlay-picker icon, only shown on
                // the Schedule destination + only when wiring is present.
                overlayPickerCalendars =
                    if (selected == TopDestination.Schedule) scheduleState.calendarsFlow else null,
                overlayPickerPrefs = calendarVisibility,
                onOverlayPickerClick = { overlayPickerOpen = true },
            )
            Row(modifier = Modifier.fillMaxSize()) {
                // Left rail only renders when the destination has view-mode
                // tabs to show. Settings/Repos/Wizard have no view-modes so
                // the rail collapses and the pane spans edge-to-edge (user
                // direction 2026-05-13 — "still space on the left").
                if (railItems.isNotEmpty()) {
                    RailColumn(
                        items = railItems,
                        activeIconKind = activeIconKind,
                        onAccountTap = { selected = TopDestination.Repos },
                        onSettingsTap = { selected = TopDestination.Settings },
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().testTag(TestTagShellContent),
                ) {
                    when (selected) {
                        TopDestination.Schedule -> SchedulePane(
                            activeRepoName = activeRepoName,
                            state = scheduleState,
                            onSyncClick = onSyncClick,
                            eventCreateController = eventCreateController,
                            onPlanTrip = { tripWizardOpen = true },
                            calendarVisibility = calendarVisibility,
                            onLongPressCalendar = onLongPressCalendar,
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
                                onOpenTogether = { selected = TopDestination.Together },
                                onOpenWizard = { selected = TopDestination.Wizard },
                                onOpenAppSettings = { selected = TopDestination.Settings },
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
                                selected = TopDestination.Schedule
                            },
                            onCancel = {
                                wizardEntryRequest?.value = null
                                selected = TopDestination.Schedule
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
                            // Phase DDD.13 wiring (F45 follow-up). Items list is
                            // empty until the ReviewFeedReader counterpart of
                            // `ReviewFeedWriter` lands; the empty-state card
                            // covers the boy/dom-side messaging meanwhile.
                            val identityState = settingsAccess.identityPrefs
                                ?.state?.collectAsState()?.value
                            com.eight87.strictlykeptboy.ui.reviews.ReviewsPane(
                                side = com.eight87.strictlykeptboy.ui.reviews.ReviewsSide.Boy,
                                items = emptyList(),
                                boyHonorific = identityState?.honorific?.ifBlank { "Sir" } ?: "Sir",
                                boyPraiseTerm = identityState?.praise?.ifBlank { "good boy" } ?: "good boy",
                            )
                        }
                        TopDestination.Settings -> SettingsPane(
                            importExportState = importExportState,
                            onPickImportFile = onPickImportFile,
                            onPickExportFile = onPickExportFile,
                            access = settingsAccess.copy(
                                // Phase CCC.10 — Settings → Lifestyle → Plan a trip.
                                onPlanTrip = { tripWizardOpen = true },
                            ),
                        )
                    }
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
                    // Round 2.21 Phase C.2 — full-screen overlay picker.
                    // Mounted above the active pane; back navigates to it.
                    val calsFlow = scheduleState.calendarsFlow
                    if (overlayPickerOpen && calendarVisibility != null && calsFlow != null) {
                        com.eight87.strictlykeptboy.ui.calendars.OverlayPickerScreen(
                            calendarsFlow = calsFlow,
                            visibilityPrefs = calendarVisibility,
                            onBack = { overlayPickerOpen = false },
                            onEditCalendar = { meta ->
                                onLongPressCalendar?.invoke(meta)
                            },
                        )
                    }
                }
            }
        }
    }
    }  // end NowPlayingSheetHost
}

@Composable
private fun ShellTopBar(
    activeRepoName: String,
    activeIconKind: com.eight87.strictlykeptboy.ui.theming.RepoIconKind,
    title: String,
    selectedDest: TopDestination,
    onSelectDest: (TopDestination) -> Unit,
    onSyncClick: () -> Unit,
    onIdentityClick: () -> Unit,
    onRepoSwitcherClick: () -> Unit,
    /**
     * Round 2.16.F — global app-settings cog moved back into the top-bar
     * action row, immediately before the avatar. The Repos pane's
     * top-bar cog (Round 2.4 migration) is removed; per-repo settings
     * still open via row-tap on a repo inside Repos.
     */
    onSettingsTap: () -> Unit,
    modePrefs: com.eight87.strictlykeptboy.ui.settings.ModePrefs? = null,
    /**
     * Round 2.21 Phase C.1 — when non-null + prefs non-null, the
     * overlay-picker icon button renders just before the settings cog.
     * Host opens [com.eight87.strictlykeptboy.ui.calendars.OverlayPickerScreen]
     * on tap.
     */
    overlayPickerCalendars:
        StateFlow<List<com.eight87.strictlykeptboy.resolver.CalendarMeta>>? = null,
    overlayPickerPrefs: com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs? = null,
    onOverlayPickerClick: () -> Unit = {},
) {
    // enableEdgeToEdge() is on in MainActivity — content draws under the
    // status bar by default. Push the top-bar Surface down past the system
    // status + display-cutout inset so the repo chip + destination buttons
    // get the breathing room tonearmboy gets for free via its Scaffold.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .testTag(TestTagShellTopBar),
    ) {
        // Round 2.3.A.1 — single-row top bar. Destination icon-buttons
        // (read surfaces only: Schedule / Tasks / Reviews) are inlined
        // into the action row to the right of the title, alongside the
        // bat avatar. Mode + sync are no longer global concerns — they
        // moved into ReposPane as per-repo state (Round 2.3.A.2 / .A.3).
        // The `modePrefs` + `onSyncClick` params remain on the function
        // signature (null-allowed) to avoid breaking call-sites, but
        // they no longer render anything here.
        // Round 2.16.E — Tasks removed from the top-bar icon row (the
        // destination is gone; todolist UI lives in the expanded
        // NowPlayingScreen sheet now).
        val topBarDestinations = listOf(
            TopDestination.Schedule,
            TopDestination.Reviews,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            topBarDestinations.forEach { dest ->
                DestinationButton(
                    dest = dest,
                    selected = dest == selectedDest,
                    onClick = { onSelectDest(dest) },
                )
            }
            // Round 2.21 Phase C.1 — overlay-picker icon (only when the
            // Schedule destination is active + wiring is present).
            if (overlayPickerCalendars != null && overlayPickerPrefs != null) {
                com.eight87.strictlykeptboy.ui.calendars.OverlayPickerButton(
                    calendarsFlow = overlayPickerCalendars,
                    visibilityPrefs = overlayPickerPrefs,
                    onClick = onOverlayPickerClick,
                )
            }
            // Round 2.16.F — global app-settings cog, immediately before
            // the avatar (pre-Round-2.1 location). Tapping selects
            // `TopDestination.Settings`. Per-repo settings still open from
            // inside the Repos pane (row-tap).
            androidx.compose.material3.IconButton(
                onClick = onSettingsTap,
                modifier = Modifier.testTag(TestTagShellSettingsCog),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            IdentityAvatar(
                onClick = onRepoSwitcherClick,
                iconKind = activeIconKind,
                sizeDp = 40,
            )
            // Keep params referenced so an accidental removal of either
            // ModePill/SyncButton call-site doesn't silently lose meaning.
            @Suppress("UNUSED_EXPRESSION") modePrefs
            @Suppress("UNUSED_EXPRESSION") onSyncClick
        }
    }
}

// Tiny shim — `Modifier.horizontalScroll` is a Modifier-extension in the
// foundation package. Re-exported here under an unambiguous name so the
// destination-row Modifier chain stays readable.
private fun Modifier.androidx_horizontalScroll(
    state: androidx.compose.foundation.ScrollState,
): Modifier = this.horizontalScroll(state)

@Composable
private fun RepoSwitcherIconButton(
    activeRepoName: String,
    onClick: () -> Unit,
) {
    // Compact icon-only repo switcher — active repo name carried by
    // contentDescription for a11y + tooltip. Folder glyph mirrors the
    // Repos destination icon so the visual language stays consistent.
    Box(
        modifier = Modifier
            .testTag("ShellRepoSwitcher")
            .semantics { contentDescription = "Repo: $activeRepoName" }
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun DestinationButton(
    dest: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Tonearmboy-shape: standard M3 IconButton (40dp circular hit target +
    // circular ripple). Selected destination renders as FilledTonalIconButton
    // so the active tab reads as a tinted circular pill — same visual
    // language as M3 NavigationBar / NavigationRail selected items.
    val label = dest.labelString()
    val tag = "$TestTagShellDestPrefix${dest.name}"
    val mod = Modifier
        .testTag(tag)
        .semantics { contentDescription = label }
    if (selected) {
        androidx.compose.material3.FilledTonalIconButton(onClick = onClick, modifier = mod) {
            Icon(
                imageVector = dest.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = mod) {
            Icon(
                imageVector = dest.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Vertical left rail — rotated text labels per [items]. 72dp wide on
 * all width classes for v1; future polish may widen on Expanded.
 *
 * Mirrors `tonearmboy.ui.library.LibraryRail` in spirit: rotated -90°
 * text inside a fixed-size Box; selected item draws a 2dp accent
 * stripe on its right edge. Scrolls vertically if items don't fit.
 */
@Composable
private fun RailColumn(
    items: List<RailItem>,
    activeIconKind: com.eight87.strictlykeptboy.ui.theming.RepoIconKind,
    onAccountTap: () -> Unit,
    onSettingsTap: () -> Unit,
) {
    // Match tonearmboy's LibraryRail: 52dp wide, 108dp per item.
    // Bottom of the rail carries the active-repo avatar + a settings gear
    // (tonearmboy parity: gear lives at the bottom-left of the rail).
    val railWidth = 52.dp
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .requiredWidth(railWidth)
            .background(MaterialTheme.colorScheme.surface)
            .testTag(TestTagShellRail),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // TOP: view-mode tabs (scrollable if many).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    RailTabItem(item = item)
                }
            }
            // Rail bottom intentionally empty per user direction 2026-05-13 —
            // settings gear moved up to the top-bar action row, bat avatar
            // already at top-right.
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RailTabItem(item: RailItem) {
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = if (item.selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = stringResource(item.labelRes)
    val tag = "$TestTagShellRailItemPrefix${item.key}"

    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 108.dp)
            .clickable(onClick = item.onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .rotate(-90f),
        )
        if (item.selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(accent)
                    .clip(RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.scaffold_placeholder_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// Wired ambient — read by deeper composables. Kept for parity with the
// previous `AppScaffold.kt` marker.
@Suppress("unused")
private val widthClassMarker: Any = LocalWindowWidthSizeClass

// ---- Label-resolver helpers ------------------------------------------------
//
// We keep the shell decoupled from `ui.a11y.EnumLabels` (which depends on
// the Compose runtime to produce a String). For the rail we only need the
// `@StringRes Int` so we resolve it locally — same mapping, no coupling.

@StringRes
private fun scheduleTabLabelRes(tab: ScheduleViewTab): Int = when (tab) {
    ScheduleViewTab.Day -> R.string.schedule_view_tab_day
    ScheduleViewTab.Week -> R.string.schedule_view_tab_week
    ScheduleViewTab.Month -> R.string.schedule_view_tab_month
    ScheduleViewTab.Agenda -> R.string.schedule_view_tab_agenda
    ScheduleViewTab.Year -> R.string.schedule_view_tab_year
}

/**
 * Round 2.16.A — verbatim port of tonearmboy `TonearmboyApp.kt` lines
 * 140-471 (the sheet-host block). Wraps a [content] layer (the app's
 * existing chrome) with a bottom-anchored sheet that hosts the
 * [MiniPlayer] at peek and [NowPlayingScreen] at fully-expanded.
 *
 * Matches tonearmboy verbatim:
 *  - peek = 118 dp
 *  - flick threshold = 0.05f (5% of sheet travel)
 *  - staggered crossfade: mini visible 0..0.5, full visible 0.5..1
 *  - nested-scroll connection drains queue overscroll → sheet progress
 *  - drag-start progress captured for direction-based flick commit
 *
 * Phase A reads from [StubTaskPlaybackSource]; Phase B replaces with
 * the real projector.
 */
@Composable
private fun NowPlayingSheetHost(
    source: Any = StubTaskPlaybackSource,
    tasksState: TasksViewState = remember { TasksViewState() },
    onWriteTask: (TaskQuickAddRequest) -> Unit = {},
    onStartTask: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Round 2.16.B — the source is one object satisfying the three
    // facets the ported composables consume. Phase A used the singleton
    // [StubTaskPlaybackSource]; Phase B injects the real
    // `TaskTransportAdapter` from AppGraph (or anything else
    // satisfying the union of the three interfaces).
    val now = source as TaskNowPlayingState
    val transport = source as TaskTransportCommands
    val queue = source as TaskQueueCommands
    val playbackState by now.state.collectAsState()
    // Round 2.16.D — task detail / quick-add overlays migrated here
    // from TasksPane so they layer above the sheet per tonearmboy's
    // overlay convention.
    var openTask by remember {
        mutableStateOf<com.eight87.strictlykeptboy.ui.tasks.TaskItem?>(null)
    }
    var quickAddOpen by remember { mutableStateOf(false) }
    val tasksUi by tasksState.state.collectAsState()

    val sheetProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val nowPlayingListState = rememberLazyListState()

    val openNowPlayingSheet: () -> Unit = remember {
        { coroutineScope.launch { sheetProgress.animateTo(1f) }; Unit }
    }
    val closeSheet: () -> Unit = remember {
        { coroutineScope.launch { sheetProgress.animateTo(0f) }; Unit }
    }

    // Round 2.16 post-DONE — peek is always visible on Schedule. When no
    // task is active, the MiniPlayer renders an empty-state row (checklist
    // icon + "No active task / Tap to pick one") that opens the sheet on
    // tap. This obsoletes the D.7 stacked Tasks FAB.
    val showMiniPlayer = true

    BackHandler(enabled = sheetProgress.value > 0f) {
        closeSheet()
    }

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeightDp.toPx() }.coerceAtLeast(1f)
    Box(modifier = Modifier.fillMaxSize()) {
        val peekDp = 118.dp
        val peekPx = with(density) { peekDp.toPx() }
        val effectivePeekPx = if (showMiniPlayer) peekPx else 0f

        val progress = sheetProgress.value
        val miniAlpha = (1f - kotlin.math.min(progress * 2f, 1f)).coerceIn(0f, 1f)
        val nowPlayingAlpha = (kotlin.math.max(progress - 0.5f, 0f) * 2f).coerceIn(0f, 1f)

        val dragStartProgress = remember { mutableStateOf<Float?>(null) }
        val onSheetDragDelta: (Float) -> Unit = { delta ->
            coroutineScope.launch {
                if (dragStartProgress.value == null) {
                    dragStartProgress.value = sheetProgress.value
                }
                val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                val next = (sheetProgress.value - delta / travel).coerceIn(0f, 1f)
                sheetProgress.snapTo(next)
            }
        }
        val onSheetDragSettle: () -> Unit = {
            coroutineScope.launch {
                val start = dragStartProgress.value ?: 0f
                val end = sheetProgress.value
                // Round 2.16.G — flick-commit math factored to
                // [flickCommitTarget] so it can be unit-tested without
                // standing up the full draggable + nested-scroll host.
                val target = flickCommitTarget(start = start, end = end)
                sheetProgress.animateTo(target)
                dragStartProgress.value = null
            }
        }

        // ---- Layer 1: existing app chrome (with bottom inset = peek). ----
        val libraryBottomPad = if (showMiniPlayer) peekDp else 0.dp
        Box(modifier = Modifier.fillMaxSize().padding(bottom = libraryBottomPad)) {
            content()
        }

        // ---- Layer 2: bottom-anchored sheet (Auxio-style). ----
        // Round 2.16.D.7 — the sheet container is always rendered so the
        // D.7 FAB can animate it open even with no active task. The peek
        // (mini-player) still only renders when `hasMedia` is true; an
        // unopened sheet with no media has effectivePeekPx=0 and progress
        // 0 → sheetHeight 0, so nothing is visible.
        run {
            val sheetHeightPx = effectivePeekPx + progress * (screenHeightPx - effectivePeekPx)
            val sheetHeightDp = with(density) { sheetHeightPx.toDp() }

            val nestedDragDirection = remember { mutableStateOf(0) }
            val sheetNestedScroll = remember(screenHeightPx, effectivePeekPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput)
                            return Offset.Zero
                        if (available.y < 0f && sheetProgress.value < 1f) {
                            val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                            val delta = -available.y / travel
                            nestedDragDirection.value = -1
                            coroutineScope.launch {
                                sheetProgress.snapTo((sheetProgress.value + delta).coerceAtMost(1f))
                            }
                            return Offset(0f, available.y)
                        }
                        if (available.y > 0f &&
                            nowPlayingListState.firstVisibleItemIndex == 0 &&
                            nowPlayingListState.firstVisibleItemScrollOffset == 0
                        ) {
                            val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                            val delta = available.y / travel
                            nestedDragDirection.value = 1
                            coroutineScope.launch {
                                sheetProgress.snapTo((sheetProgress.value - delta).coerceAtLeast(0f))
                            }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput)
                            return Offset.Zero
                        if (available.y > 0f) {
                            val travel = (screenHeightPx - effectivePeekPx).coerceAtLeast(1f)
                            val delta = available.y / travel
                            nestedDragDirection.value = 1
                            coroutineScope.launch {
                                sheetProgress.snapTo((sheetProgress.value - delta).coerceAtLeast(0f))
                            }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(
                        available: Velocity,
                    ): Velocity {
                        val dir = nestedDragDirection.value
                        val target = when {
                            dir > 0 -> 0f
                            dir < 0 -> 1f
                            else -> if (sheetProgress.value >= 0.5f) 1f else 0f
                        }
                        sheetProgress.animateTo(target)
                        nestedDragDirection.value = 0
                        return Velocity.Zero
                    }
                }
            }

            val sheetDraggable = rememberDraggableState { delta ->
                onSheetDragDelta(delta)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeightDp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clipToBounds()
                    .nestedScroll(sheetNestedScroll)
                    .draggable(
                        state = sheetDraggable,
                        orientation = Orientation.Vertical,
                        onDragStopped = { onSheetDragSettle() },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(screenHeightDp),
                ) {
                    if (progress > 0.45f) Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(nowPlayingAlpha),
                    ) {
                        NowPlayingScreen(
                            nowPlayingState = now,
                            transport = transport,
                            queueCommands = queue,
                            onBack = closeSheet,
                            nowPlayingListState = nowPlayingListState,
                            // Round 2.16.D — replace the music queue with
                            // the task views: chip-strip + selected
                            // Combined/Today/Per-list/Standing/Shopping.
                            // The body is hoisted as a LazyItemScope-
                            // scoped slot so it can claim viewport height
                            // when needed.
                            showHeroCard = playbackState.hasMedia,
                            bodyContent = {
                                ExpandedNowPlayingTaskBody(
                                    tasksState = tasksState,
                                    onOpenTask = { task -> openTask = task },
                                    onStartTask = onStartTask,
                                    onLongPressTask = { /* Phase D — TBD */ },
                                    bodyHeight = if (playbackState.hasMedia) {
                                        // Hero + transport ~ 480 dp; leave
                                        // most of the rest of the viewport
                                        // to the task body.
                                        (screenHeightDp - 560.dp).coerceAtLeast(240.dp)
                                    } else {
                                        // No hero → task body fills the
                                        // whole viewport minus top app bar.
                                        (screenHeightDp - 120.dp).coerceAtLeast(360.dp)
                                    },
                                )
                            },
                        )
                    }

                    // Round 2.16.D.2 — TaskQuickAdd FAB anchored bottom-end
                    // of the expanded sheet. Tapping does NOT collapse the
                    // sheet (we drive only the quick-add overlay flag).
                    // Visible alpha follows the expanded-sheet crossfade so
                    // it fades in with NowPlayingScreen.
                    if (progress > 0.45f) Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .alpha(nowPlayingAlpha),
                    ) {
                        com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddFab(
                            onClick = { quickAddOpen = true },
                        )
                    }

                    if (showMiniPlayer) Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(peekDp)
                            .alpha(miniAlpha),
                    ) {
                        MiniPlayer(
                            state = playbackState,
                            onTogglePlayPause = transport::togglePlayPause,
                            onClose = transport::stop,
                            onExpand = openNowPlayingSheet,
                            onSkipNext = transport::seekToNext,
                            onSkipPrevious = transport::seekToPrevious,
                            onPlayButtonLongPress = { transport.stop() },
                            onToggleShuffle = transport::toggleShuffle,
                            onCycleRepeat = transport::cycleRepeatMode,
                            onSeekTo = transport::seekTo,
                            onSheetDragDelta = onSheetDragDelta,
                            onSheetDragSettle = onSheetDragSettle,
                        )
                    }
                }
            }
        }

        // Round 2.16.D.3 — TaskDetailSheet over NowPlayingScreen.
        // ModalBottomSheet renders above all sibling Box content per the
        // Compose dialog/sheet z-order convention, so no extra z-index
        // wrangling is needed.
        openTask?.let { t ->
            com.eight87.strictlykeptboy.ui.tasks.TaskDetailSheet(
                task = t,
                onDismiss = { openTask = null },
                onEdit = { /* Phase EE — editor stub */ },
                onToggleDone = { tasksState.toggleDone(t.id) },
            )
        }

        // Round 2.16.D.2 — TaskQuickAdd sheet (modal) over NowPlayingScreen.
        if (quickAddOpen) {
            val initialTarget = tasksUi.todolists.firstOrNull()?.let {
                com.eight87.strictlykeptboy.ui.tasks.QuickAddTarget.Todolist(it)
            }
            com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddSheet(
                todolists = tasksUi.todolists,
                initialTarget = initialTarget,
                onDismiss = { quickAddOpen = false },
                onSubmit = { title, target ->
                    onWriteTask(
                        com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest(
                            title = title,
                            target = target,
                        ),
                    )
                    quickAddOpen = false
                },
            )
        }
    }
}

