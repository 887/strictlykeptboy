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
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbTopBar
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** Stateful parent — owns the active-view-tab + repo-switcher callbacks. */
@Composable
fun SchedulePane(
    activeRepoName: String,
    state: ScheduleViewState,
    modifier: Modifier = Modifier,
    onPersistTab: (ScheduleViewTab) -> Unit = {},
) {
    val selectedTab by state.selectedTab.collectAsState()
    val date by state.date.collectAsState()
    val rendered by state.rendered.collectAsState()

    var detailBand by remember { mutableStateOf<DayBand?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SkbTopBar(
            activeRepoName = activeRepoName,
            selectedViewTab = selectedTab,
            onSelectViewTab = {
                state.setSelectedTab(it)
                onPersistTab(it)
            },
            onRepoSwitcherClick = { /* UI-K — stubbed for Phase F */ },
            onSyncClick = { /* Phase J — stubbed */ },
            onIdentityClick = { /* UI-L — stubbed */ },
        )
        when (selectedTab) {
            ScheduleViewTab.Day -> ScheduleDayView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onBandTap = { detailBand = it },
            )
            ScheduleViewTab.Week -> {
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ScheduleWeekView(
                    weekStart = weekStart,
                    schedule = rendered,
                    modifier = Modifier.fillMaxSize(),
                    onBandTap = { detailBand = it },
                    onSwipeWeek = { delta -> state.setDate(date.plusWeeks(delta.toLong())) },
                )
            }
            ScheduleViewTab.Month -> ScheduleMonthView(
                monthAnchor = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onDayTap = { d ->
                    state.setDate(d)
                    state.setSelectedTab(ScheduleViewTab.Day)
                    onPersistTab(ScheduleViewTab.Day)
                },
                onBandTap = { detailBand = it },
            )
            ScheduleViewTab.Agenda -> ScheduleTimeboxView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onBandTap = { detailBand = it },
            )
            ScheduleViewTab.Year -> ScheduleYearView(
                year = date.year,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onMonthTap = { m ->
                    state.setDate(date.withMonth(m.value).withDayOfMonth(1))
                    state.setSelectedTab(ScheduleViewTab.Month)
                    onPersistTab(ScheduleViewTab.Month)
                },
            )
        }
    }

    detailBand?.let { band ->
        EventDetailSheet(
            band = band,
            onDismiss = { detailBand = null },
            onEdit = { /* Phase I/K — stubbed */ },
        )
    }
}
