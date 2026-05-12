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
    importExportState: ImportExportViewState? = null,
    onPickImportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit = {},
    onPickExportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit = {},
    settingsAccess: SettingsAccess = SettingsAccess(),
    activeIconKindFlow: StateFlow<com.eight87.strictlykeptboy.ui.theming.RepoIconKind>? = null,
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
            importExportState = importExportState,
            onPickImportFile = onPickImportFile,
            onPickExportFile = onPickExportFile,
            settingsAccess = settingsAccess,
            activeIconKindFlow = activeIconKindFlow,
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
    importExportState: ImportExportViewState?,
    onPickImportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    onPickExportFile: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit,
    settingsAccess: SettingsAccess,
    activeIconKindFlow: StateFlow<com.eight87.strictlykeptboy.ui.theming.RepoIconKind>?,
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().testTag(TestTagAppShell),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ShellTopBar(
                activeRepoName = activeRepoName,
                activeIconKind = activeIconKind,
                selectedDest = selected,
                onSelectDest = { selected = it },
                onSyncClick = onSyncClick,
                onIdentityClick = { /* UI-L — stubbed */ },
                onRepoSwitcherClick = { /* UI-K — stubbed */ },
            )
            Row(modifier = Modifier.fillMaxSize()) {
                if (railItems.isNotEmpty()) {
                    RailColumn(items = railItems)
                }
                Box(
                    modifier = Modifier.fillMaxSize().testTag(TestTagShellContent),
                ) {
                    when (selected) {
                        TopDestination.Schedule -> SchedulePane(
                            activeRepoName = activeRepoName,
                            state = scheduleState,
                            onSyncClick = onSyncClick,
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
                            ReposPane(state = reposState, secretsStore = secretsStore)
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
                            access = settingsAccess,
                        )
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Far-left: per-D.88, the avatar IS the active repo's identity.
            // [activeIconKind] determines the rendering — Sticker(species),
            // Photo(uri), Emoji(glyph), or AutoInitials. Tap routes to the
            // repo-switcher (the avatar is the active-repo affordance).
            IdentityAvatar(onClick = onRepoSwitcherClick, iconKind = activeIconKind)
            // Destination buttons fill the rest of the row.
            val destScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .androidx_horizontalScroll(destScroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TopDestination.entries.forEach { dest ->
                    DestinationButton(
                        dest = dest,
                        selected = dest == selectedDest,
                        onClick = { onSelectDest(dest) },
                    )
                }
            }
            SyncButton(onClick = onSyncClick)
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
    // Stacked layout — icon on top, small label beneath. Lets all 6 destinations
    // fit in the top-bar row on Compact width without cropping the labels
    // (user-reported: side-by-side icon+label was cropping past "Schedule").
    // Each button stays ~56dp wide; the row no longer needs horizontal-scroll
    // in normal phone widths.
    val label = dest.labelString()
    val tag = "$TestTagShellDestPrefix${dest.name}"
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .testTag(tag)
            .semantics { contentDescription = label }
            .background(containerColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .requiredWidth(70.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = dest.icon,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
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
private fun RailColumn(items: List<RailItem>) {
    // Match tonearmboy's LibraryRail: 52dp wide, 108dp per item. The previous
    // 72dp width made labelLarge text feel chunky vs the tonearmboy reference
    // the user calls out as "kinda perfect".
    val railWidth = 52.dp
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .requiredWidth(railWidth)
            .background(MaterialTheme.colorScheme.surface)
            .testTag(TestTagShellRail),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                RailTabItem(item = item)
            }
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
