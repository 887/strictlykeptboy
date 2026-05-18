package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import com.eight87.strictlykeptboy.resolver.asDayBandSource
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

const val TestTagEditScheduleAgenda = "EditScheduleAgenda"
const val TestTagEditScheduleDayJumperRail = "EditScheduleDayJumperRail"
const val TestTagEditScheduleDayJumperItemPrefix = "EditScheduleDayJumper-"

/**
 * Full-screen Edit Schedule overlay — agenda-list renderer anchored at
 * this week's Monday. Snapshots the user's prior schedule date + tab on
 * mount and restores them on dispose.
 *
 * Layout: TopAppBar · agenda body · right-side day-jumper rail (tap a
 * day-of-month to scroll the agenda to that day's sticky header) ·
 * bottom-end EventCreate FAB (when wired).
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
    val renderedDates = remember(rendered) {
        rendered?.days?.map { it.date }.orEmpty()
    }

    // Precompute non-empty days + their item index in the LazyColumn so
    // the side rail can scroll-to-day. Index counts the sticky header
    // (1) plus that day's bands.
    val daysWithIndex: List<Pair<LocalDate, Int>> = remember(rendered, renderedDates) {
        val out = mutableListOf<Pair<LocalDate, Int>>()
        var idx = 0
        renderedDates.forEach { d ->
            val bands = dayBandSource.bandsFor(d)
            if (bands.isNotEmpty()) {
                out += d to idx
                idx += 1 + bands.size
            }
        }
        out
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag(TestTagEditScheduleAgenda)) {
                    if (renderedDates.isEmpty()) {
                        Text(
                            text = stringResource(R.string.schedule_detail_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        ScheduleAgendaView(
                            dates = renderedDates,
                            dayBands = dayBandSource,
                            modifier = Modifier.fillMaxSize(),
                            onBandTap = onBandTap,
                            listState = listState,
                        )
                    }
                }
                // Right-side day-jumper rail — one cell per non-empty day,
                // tap to scroll the agenda to that day's sticky header.
                if (daysWithIndex.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .width(40.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .testTag(TestTagEditScheduleDayJumperRail),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        daysWithIndex.forEach { (date, headerIndex) ->
                            DayJumperCell(
                                date = date,
                                isToday = date == LocalDate.now(),
                                onClick = {
                                    coroutineScope.launch { listState.scrollToItem(headerIndex) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            // Bottom-end EventCreate FAB — same control the regular
            // SchedulePane offers, plumbed through from the shell.
            if (eventCreateController != null) {
                EventCreateFab(
                    onClick = {
                        eventCreateController.openSheet(
                            defaultStart = monday.atTime(12, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toOffsetDateTime(),
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

@Composable
private fun DayJumperCell(
    date: LocalDate,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (isToday) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainer
    val content = if (isToday) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("$TestTagEditScheduleDayJumperItemPrefix$date"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfWeek.name.take(1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = content,
                )
            }
        }
    }
}
