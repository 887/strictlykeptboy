package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.ProvideWindowSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.repos.ReposPane
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.schedule.SchedulePane
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.settings.SettingsAccess
import com.eight87.strictlykeptboy.ui.settings.SettingsPane
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest
import com.eight87.strictlykeptboy.ui.tasks.TasksPane
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import com.eight87.strictlykeptboy.ui.together.TogetherPane
import com.eight87.strictlykeptboy.ui.together.TogetherViewModel
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft
import com.eight87.strictlykeptboy.ui.wizard.WizardNavHost
import kotlinx.coroutines.flow.StateFlow

const val TestTagAppScaffold = "AppScaffold"

/**
 * Phase F.1 — top-level app scaffold.
 *
 * Per D.15 and UI-A: NavigationSuiteScaffold pinned to NavigationRail on
 * phone (D.15 — "rail-collapsed-on-phone-too"); on Medium/Expanded width
 * classes we open it to the full wide rail (Phase R.1 — tablets get the
 * label-text rail rather than the icon-only collapsed strip).
 *
 * Five destinations per the F.1 brief. Only Schedule has real content this
 * phase; the rest are stubbed placeholder screens.
 */
enum class TopDestination(val label: String, val icon: ImageVector) {
    Schedule("Schedule", Icons.Filled.CalendarMonth),
    Tasks("Tasks", Icons.Filled.CheckCircle),
    Together("Together", Icons.Filled.Groups),
    Repos("Repos", Icons.Filled.Folder),
    Wizard("Wizard", Icons.Filled.AutoAwesome),
    Settings("Settings", Icons.Filled.Settings),
}

const val TestTagDestPrefix = "Dest-"

@Composable
fun AppScaffold(
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
) {
    ProvideWindowSizeClass(modifier = modifier) { widthClass ->
        AppScaffoldContent(
            widthClass = widthClass,
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
        )
    }
}

@Composable
private fun AppScaffoldContent(
    widthClass: WindowWidthSizeClass,
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
    settingsAccess: SettingsAccess = SettingsAccess(),
) {
    var selected by rememberSaveable { mutableStateOf(TopDestination.Schedule) }
    val activeRepoName by activeRepoNameFlow.collectAsState()

    // D.15 — keep the rail variant on every form factor. On Compact stay
    // with the collapsed (icon-only) rail; on Medium/Expanded open to the
    // wide-expanded rail so the labels are visible alongside icons.
    val layoutType = when (widthClass) {
        WindowWidthSizeClass.Compact -> NavigationSuiteType.NavigationRail
        WindowWidthSizeClass.Medium,
        WindowWidthSizeClass.Expanded -> NavigationSuiteType.WideNavigationRailExpanded
    }

    NavigationSuiteScaffold(
        modifier = Modifier.testTag(TestTagAppScaffold),
        layoutType = layoutType,
        navigationSuiteItems = {
            TopDestination.entries.forEach { dest ->
                item(
                    selected = dest == selected,
                    onClick = { selected = dest },
                    icon = {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            modifier = Modifier.testTag("$TestTagDestPrefix${dest.name}"),
                        )
                    },
                    label = { Text(dest.label) },
                )
            }
        },
    ) {
        when (selected) {
            TopDestination.Schedule -> SchedulePane(
                activeRepoName = activeRepoName,
                state = scheduleState,
                onPersistTab = onPersistTab,
                onSyncClick = onSyncClick,
            )
            TopDestination.Tasks -> TasksPane(
                activeRepoName = activeRepoName,
                state = tasksState,
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

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.scaffold_placeholder_coming_soon), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// LocalWindowWidthSizeClass is imported above for downstream composables
// reading the ambient breakpoint without round-tripping through callers.
@Suppress("unused")
private val widthClassMarker: Any = LocalWindowWidthSizeClass
