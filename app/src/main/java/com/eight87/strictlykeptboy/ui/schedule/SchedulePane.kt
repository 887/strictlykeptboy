package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbTopBar
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters

const val TestTagScheduleDetailEmpty = "ScheduleDetailEmpty"
const val TestTagScheduleMasterPane = "ScheduleMasterPane"

/**
 * Stateful parent — owns the active-view-tab + repo-switcher callbacks.
 *
 * Phase R.2 — on Medium/Expanded width classes this pane becomes a
 * `Row(master | detail)` where the right pane always renders the
 * focused event's detail. On Compact (phone) the legacy modal bottom
 * sheet behaviour is preserved.
 */
@Composable
fun SchedulePane(
    activeRepoName: String,
    state: ScheduleViewState,
    modifier: Modifier = Modifier,
    onPersistTab: (ScheduleViewTab) -> Unit = {},
    onSyncClick: () -> Unit = {},
) {
    val widthClass = LocalWindowWidthSizeClass.current
    val rendered by state.rendered.collectAsState()

    var detailBand by remember { mutableStateOf<DayBand?>(null) }

    // Auto-focus the currently-active band (now-card resolver query) on
    // tablet so the right pane is never empty when there's a clear
    // "now" event. Compact still opens the sheet only on explicit tap.
    val activeNowBand = remember(rendered) {
        rendered?.let { rs ->
            findActiveBand(rs.days.flatMap { it.bands })
        }
    }
    val effectiveDetail = detailBand ?: if (widthClass.isTwoPane()) activeNowBand else null

    if (widthClass.isTwoPane()) {
        MasterDetailLayout(
            modifier = modifier,
            widthClass = widthClass,
            master = {
                Box(modifier = Modifier.testTag(TestTagScheduleMasterPane)) {
                    ScheduleMasterContent(
                        activeRepoName = activeRepoName,
                        state = state,
                        onPersistTab = onPersistTab,
                        onSyncClick = onSyncClick,
                        onBandTap = { detailBand = it },
                    )
                }
            },
            detail = {
                if (effectiveDetail != null) {
                    EventDetailContent(band = effectiveDetail)
                } else {
                    ScheduleDetailEmptyState()
                }
            },
        )
    } else {
        Box(modifier = modifier.fillMaxSize().testTag(TestTagScheduleMasterPane)) {
            ScheduleMasterContent(
                activeRepoName = activeRepoName,
                state = state,
                onPersistTab = onPersistTab,
                onSyncClick = onSyncClick,
                onBandTap = { detailBand = it },
            )
        }
        detailBand?.let { band ->
            EventDetailSheet(
                band = band,
                onDismiss = { detailBand = null },
                onEdit = { /* Phase I/K — stubbed */ },
            )
        }
    }
}

@Composable
private fun ScheduleMasterContent(
    activeRepoName: String,
    state: ScheduleViewState,
    onPersistTab: (ScheduleViewTab) -> Unit,
    onSyncClick: () -> Unit,
    onBandTap: (DayBand) -> Unit,
) {
    val selectedTab by state.selectedTab.collectAsState()
    val date by state.date.collectAsState()
    val rendered by state.rendered.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        SkbTopBar(
            activeRepoName = activeRepoName,
            selectedViewTab = selectedTab,
            onSelectViewTab = {
                state.setSelectedTab(it)
                onPersistTab(it)
            },
            onRepoSwitcherClick = { /* UI-K — stubbed for Phase F */ },
            onSyncClick = onSyncClick,
            onIdentityClick = { /* UI-L — stubbed */ },
        )
        when (selectedTab) {
            ScheduleViewTab.Day -> ScheduleDayView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onBandTap = onBandTap,
            )
            ScheduleViewTab.Week -> {
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ScheduleWeekView(
                    weekStart = weekStart,
                    schedule = rendered,
                    modifier = Modifier.fillMaxSize(),
                    onBandTap = onBandTap,
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
                onBandTap = onBandTap,
            )
            ScheduleViewTab.Agenda -> ScheduleTimeboxView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onBandTap = onBandTap,
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
}

@Composable
private fun ScheduleDetailEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(TestTagScheduleDetailEmpty),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.schedule_detail_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Now-card resolver query: first band whose effective interval contains
 * `Instant.now()`. Returns null if no band is currently active.
 */
internal fun findActiveBand(bands: List<DayBand>, now: Instant = Instant.now()): DayBand? =
    bands.firstOrNull { b ->
        val start = b.instance.effectiveStart.toInstant()
        val end = b.instance.effectiveEnd.toInstant()
        !now.isBefore(start) && now.isBefore(end)
    }
