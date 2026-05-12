package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

const val TestTagMonthView = "ScheduleMonthView"
const val TestTagMonthCell = "MonthCell"
const val TestTagMonthTodayCell = "MonthTodayCell"
const val TestTagMonthOverflow = "MonthOverflow"
const val TestTagMonthChip = "MonthChip"

private const val MaxChipsPerCell = 3

/**
 * Phase G.3 — month grid (6 rows × 7 cols).
 *
 * Always renders 6 rows so the grid doesn't reflow on month change —
 * spec UI-F. Out-of-month days are dimmed.
 */
@Composable
fun ScheduleMonthView(
    monthAnchor: LocalDate,
    schedule: RenderedSchedule?,
    modifier: Modifier = Modifier,
    onDayTap: (LocalDate) -> Unit = {},
    onOverflowTap: (LocalDate) -> Unit = {},
    onBandTap: (DayBand) -> Unit = {},
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
    today: LocalDate = LocalDate.now(),
) {
    val firstOfMonth = monthAnchor.withDayOfMonth(1)
    val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(weekStart))

    val bandsByDate: Map<LocalDate, List<DayBand>> =
        schedule?.days?.associate { it.date to it.bands }.orEmpty()

    Column(modifier = modifier.fillMaxSize().testTag(TestTagMonthView)) {
        // Day-of-week header.
        Row(modifier = Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                val dow = weekStart.plus(i.toLong())
                Box(modifier = Modifier.weight(1f).padding(4.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = dow.name.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 6 rows × 7 cols.
        repeat(6) { rowIdx ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(7) { colIdx ->
                    val date = gridStart.plusDays((rowIdx * 7 + colIdx).toLong())
                    val inMonth = date.month == firstOfMonth.month
                    val isToday = date == today
                    MonthCell(
                        date = date,
                        bands = bandsByDate[date].orEmpty(),
                        inMonth = inMonth,
                        isToday = isToday,
                        onDayTap = onDayTap,
                        onOverflowTap = onOverflowTap,
                        onBandTap = onBandTap,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    date: LocalDate,
    bands: List<DayBand>,
    inMonth: Boolean,
    isToday: Boolean,
    onDayTap: (LocalDate) -> Unit,
    onOverflowTap: (LocalDate) -> Unit,
    onBandTap: (DayBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleBands = bands.take(MaxChipsPerCell)
    val overflow = (bands.size - MaxChipsPerCell).coerceAtLeast(0)

    val bg = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        inMonth -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val numeralColor = when {
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val cellTag = if (isToday) TestTagMonthTodayCell else "$TestTagMonthCell-$date"

    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable { onDayTap(date) }
            .testTag(cellTag),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = numeralColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            visibleBands.forEach { band ->
                Surface(
                    onClick = { onBandTap(band) },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                        .testTag("$TestTagMonthChip-${band.instance.instanceId}"),
                ) {
                    Text(
                        text = band.instance.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
            if (overflow > 0) {
                Surface(
                    onClick = { onOverflowTap(date) },
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                        .testTag("$TestTagMonthOverflow-$date"),
                ) {
                    Text(
                        text = "+$overflow more",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}
