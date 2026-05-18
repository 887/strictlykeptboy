package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.ui.schedule.colorForSeed
import java.time.format.DateTimeFormatter

const val TestTagTimeboxRow = "TimeboxRow"

/**
 * Round 2.26.C.1 — timebox-band sibling of [TaskRow] for the unified
 * Today feed. Same outer Surface + 4dp accent stripe + 64dp row height
 * as [TaskRow], but the leading stripe is tinted from the calendar's
 * accent seed (not the todolist), the checkbox slot is replaced by a
 * [Icons.Outlined.Schedule] glyph, and the secondary chip reads
 * "HH:mm–HH:mm · CalendarName" (calendar id used as a fallback when no
 * display-name lookup is available at this seam — Phase B/F may wire a
 * resolver later).
 */
@Composable
fun TimeboxRow(
    band: DayBand,
    onTap: (DayBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = colorForSeed(band.accentColorSeed)
    val start = band.instance.effectiveStart
    val end = band.instance.effectiveEnd
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val range = "${fmt.format(start)}–${fmt.format(end)}"
    val calendarLabel = band.instance.calendar.id

    Surface(
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("$TestTagTimeboxRow-${band.instance.instanceId}")
            .clickable { onTap(band) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(accent),
            )
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, end = 4.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Timebox",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = (band.instance.emoji?.let { "$it  " } ?: "") + band.instance.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$range · $calendarLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
