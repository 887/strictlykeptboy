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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
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
import com.eight87.strictlykeptboy.ui.tasks.TaskViewTab
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

/**
 * Phase U.4 / F11 note: [label] is **wire-format** / stable English fallback
 * for testTag composition and toString. Translatable UI display routes
 * through `TopDestination.labelString()` in `ui/a11y/EnumLabels.kt`.
 */
enum class TopDestination(val label: String, val icon: ImageVector) {
    Schedule("Schedule", Icons.Filled.CalendarMonth),
    Tasks("Tasks", Icons.Filled.CheckCircle),
    Together("Together", Icons.Filled.Groups),
    Repos("Repos", Icons.Filled.Folder),
    Wizard("Wizard", Icons.Filled.AutoAwesome),
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
) {
    var selected by rememberSaveable { mutableStateOf(TopDestination.Schedule) }
    val activeRepoName by activeRepoNameFlow.collectAsState()
    // D.88 / F48 — top-bar avatar reflects the active repo's iconKind. Defaults
    // to Sticker("bat") if the caller hasn't wired the flow (e.g. tests, previews).
    val activeIconKind by (activeIconKindFlow
        ?: MutableStateFlow(com.eight87.strictlykeptboy.ui.theming.RepoIconKind.Sticker("bat") as com.eight87.strictlykeptboy.ui.theming.RepoIconKind))
        .collectAsState()

    // Tasks owns its tab here so the rail (which lives in the shell) can
    // drive it. ISP: only the tab + setter are hoisted; quick-add /
    // detail sheets continue to live inside [TasksPane].
    var tasksTab by rememberSaveable { mutableStateOf(TaskViewTab.Combined) }

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
        TopDestination.Tasks -> TaskViewTab.entries.map { tab ->
            RailItem(
                key = tab.name,
                labelRes = taskTabLabelRes(tab),
                selected = tab == tasksTab,
                onClick = { tasksTab = tab },
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().testTag(TestTagAppShell),
    ) {
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
                // the Repos destination (which the top-bar button-row hides,
                // since this avatar covers it).
                onRepoSwitcherClick = { selected = TopDestination.Repos },
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
                        )
                        TopDestination.Tasks -> TasksPane(
                            activeRepoName = activeRepoName,
                            state = tasksState,
                            selectedTab = tasksTab,
                            onSelectTab = { tasksTab = it },
                            onWriteTask = onWriteTask,
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
                            )
                        } else {
                            PlaceholderScreen(stringResource(R.string.scaffold_dest_repos))
                        }
                        TopDestination.Wizard -> WizardNavHost(
                            onFinish = {
                                onWizardFinish()
                                selected = TopDestination.Schedule
                            },
                            onCancel = { selected = TopDestination.Schedule },
                            onScaffold = onWizardScaffold,
                            neutralMode = neutralMode,
                        )
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
                }
            }
        }
    }
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
        // Tonearmboy-shape: big title left + small icon actions right + sync.
        // The big stacked destination buttons are gone; destinations live
        // as tiny IconButtons in the action row. Bat + settings-gear move to
        // the BOTTOM of the left rail (see RailColumn). See user direction
        // 2026-05-13 (tonearmboy parity ask).
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            // Top-level destinations as tiny icon-only buttons. Hidden:
            //   - Repos (reachable via the trailing bat avatar)
            //   - Settings (reachable via the rail-bottom gear)
            //   - Together (reachable from inside the Repos pane as "find a time")
            //   - Wizard (Wizard top-bar entry stays for now; planned to demote
            //     to a "+" inside the Repos pane in a follow-up round)
            TopDestination.entries
                .filter {
                    it != TopDestination.Repos &&
                        it != TopDestination.Settings &&
                        it != TopDestination.Together &&
                        it != TopDestination.Wizard
                }
                .forEach { dest ->
                    DestinationButton(
                        dest = dest,
                        selected = dest == selectedDest,
                        onClick = { onSelectDest(dest) },
                    )
                }
            SyncButton(onClick = onSyncClick)
            // Settings gear — same selected-tint pattern as the Schedule /
            // Tasks destination buttons (FilledTonalIconButton when active)
            // so it reads as a real navigation tab, not a plain icon.
            val settingsMod = Modifier.testTag("ShellTopBarSettings")
            if (selectedDest == TopDestination.Settings) {
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { onSelectDest(TopDestination.Settings) },
                    modifier = settingsMod,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.dest_settings),
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                androidx.compose.material3.IconButton(
                    onClick = { onSelectDest(TopDestination.Settings) },
                    modifier = settingsMod,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.dest_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            // Far-right: bat/accounts avatar. Per user direction 2026-05-13:
            // put the bat back up top on the very right (was at rail bottom).
            // Tapping it navigates to Repos (D.88: bat IS the active-repo
            // affordance). Sized 40dp to match the IconButton hit-targets in
            // this row — previously 28dp default which read smaller than the
            // other action icons.
            IdentityAvatar(
                onClick = onRepoSwitcherClick,
                iconKind = activeIconKind,
                sizeDp = 40,
            )
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

@StringRes
private fun taskTabLabelRes(tab: TaskViewTab): Int = when (tab) {
    TaskViewTab.Combined -> R.string.task_view_tab_combined
    TaskViewTab.Today -> R.string.task_view_tab_today
    TaskViewTab.PerList -> R.string.task_view_tab_per_list
    TaskViewTab.Shopping -> R.string.task_view_tab_shopping
    TaskViewTab.Standing -> R.string.task_view_tab_standing
}
