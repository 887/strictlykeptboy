package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import com.eight87.strictlykeptboy.resolver.asDayBandSource
import com.eight87.strictlykeptboy.ui.common.FastScrollbar
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

const val TestTagEditScheduleAgenda = "EditScheduleAgenda"

/**
 * Full-screen Edit Schedule overlay — a "generic week" template view.
 *
 * Always shows Monday → Sunday with seven sticky day headers (one per
 * weekday, weekday name only — no dates), regardless of whether each
 * day has events. Underlying data comes from this week's
 * materialization so recurring routines/timeboxes show up on the
 * weekdays they fire on.
 *
 * Right side: tonearmboy-style `FastScrollbar` with a single-letter
 * weekday chip per day (M/T/W/T/F/S/S) that fades in while the user
 * scrolls or drags — the "floaties" lifted from tonearmboy's library.
 *
 * Snapshots the user's prior date + tab on mount and restores them on
 * dispose so closing returns to the original Schedule pane state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleScreen(
    scheduleState: ScheduleViewState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBandTap: (DayBand) -> Unit = {},
    eventCreateController: EventCreateController? = null,
) {
    val priorDate = remember { scheduleState.date.value }
    val priorTab = remember { scheduleState.selectedTab.value }
    val monday = remember {
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    DisposableEffect(Unit) {
        scheduleState.setDate(monday)
        scheduleState.setSelectedTab(ScheduleViewTab.Now)
        onDispose {
            scheduleState.setDate(priorDate)
            scheduleState.setSelectedTab(priorTab)
        }
    }

    val rendered by scheduleState.rendered.collectAsState()
    val dayBandSource: DayBandSource = remember(rendered) {
        rendered?.asDayBandSource() ?: DayBandSource.Empty
    }
    // Always Monday → Sunday — generic-week framing, decoupled from
    // whatever date range the resolver happens to have rendered.
    val weekDates: List<LocalDate> = remember(monday) {
        (0..6).map { monday.plusDays(it.toLong()) }
    }
    // Section-start indices for the FastScrollbar — counts sticky
    // header + (bands.size OR 1 empty-row). Mirrors the structure
    // ScheduleAgendaView builds when includeEmptyDays = true.
    val sectionStarts: List<Pair<Int, String>> = remember(weekDates, rendered) {
        val out = mutableListOf<Pair<Int, String>>()
        var idx = 0
        weekDates.forEach { d ->
            val bands = dayBandSource.bandsFor(d)
            val letter = d.dayOfWeek
                .getDisplayName(TextStyle.NARROW, Locale.getDefault())
            out += idx to letter
            idx += 1 + (if (bands.isEmpty()) 1 else bands.size)
        }
        out
    }

    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Agenda body — uses ScheduleAgendaView with includeEmptyDays
            // so every Mon-Sun gets a sticky header even on empty days,
            // and headerLabelFor renders the generic weekday name.
            ScheduleAgendaView(
                dates = weekDates,
                dayBands = dayBandSource,
                modifier = Modifier.fillMaxSize().testTag(TestTagEditScheduleAgenda),
                onBandTap = onBandTap,
                listState = listState,
                headerLabelFor = { date ->
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                },
                includeEmptyDays = true,
            )
            // tonearmboy-style FastScrollbar — draggable thumb + fading
            // section chips at each weekday boundary. Aligned to the
            // right edge.
            FastScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
                sectionStarts = sectionStarts,
            )
            // Bottom-end EventCreate FAB — same control the regular
            // SchedulePane offers, plumbed through from the shell.
            if (eventCreateController != null) {
                EventCreateFab(
                    onClick = {
                        eventCreateController.openSheet(
                            defaultStart = monday.atTime(12, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toOffsetDateTime(),
                            defaultDurationMinutes = 5,
                            defaultRecurrence = RecurrencePreset.Weekly,
                        )
                    },
                    onLongPressPlanTrip = {},
                    enabled = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }
}
