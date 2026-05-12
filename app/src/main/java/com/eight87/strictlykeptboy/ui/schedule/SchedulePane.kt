package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbTopBar

/** Stateful parent — owns the active-view-tab + repo-switcher callbacks. */
@Composable
fun SchedulePane(
    activeRepoName: String,
    state: ScheduleViewState,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(ScheduleViewTab.Day) }
    val date by state.date.collectAsState()
    val rendered by state.rendered.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        SkbTopBar(
            activeRepoName = activeRepoName,
            selectedViewTab = selectedTab,
            onSelectViewTab = { selectedTab = it },
            onRepoSwitcherClick = { /* UI-K — stubbed for Phase F */ },
            onSyncClick = { /* Phase J — stubbed */ },
            onIdentityClick = { /* UI-L — stubbed */ },
        )
        when (selectedTab) {
            ScheduleViewTab.Day -> ScheduleDayView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
            )
            else -> ScheduleDayView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
