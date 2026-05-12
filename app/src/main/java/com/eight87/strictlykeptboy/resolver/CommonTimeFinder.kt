package com.eight87.strictlykeptboy.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase E.5 / RV-E — common-time finder.
 *
 * Given a busy set per participant (each participant is a [RepoRef]
 * resolved by the caller into materialized instances), finds free
 * intervals common across every participant that satisfy the duration
 * and time-of-day envelope. Returns up to [topK] candidates ranked by
 * niceness.
 *
 * Caller pre-resolves each participant's busy set with the [Renderer]
 * and hands the materialized lists here. Keeps the finder pure-data
 * and testable without spinning up Room.
 */
class CommonTimeFinder {

    data class Query(
        val participants: List<RepoRef>,
        val busyByParticipant: Map<RepoRef, List<MaterializedInstance>>,
        val range: DateRange,
        val minDurationMinutes: Int,
        val window: TimeWindow,
        val tzId: ZoneId = ZoneId.systemDefault(),
        val idealTimeOfDay: LocalTime? = null,
        val topK: Int = 10,
    )

    suspend fun find(query: Query): List<TimeSlot> = withContext(Dispatchers.Default) {
        require(query.minDurationMinutes > 0) { "minDurationMinutes must be positive" }
        val candidateWindows = enumerateCandidateWindows(query)
        val minLengthMs = query.minDurationMinutes * 60_000L

        // Aggregate all participants' busy intervals into one sweep-source.
        val busy = query.participants
            .flatMap { p ->
                query.busyByParticipant[p].orEmpty()
                    .filter { it.isBusy }
                    .map { it.effectiveInterval.toInstantInterval() }
            }
            .filter { it.lengthMillis() > 0L } // zero-duration events are point-busy, do not consume free time
            .sortedBy { it.from }

        val viable = candidateWindows.flatMap { window ->
            subtractBusy(window, busy).filter { it.lengthMillis() >= minLengthMs }
        }

        val ranked = viable.sortedWith(
            compareByDescending<InstantInterval> { it.lengthMillis() }
                .thenBy { idealDistance(it, query.idealTimeOfDay, query.tzId) }
                .thenBy { it.from },
        )
        ranked.take(query.topK).map { TimeSlot(it.from.atZone(query.tzId), it.toExclusive.atZone(query.tzId)) }
    }

    /**
     * Subtract [busy] intervals from [window]. Sweep-line: walk in
     * sorted order, emit gaps. Point-busy intervals are pre-filtered
     * by the caller.
     */
    internal fun subtractBusy(
        window: InstantInterval,
        busy: List<InstantInterval>,
    ): List<InstantInterval> {
        val overlapping = busy.filter { it.overlaps(window) }
        val gaps = mutableListOf<InstantInterval>()
        var cursor = window.from
        for (b in overlapping) {
            val bStart = maxOf(b.from, window.from)
            val bEnd = minOf(b.toExclusive, window.toExclusive)
            if (bStart.isAfter(cursor)) gaps += InstantInterval(cursor, bStart)
            if (bEnd.isAfter(cursor)) cursor = bEnd
        }
        if (window.toExclusive.isAfter(cursor)) gaps += InstantInterval(cursor, window.toExclusive)
        return gaps
    }

    private fun enumerateCandidateWindows(q: Query): List<InstantInterval> {
        val days = generateSequence(q.range.start) { d ->
            val end = q.range.endInclusive ?: q.range.start
            if (d.isBefore(end)) d.plusDays(1) else null
        }.toList()
        return days.filter { it.dayOfWeek in q.window.daysOfWeek }
            .map { dayWindow(it, q.window, q.tzId) }
    }

    private fun dayWindow(date: LocalDate, w: TimeWindow, tz: ZoneId): InstantInterval {
        val fromZdt = ZonedDateTime.of(date, w.from, tz)
        val toZdt = if (w.toExclusive.isAfter(w.from) || w.toExclusive == w.from) {
            ZonedDateTime.of(date, w.toExclusive, tz)
        } else {
            // window crosses midnight — extend to next day
            ZonedDateTime.of(date.plusDays(1), w.toExclusive, tz)
        }
        return InstantInterval(fromZdt.toInstant(), toZdt.toInstant())
    }

    private fun idealDistance(slot: InstantInterval, ideal: LocalTime?, tz: ZoneId): Long {
        if (ideal == null) return 0L
        val midMs = (slot.from.toEpochMilli() + slot.toExclusive.toEpochMilli()) / 2
        val midLocal = java.time.Instant.ofEpochMilli(midMs).atZone(tz).toLocalTime()
        return kotlin.math.abs(midLocal.toSecondOfDay() - ideal.toSecondOfDay()).toLong()
    }
}
