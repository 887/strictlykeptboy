package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

const val TestTagTimeboxView = "ScheduleTimeboxView"
const val TestTagTimeboxCard = "TimeboxCard"
const val TestTagTimeboxNowCard = "TimeboxNowCard"
const val TestTagTimeboxEmpty = "TimeboxEmpty"

/**
 * Phase G.4 — today's planned focus blocks rendered edge-to-edge as
 * large cards (~120dp tall). The "now" block is visually emphasized.
 *
 * Stateless. Caller passes today's [RenderedSchedule] (single-day
 * range) and the current instant for now-detection.
 */
@Composable
fun ScheduleTimeboxView(
    date: LocalDate,
    schedule: RenderedSchedule?,
    modifier: Modifier = Modifier,
    onBandTap: (DayBand) -> Unit = {},
    /** Phase CCC.10 / HV-G.3 — opens the trip wizard from the empty-state CTA. */
    onPlanTrip: (() -> Unit)? = null,
) {
    val day = schedule?.days?.firstOrNull { it.date == date }
    val bands = day?.bands.orEmpty()

    if (bands.isEmpty()) {
        EmptyScheduleState(
            modifier = modifier.fillMaxSize().testTag(TestTagTimeboxEmpty),
            onPlanTrip = onPlanTrip,
        )
        return
    }

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(30_000L)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagTimeboxView),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        items(bands, key = { it.instance.instanceId }) { band ->
            val isNow = !now.isBefore(band.instance.effectiveStart) &&
                now.isBefore(band.instance.effectiveEnd)
            TimeboxCard(band = band, isNow = isNow, onClick = { onBandTap(band) })
        }
    }
}

@Composable
private fun TimeboxCard(band: DayBand, isNow: Boolean, onClick: () -> Unit) {
    val start = band.instance.effectiveStart
    val end = band.instance.effectiveEnd
    val timeRange = "%02d:%02d → %02d:%02d".format(start.hour, start.minute, end.hour, end.minute)

    val minutesRemaining = if (isNow) {
        ChronoUnit.MINUTES.between(ZonedDateTime.now(), end).coerceAtLeast(0L)
    } else 0L

    val cd = if (isNow) {
        "Currently in ${band.instance.title}, $minutesRemaining minutes remaining, ends at %02d:%02d"
            .format(end.hour, end.minute)
    } else {
        "${band.instance.title}, $timeRange"
    }

    Surface(
        onClick = onClick,
        color = if (isNow) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        border = if (isNow) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isNow) 140.dp else 120.dp)
            .semantics { contentDescription = cd }
            .testTag(if (isNow) TestTagTimeboxNowCard else "$TestTagTimeboxCard-${band.instance.instanceId}"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = if (isNow) "Now — $timeRange" else timeRange,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${band.instance.emoji ?: ""}  ${band.instance.title}".trim(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (isNow) {
                Text(
                    text = "$minutesRemaining min remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
