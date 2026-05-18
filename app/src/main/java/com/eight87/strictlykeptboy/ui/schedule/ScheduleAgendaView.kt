package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val TestTagAgendaView = "ScheduleAgendaView"
const val TestTagAgendaDayHeader = "AgendaDayHeader"
const val TestTagAgendaRow = "AgendaRow"
const val TestTagAgendaEmpty = "AgendaEmpty"

/**
 * Round 2.21 Phase E.2 — Schedule (agenda-list) view.
 *
 * Mirrors Google Calendar's Schedule view: vertical list of days,
 * each day's events as density-1 rows with emoji + color dot + time
 * + title + group badge. Time grid is intentionally absent —
 * Schedule is *list*, not *timeline*. Zoom is ignored (D-2.21.g).
 *
 * Resolver-level range: 7 days starting at the locale week-start
 * (see [ScheduleViewState.rangeAndModeFor]).
 */
@Composable
fun ScheduleAgendaView(
    dates: List<LocalDate>,
    dayBands: DayBandSource,
    modifier: Modifier = Modifier,
    onBandTap: (DayBand) -> Unit = {},
) {
    // Round 2026-05-17 [M] #10 — Agenda needs *which dates* to iterate;
    // host derives this from the rendered range. Per-day bands come
    // from the narrow [DayBandSource].
    val daysWithBands: List<Pair<LocalDate, List<DayBand>>> =
        dates.map { it to dayBands.bandsFor(it) }
    val totalBands = daysWithBands.sumOf { it.second.size }

    if (totalBands == 0) {
        Box(
            modifier = modifier.fillMaxSize().testTag(TestTagAgendaEmpty).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No events in range.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }

    LazyColumn(modifier = modifier.fillMaxSize().testTag(TestTagAgendaView)) {
        daysWithBands.forEach { (date, bands) ->
            if (bands.isEmpty()) return@forEach
            item(key = "agenda-header-$date") {
                AgendaDayHeader(date = date, formatter = dateFormatter)
            }
            // Key must include the bucket date — a multi-day event or a
            // calendar shared across repos can otherwise re-emit the same
            // instanceId across multiple day buckets, crashing LazyColumn
            // with "Key was already used".
            itemsIndexed(
                items = bands,
                key = { idx, b -> "agenda-row-$date-$idx-${b.instance.instanceId}" },
            ) { _, band ->
                AgendaRow(band = band, onBandTap = onBandTap)
            }
        }
    }
}

@Composable
private fun AgendaDayHeader(date: LocalDate, formatter: DateTimeFormatter) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("$TestTagAgendaDayHeader-$date"),
    ) {
        Text(
            text = "${emojiFor(date.dayOfWeek)}  ${formatter.format(date)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AgendaRow(band: DayBand, onBandTap: (DayBand) -> Unit) {
    val start = band.instance.effectiveStart
    val end = band.instance.effectiveEnd
    val tint = band.accentColorSeed?.let {
        Color(0xFF000000.toInt() or (it and 0x00FFFFFF))
    } ?: MaterialTheme.colorScheme.outlineVariant
    val group = band.instance.group
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBandTap(band) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("$TestTagAgendaRow-${band.instance.instanceId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(tint),
        )
        Text(
            text = "%02d:%02d".format(start.hour, start.minute),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (band.instance.emoji?.let { "$it  " } ?: "") + band.instance.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "%02d:%02d–%02d:%02d".format(
                    start.hour, start.minute, end.hour, end.minute,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!group.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = group,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
}

