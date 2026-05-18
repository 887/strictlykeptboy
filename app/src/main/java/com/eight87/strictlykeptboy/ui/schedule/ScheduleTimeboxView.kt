package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import com.eight87.strictlykeptboy.ui.share.isForeignBand
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

const val TestTagTimeboxView = "ScheduleTimeboxView"
const val TestTagTimeboxCard = "TimeboxCard"
const val TestTagTimeboxNowCard = "TimeboxNowCard"
const val TestTagTimeboxEmpty = "TimeboxEmpty"
const val TestTagTimeboxRegularSection = "TimeboxRegularSection"
const val TestTagTimeboxRegularCard = "TimeboxRegularCard"
/** Round 2.18.C.5 — external (CalendarContract) account divider label, suffix `-<accountName>`. */
const val TestTagTimeboxExternalAccountHeader = "TimeboxExternalAccountHeader"

/**
 * Phase G.4 — today's planned focus blocks rendered edge-to-edge as
 * large cards (~120dp tall). The "now" block is visually emphasized.
 *
 * Stateless. Caller passes a narrow [DayBandSource] (single-day
 * range) and the current instant for now-detection.
 */
@Composable
fun ScheduleTimeboxView(
    date: LocalDate,
    dayBands: DayBandSource,
    modifier: Modifier = Modifier,
    onBandTap: (DayBand) -> Unit = {},
    /** Phase CCC.10 / HV-G.3 — opens the trip wizard from the empty-state CTA. */
    onPlanTrip: (() -> Unit)? = null,
    /** Round 2.2.C.2 — default-write repo for `isForeignBand`. Empty = chip suppressed. */
    defaultWriteRepoId: String = "",
) {
    val allBands = dayBands.bandsFor(date)
    // Round 2.2.C.9 — primary list is Timebox-kind only; Regular bands flow into a secondary section.
    val timeboxBands = allBands.filter { it.kind == CalendarKind.Timebox }
    val regularBands = allBands.filter { it.kind != CalendarKind.Timebox }
    val bands = timeboxBands

    if (allBands.isEmpty()) {
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
        items(bands, key = { "tb-${it.instance.instanceId}" }) { band ->
            val isNow = !now.isBefore(band.instance.effectiveStart) &&
                now.isBefore(band.instance.effectiveEnd)
            TimeboxCard(
                band = band,
                isNow = isNow,
                onClick = { onBandTap(band) },
                defaultWriteRepoId = defaultWriteRepoId,
            )
        }
        // Round 2.18.C.5 — split regular vs external. External bands group
        // by source account (encoded in repo.id = "system/<at>/<name>")
        // and render under a divider label per account.
        val fileBackedRegular = regularBands.filter { it.kind != CalendarKind.External }
        val externalBands = regularBands.filter { it.kind == CalendarKind.External }
        if (fileBackedRegular.isNotEmpty()) {
            item(key = "regular-section-header") {
                Text(
                    text = "Scheduled events on top of your time blocks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp)
                        .testTag(TestTagTimeboxRegularSection),
                )
            }
            items(fileBackedRegular, key = { "reg-${it.instance.instanceId}" }) { band ->
                RegularSecondaryCard(
                    band = band,
                    onClick = { onBandTap(band) },
                    defaultWriteRepoId = defaultWriteRepoId,
                )
            }
        }
        if (externalBands.isNotEmpty()) {
            val groups: Map<Pair<String, String>, List<DayBand>> = externalBands.groupBy { band ->
                val parts = band.instance.repo.id.split('/', limit = 3)
                if (parts.size == 3 && parts[0] == "system") parts[1] to parts[2] else "" to band.instance.repo.id
            }
            groups.entries.sortedBy { it.key.second }.forEach { (account, bs) ->
                val (accountType, accountName) = account
                item(key = "ext-header-$accountName") {
                    Text(
                        text = if (accountName.isNotBlank()) accountName else accountType,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp)
                            .testTag("$TestTagTimeboxExternalAccountHeader-$accountName"),
                    )
                }
                items(bs, key = { "ext-${it.instance.instanceId}" }) { band ->
                    RegularSecondaryCard(
                        band = band,
                        onClick = { onBandTap(band) },
                        defaultWriteRepoId = defaultWriteRepoId,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegularSecondaryCard(
    band: DayBand,
    onClick: () -> Unit,
    defaultWriteRepoId: String,
) {
    val start = band.instance.effectiveStart
    val end = band.instance.effectiveEnd
    val timeRange = "%02d:%02d → %02d:%02d".format(start.hour, start.minute, end.hour, end.minute)
    val isSuperseded = band.supersededByCalendar != null
    val isOffSchedule = band.offSchedule
    val authorId = band.instance.author?.id
    val showAuthorChip = authorId != null && isForeignBand(band.instance.repo.id, defaultWriteRepoId)
    val alpha = if (isSuperseded) 0.35f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("$TestTagTimeboxRegularCard-${band.instance.instanceId}"),
    ) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = alpha),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BandKindGlyph(kind = band.kind)
                        if (isSuperseded) {
                            Spacer(Modifier.width(4.dp))
                            BandSupersededGlyph()
                        }
                        if (isOffSchedule) {
                            Spacer(Modifier.width(4.dp))
                            BandOffScheduleGlyph()
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = (band.instance.emoji?.let { "$it  " } ?: "") + band.instance.title,
                            style = MaterialTheme.typography.titleSmall,
                            textDecoration = if (isSuperseded) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = timeRange,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showAuthorChip) {
                    BandAuthorChip(
                        authorId = authorId!!,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
        }
        if (isOffSchedule) {
            BandDashedBorder(color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TimeboxCard(
    band: DayBand,
    isNow: Boolean,
    onClick: () -> Unit,
    defaultWriteRepoId: String,
) {
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

    val isSuperseded = band.supersededByCalendar != null
    val isOffSchedule = band.offSchedule
    val bandAlpha = if (isSuperseded) 0.35f else 1f
    val authorId = band.instance.author?.id
    val showAuthorChip = authorId != null && isForeignBand(band.instance.repo.id, defaultWriteRepoId)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isNow) 140.dp else 120.dp),
    ) {
        Surface(
            onClick = onClick,
            color = (if (isNow) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainer).copy(alpha = bandAlpha),
            shape = RoundedCornerShape(20.dp),
            border = if (isNow) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = cd }
                .testTag(if (isNow) TestTagTimeboxNowCard else "$TestTagTimeboxCard-${band.instance.instanceId}"),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 4-dp left color stripe (2.2.C.1-paint)
                BandLeftStripe(seed = band.accentColorSeed)
                Column(modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BandKindGlyph(kind = band.kind)
                        if (isSuperseded) {
                            Spacer(Modifier.width(4.dp))
                            BandSupersededGlyph()
                        }
                        if (isOffSchedule) {
                            Spacer(Modifier.width(4.dp))
                            BandOffScheduleGlyph()
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isNow) "Now — $timeRange" else timeRange,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${band.instance.emoji ?: ""}  ${band.instance.title}".trim(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                        textDecoration = if (isSuperseded) TextDecoration.LineThrough else null,
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
                if (showAuthorChip) {
                    BandAuthorChip(
                        authorId = authorId!!,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
            }
        }
        if (isOffSchedule) {
            BandDashedBorder(
                color = MaterialTheme.colorScheme.error,
                cornerRadiusDp = 20.dp,
            )
        }
    }
}
