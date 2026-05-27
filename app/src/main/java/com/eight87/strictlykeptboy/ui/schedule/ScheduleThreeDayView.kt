package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import java.time.LocalDate

const val TestTagThreeDayView = "ScheduleThreeDayView"
const val TestTagThreeDayColumn = "ThreeDayColumn"

/**
 * Round 2.21 Phase E.3 — 3-day timeline.
 *
 * Three day-grids side by side, each rendered by reusing
 * [ScheduleDayView]. Respects [effectiveZoom] per D-2.21.g. Resolver
 * range is `[date .. date+2]` (set in [ScheduleViewState.rangeAndModeFor]).
 */
@Composable
fun ScheduleThreeDayView(
    anchor: LocalDate,
    dayBands: DayBandSource,
    modifier: Modifier = Modifier,
    onBandTap: (DayBand) -> Unit = {},
    today: LocalDate = LocalDate.now(),
    defaultWriteRepoId: String = "",
    effectiveZoom: Int = 2,
    /** Round 2.22 / Phase B UI follow-up — long-press-and-drag drop callback. */
    onDragReschedule: ((DayBand, java.time.OffsetDateTime) -> Unit)? = null,
) {
    val dates = (0..2).map { anchor.plusDays(it.toLong()) }
    val sharedScroll = rememberScrollState()
    val hourHeight = hourHeightForZoom(effectiveZoom)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    // Auto-scroll to the NowLine when today is in the visible range,
    // else anchor at 07:00. Matches ScheduleDayView / ScheduleWeekView
    // so view-switches don't dump the user at 00:00.
    androidx.compose.runtime.LaunchedEffect(anchor, hourHeightPx) {
        if (hourHeightPx <= 0f) return@LaunchedEffect
        val rangeContainsToday = today in dates
        val targetHours = if (rangeContainsToday) {
            val now = java.time.LocalTime.now()
            (now.hour + now.minute / 60f) - 3f
        } else {
            7f
        }
        sharedScroll.scrollTo((targetHours * hourHeightPx).toInt().coerceAtLeast(0))
    }
    Column(modifier = modifier.fillMaxSize().testTag(TestTagThreeDayView)) {
        // Header strip — gutter spacer + 3 day chips so the day columns
        // line up exactly with the body columns below.
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(DayViewGutterWidth))
            dates.forEach { d ->
                val isToday = d == today
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Round 2.23 Phase B (D-2.23.c) — weekday emoji
                        // header strip; visible above the weekday abbreviation.
                        Text(
                            text = emojiFor(d.dayOfWeek),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = d.dayOfWeek.name.take(3),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = d.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Three side-by-side day columns. Each column reuses the same
        // ScheduleDayView; vertical scroll lives inside each column.
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            // Shared hour gutter — outside the equal-weight column row
            // and inside its own verticalScroll bound to [sharedScroll]
            // so the hour labels track the body columns.
            Column(modifier = Modifier.width(DayViewGutterWidth).verticalScroll(sharedScroll)) {
                DayHourGutter(hourHeight = hourHeight)
            }
            dates.forEach { d ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("$TestTagThreeDayColumn-$d"),
                ) {
                    ScheduleDayView(
                        date = d,
                        dayBands = dayBands,
                        onBandTap = onBandTap,
                        isToday = d == today,
                        defaultWriteRepoId = defaultWriteRepoId,
                        effectiveZoom = effectiveZoom,
                        onDragReschedule = onDragReschedule,
                        showWeekdayHeader = false,
                        showHourGutter = false,
                        // All three columns share one scroll state so a
                        // drag on any of them moves all in sync.
                        sharedScrollState = sharedScroll,
                    )
                }
            }
        }
    }
}
