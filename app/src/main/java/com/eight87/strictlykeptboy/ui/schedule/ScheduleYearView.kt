package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

const val TestTagYearView = "ScheduleYearView"
const val TestTagYearMonth = "YearMonth"
const val TestTagYearMonthCell = "YearMonthCell"

/**
 * Phase G.5 — 12-month heat-map.
 *
 * 3 cols × 4 rows on phone portrait, 4 cols × 3 rows on landscape/tablet
 * (per UI-H / G.5 brief).
 */
@Composable
fun ScheduleYearView(
    year: Int,
    schedule: RenderedSchedule?,
    modifier: Modifier = Modifier,
    onMonthTap: (Month) -> Unit = {},
) {
    val countsByDate: Map<LocalDate, Int> =
        schedule?.days?.associate { it.date to it.bands.size }.orEmpty()

    val isLandscape = LocalConfiguration.current.screenWidthDp >= 600 ||
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cols = if (isLandscape) 4 else 3

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = modifier.fillMaxSize().testTag(TestTagYearView),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        items(Month.entries) { month ->
            MiniMonth(
                year = year,
                month = month,
                countsByDate = countsByDate,
                onTap = { onMonthTap(month) },
            )
        }
    }
}

@Composable
private fun MiniMonth(
    year: Int,
    month: Month,
    countsByDate: Map<LocalDate, Int>,
    onTap: () -> Unit,
) {
    val ym = YearMonth.of(year, month)
    val firstDow = WeekFields.ISO.firstDayOfWeek
    val firstOfMonth = ym.atDay(1)
    val leadingBlanks = ((firstOfMonth.dayOfWeek.value - firstDow.value) + 7) % 7

    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable(onClick = onTap)
            .testTag("$TestTagYearMonth-${month.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // 6 rows × 7 cols of tiny cells.
        repeat(6) { rowIdx ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { colIdx ->
                    val cellIdx = rowIdx * 7 + colIdx
                    val dayOfMonth = cellIdx - leadingBlanks + 1
                    if (dayOfMonth in 1..ym.lengthOfMonth()) {
                        val date = ym.atDay(dayOfMonth)
                        val count = countsByDate[date] ?: 0
                        val intensity = intensityForCount(count)
                        val baseColor = MaterialTheme.colorScheme.primary
                        val color = if (count == 0) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            baseColor.copy(alpha = intensity)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .size(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                                .testTag("$TestTagYearMonthCell-${month.name}-$dayOfMonth"),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).padding(1.dp).size(14.dp))
                    }
                }
            }
        }
    }
}

/** Buckets: 0 = transparent, 1 = 0.2, 2 = 0.4, 3 = 0.6, 4 = 0.8, 5+ = 1.0. */
internal fun intensityForCount(count: Int): Float = when {
    count <= 0 -> 0f
    count == 1 -> 0.2f
    count == 2 -> 0.4f
    count == 3 -> 0.6f
    count == 4 -> 0.8f
    else -> 1.0f
}
