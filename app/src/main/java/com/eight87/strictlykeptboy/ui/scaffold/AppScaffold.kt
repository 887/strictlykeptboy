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
import com.eight87.strictlykeptboy.ui.repos.ReposPane
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.schedule.SchedulePane
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.import_export.ImportExportScreen
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
 * Per D.15 and UI-A: NavigationSuiteScaffold pinned to NavigationRail at
 * every width class — collapsed rail on phones too. The rail visual rhyme
 * matches tonearmboy's main library tabs.
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
) {
    var selected by rememberSaveable { mutableStateOf(TopDestination.Schedule) }
    val activeRepoName by activeRepoNameFlow.collectAsState()

    NavigationSuiteScaffold(
        modifier = modifier.testTag(TestTagAppScaffold),
        // D.15 — force the rail variant on every form factor (phone too).
        layoutType = NavigationSuiteType.NavigationRail,
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
            TopDestination.Settings -> if (importExportState != null) {
                ImportExportScreen(
                    state = importExportState,
                    onPickImportFile = onPickImportFile,
                    onPickExportFile = onPickExportFile,
                )
            } else {
                PlaceholderScreen(stringResource(R.string.scaffold_dest_settings))
            }
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
