package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import com.eight87.strictlykeptboy.resolver.asDayBandSource
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

const val TestTagEditScheduleAgenda = "EditScheduleAgenda"

/**
 * Full-screen Edit Schedule overlay — the agenda-list renderer
 * (`ScheduleAgendaView`) anchored at this week's Monday so the user
 * always sees a consistent week starting on a Monday. The screen
 * temporarily flips the shared [ScheduleViewState] to that anchor +
 * `Now` tab while open and restores the prior selection on dispose, so
 * closing returns the user to whatever view they had on the schedule
 * pane.
 *
 * Tap-through on a band routes up to [onBandTap] — the host mounts
 * EventDetailScreen above this surface (same outer-Box pattern).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleScreen(
    scheduleState: ScheduleViewState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBandTap: (DayBand) -> Unit = {},
) {
    // Snapshot the user's prior selection so closing restores it.
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().testTag(TestTagEditScheduleAgenda)) {
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
                    )
                }
            }
        }
    }
}
