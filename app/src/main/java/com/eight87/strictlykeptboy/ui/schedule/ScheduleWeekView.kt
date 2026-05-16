package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.ui.share.isForeignBand
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

const val TestTagWeekView = "ScheduleWeekView"
const val TestTagWeekColumn = "WeekColumn"
const val TestTagWeekHeader = "WeekHeader"
const val TestTagWeekTodayColumn = "WeekTodayColumn"
const val TestTagWeekBand = "WeekBand"

private val GutterWidth = 44.dp

/**
 * Phase G.2 — 7-column week timeline.
 *
 * Stateless. Takes a 7-day [RenderedSchedule] plus the week-start
 * [LocalDate]. Horizontal swipe deltas (>72.dp) trigger
 * [onSwipeWeek] with -1 (previous) / +1 (next).
 */
@Composable
fun ScheduleWeekView(
    weekStart: LocalDate,
    schedule: RenderedSchedule?,
    modifier: Modifier = Modifier,
    onBandTap: (DayBand) -> Unit = {},
    onSwipeWeek: (Int) -> Unit = {},
    today: LocalDate = LocalDate.now(),
    /** Round 2.2.C.2 — default-write repo for `isForeignBand`. Empty = chip suppressed. */
    defaultWriteRepoId: String = "",
    /** Round 2.21 Phase D.7 — effective zoom ∈ {1..4}, default 2 (80dp/h). */
    effectiveZoom: Int = 2,
    /** Round 2.22 / Phase B UI follow-up — long-press-and-drag drop callback. */
    onDragReschedule: ((DayBand, java.time.OffsetDateTime) -> Unit)? = null,
) {
    val HourHeight = hourHeightForZoom(effectiveZoom)
    val dragState = rememberDragRescheduleUiState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hourHeightPx = with(density) { HourHeight.toPx() }
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val bandsByDate: Map<LocalDate, List<DayBand>> =
        schedule?.days?.associate { it.date to it.bands }.orEmpty()

    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagWeekView)
            .pointerInput(weekStart) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = {
                        val threshold = 72.dp.toPx()
                        if (dragX > threshold) onSwipeWeek(-1)
                        else if (dragX < -threshold) onSwipeWeek(+1)
                    },
                ) { _, delta -> dragX += delta }
            },
    ) {
        // Header row — shared gutter spacer + 7 day chips.
        Row(modifier = Modifier.fillMaxWidth().testTag(TestTagWeekHeader)) {
            Box(modifier = Modifier.width(GutterWidth))
            days.forEach { d ->
                val isToday = d == today
                Surface(
                    color = if (isToday) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .then(
                            if (isToday) Modifier.testTag(TestTagWeekTodayColumn)
                            else Modifier,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
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

        // Vertically-scrollable timeline.
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
            HourGutter(hourHeight = HourHeight)
            Row(modifier = Modifier.fillMaxWidth().height(HourHeight * 24)) {
                days.forEach { d ->
                    DayColumn(
                        date = d,
                        bands = bandsByDate[d].orEmpty(),
                        isToday = d == today,
                        onBandTap = onBandTap,
                        defaultWriteRepoId = defaultWriteRepoId,
                        hourHeight = HourHeight,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        dragState = dragState,
                        hourHeightPx = hourHeightPx,
                        onDragReschedule = onDragReschedule,
                    )
                }
            }
        }
    }
}

@Composable
private fun HourGutter(hourHeight: androidx.compose.ui.unit.Dp) {
    Column(modifier = Modifier.width(GutterWidth)) {
        for (hr in 0 until 24) {
            Box(modifier = Modifier.fillMaxWidth().height(hourHeight).padding(start = 4.dp, top = 2.dp)) {
                Text(
                    text = "%02d".format(hr),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    bands: List<DayBand>,
    isToday: Boolean,
    onBandTap: (DayBand) -> Unit,
    defaultWriteRepoId: String,
    hourHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    dragState: DragRescheduleUiState? = null,
    hourHeightPx: Float = 0f,
    onDragReschedule: ((DayBand, java.time.OffsetDateTime) -> Unit)? = null,
) {
    val HourHeight = hourHeight
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .background(
                if (isToday) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surface,
            )
            .testTag("$TestTagWeekColumn-$date"),
    ) {
        // Hour grid lines.
        Column(modifier = Modifier.fillMaxSize()) {
            for (hr in 0 until 24) {
                Box(modifier = Modifier.fillMaxWidth().height(HourHeight)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        val widthPx = maxWidth
        bands.forEach { band ->
            val laneWidth = widthPx / band.totalLanes.coerceAtLeast(1)
            val laneOffsetX = laneWidth * band.laneIndex
            val start = band.instance.effectiveStart
            val end = band.instance.effectiveEnd
            val topDp = HourHeight * minutesFromMidnight(start) / 60f
            val heightDp = HourHeight * durationMinutes(start, end).coerceAtLeast(15f) / 60f

            val isSuperseded = band.supersededByCalendar != null
            val isOffSchedule = band.offSchedule
            val seedColor = colorForSeed(band.accentColorSeed)
            // Week view uses chroma-reduced full fill per spec.
            val fillColor = if (seedColor == Color.Unspecified) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                seedColor.copy(alpha = 0.6f)
            }
            val effectiveFill = if (isSuperseded) fillColor.copy(alpha = fillColor.alpha * 0.35f) else fillColor
            val authorId = band.instance.author?.id
            val showAuthorChip = authorId != null &&
                isForeignBand(band.instance.repo.id, defaultWriteRepoId)

            Box(
                modifier = Modifier
                    .offset(x = laneOffsetX, y = topDp)
                    .width((laneWidth - 2.dp).coerceAtLeast(8.dp))
                    .height(heightDp)
                    .padding(1.dp),
            ) {
                val dragModifier = if (dragState != null && onDragReschedule != null) {
                    Modifier.dragRescheduleBand(
                        state = dragState,
                        band = band,
                        hourHeightPx = hourHeightPx,
                        onDrop = onDragReschedule,
                    )
                } else Modifier
                Surface(
                    onClick = { onBandTap(band) },
                    color = effectiveFill,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(dragModifier)
                        .testTag("$TestTagWeekBand-${band.instance.instanceId}"),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            BandKindGlyph(kind = band.kind)
                            if (isSuperseded) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(2.dp))
                                BandSupersededGlyph()
                            }
                            if (isOffSchedule) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(2.dp))
                                BandOffScheduleGlyph()
                            }
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = band.instance.title,
                                style = MaterialTheme.typography.labelSmall,
                                textDecoration = if (isSuperseded) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (showAuthorChip) {
                            BandAuthorChip(
                                authorId = authorId!!,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp),
                            )
                        }
                    }
                }
                if (isOffSchedule) {
                    BandDashedBorder(
                        color = MaterialTheme.colorScheme.error,
                        cornerRadiusDp = 8.dp,
                    )
                }
            }
        }

        if (isToday) WeekNowLine(hourHeight = HourHeight)
    }
}

@Composable
private fun WeekNowLine(hourHeight: androidx.compose.ui.unit.Dp) {
    val HourHeight = hourHeight
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000L)
        }
    }
    val topDp = HourHeight * (now.hour * 60 + now.minute) / 60f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .offset(y = topDp)
            .background(Color.Red),
    )
}

private fun minutesFromMidnight(z: ZonedDateTime): Float =
    (z.hour * 60 + z.minute + z.second / 60f)

private fun durationMinutes(start: ZonedDateTime, end: ZonedDateTime): Float {
    val s = minutesFromMidnight(start)
    val e = minutesFromMidnight(end)
    return (e - s).coerceAtLeast(0f)
}
