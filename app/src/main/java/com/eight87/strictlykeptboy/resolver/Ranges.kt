package com.eight87.strictlykeptboy.resolver

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Open-ended on the upper bound iff [endInclusive] is `null` (per RV-A). */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate?) {
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && (endInclusive == null || !date.isAfter(endInclusive))
}

/**
 * Active-hours predicate, evaluated in the calendar's tz.
 *
 * `from` inclusive, `to` exclusive (RV-A locked decision).
 * `to <= from` (but not equal) means the range crosses midnight.
 * `to == from` is an explicit "never active" signal.
 */
data class HourRange(val day: DayOfWeek, val from: LocalTime, val to: LocalTime)

/** A frozen [from, to] half-open instant interval. */
data class InstantInterval(val from: Instant, val toExclusive: Instant) {
    init { require(!toExclusive.isBefore(from)) { "InstantInterval inverted" } }
    fun overlaps(other: InstantInterval): Boolean =
        from.isBefore(other.toExclusive) && other.from.isBefore(toExclusive)
    fun lengthMillis(): Long = toExclusive.toEpochMilli() - from.toEpochMilli()
}

/** Closed half-open `[from, to)` zoned interval used by the renderer. */
data class ZonedInterval(val from: ZonedDateTime, val toExclusive: ZonedDateTime) {
    fun toInstantInterval(): InstantInterval =
        InstantInterval(from.toInstant(), toExclusive.toInstant())
    fun lengthMillis(): Long = java.time.Duration.between(from, toExclusive).toMillis()
}
