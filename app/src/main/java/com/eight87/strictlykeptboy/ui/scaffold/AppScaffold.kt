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
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.ui.schedule.SchedulePane
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.tasks.TaskQuickAddRequest
import com.eight87.strictlykeptboy.ui.tasks.TasksPane
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
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
            )
            TopDestination.Tasks -> TasksPane(
                activeRepoName = activeRepoName,
                state = tasksState,
                onWriteTask = onWriteTask,
            )
            TopDestination.Repos -> PlaceholderScreen("Repos")
            TopDestination.Wizard -> PlaceholderScreen("Wizard")
            TopDestination.Settings -> PlaceholderScreen("Settings")
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
            Text("coming soon", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
