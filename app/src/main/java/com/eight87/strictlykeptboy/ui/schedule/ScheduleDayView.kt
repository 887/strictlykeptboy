package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

const val TestTagDayView = "ScheduleDayView"
const val TestTagDayBand = "DayBand"
const val TestTagDayEmpty = "DayEmpty"
const val TestTagNowLine = "NowLine"

private val HourHeight = 60.dp
private val GutterWidth = 56.dp

/** Phase F.4 — stateless day view consuming a [RenderedSchedule]. */
@Composable
fun ScheduleDayView(
    date: LocalDate,
    schedule: RenderedSchedule?,
    modifier: Modifier = Modifier,
    onAddAt: (LocalTime) -> Unit = {},
    onBandTap: (DayBand) -> Unit = {},
    isToday: Boolean = date == LocalDate.now(),
) {
    val day = schedule?.days?.firstOrNull { it.date == date }
    val bands = day?.bands.orEmpty()

    if (bands.isEmpty()) {
        EmptyScheduleState(modifier = modifier.fillMaxSize().testTag(TestTagDayEmpty))
        return
    }

    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .testTag(TestTagDayView),
    ) {
        HourGutter()
        Box(modifier = Modifier.fillMaxWidth().height(HourHeight * 24)) {
            HourLines(onTapHour = { hr -> onAddAt(LocalTime.of(hr, 0)) })
            BandsLayer(bands = bands, onBandTap = onBandTap)
            if (isToday) NowLine()
        }
    }
}

@Composable
private fun HourGutter() {
    Column(modifier = Modifier.width(GutterWidth)) {
        for (hr in 0 until 24) {
            Box(
                modifier = Modifier.fillMaxWidth().height(HourHeight).padding(start = 8.dp, top = 2.dp),
            ) {
                Text(
                    text = "%02d".format(hr),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HourLines(onTapHour: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (hr in 0 until 24) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HourHeight)
                    .clickable { onTapHour(hr) },
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun BandsLayer(bands: List<DayBand>, onBandTap: (DayBand) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = maxWidth
        bands.forEach { band ->
            val laneWidth = widthPx / band.totalLanes.coerceAtLeast(1)
            val laneOffsetX = laneWidth * band.laneIndex

            val start = band.instance.effectiveStart
            val end = band.instance.effectiveEnd
            val topDp = HourHeight * minutesFromMidnight(start) / 60f
            val heightDp = HourHeight * durationMinutes(start, end).coerceAtLeast(15f) / 60f

            Surface(
                onClick = { onBandTap(band) },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .offset(x = laneOffsetX, y = topDp)
                    .width(laneWidth - 4.dp)
                    .height(heightDp)
                    .padding(2.dp)
                    .testTag("$TestTagDayBand-${band.instance.instanceId}"),
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = band.instance.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "%02d:%02d–%02d:%02d".format(
                            start.hour, start.minute, end.hour, end.minute,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowLine() {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.LocalTime.now()
            delay(30_000L)
        }
    }
    val topDp = HourHeight * (now.hour * 60 + now.minute) / 60f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .offset(y = topDp)
            .background(Color.Red)
            .testTag(TestTagNowLine),
    )
}

private fun minutesFromMidnight(z: ZonedDateTime): Float =
    (z.hour * 60 + z.minute + z.second / 60f)

private fun durationMinutes(start: ZonedDateTime, end: ZonedDateTime): Float {
    val s = minutesFromMidnight(start)
    val e = minutesFromMidnight(end)
    return (e - s).coerceAtLeast(0f)
}
