package com.eight87.strictlykeptboy.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Phase E.2 / RV-B — recurrence materializer.
 *
 * Backed by dmfs lib-recur (D.5). The materializer is stateless and
 * thread-safe; expansion runs on `Dispatchers.Default` since it's CPU
 * bound and may chew through thousands of dates for a year-view query.
 *
 * v1 single-tz support: every rule expands in its own `tzId` and we
 * convert results to that tz for the renderer. RV-H (multi-tz semantics)
 * is a future phase; the API here is already shaped for it (instances
 * carry zoned times, not floating).
 */
class RecurrenceMaterializer {

    /**
     * Expand [rule] across [range], applying [exceptions] (cancel / move
     * / note). Returns materialized instances clipped to `[range.start,
     * range.endInclusive + 1d)`.
     */
    suspend fun expand(
        rule: RecurrenceInput,
        exceptions: List<ExceptionInput>,
        range: DateRange,
    ): List<MaterializedInstance> = withContext(Dispatchers.Default) {
        if (!rule.active) return@withContext emptyList()

        val parsed = RecurrenceRule(normalizedRrule(rule.rrule))
        val tz = TimeZone.getTimeZone(rule.tzId.id)
        val startInstant = rule.dtstart.toInstant().toEpochMilli()
        val rangeStartInstant = range.start.atStartOfDay(rule.tzId).toInstant().toEpochMilli()
        val rangeEndExclusive = (range.endInclusive ?: range.start.plusYears(10))
            .plusDays(1).atStartOfDay(rule.tzId).toInstant().toEpochMilli()

        val it = parsed.iterator(DateTime(tz, startInstant))
        // fastForward is faster than iterating from DTSTART when range is far in future
        if (rangeStartInstant > startInstant) it.fastForward(DateTime(tz, rangeStartInstant))

        val rawStarts = mutableListOf<ZonedDateTime>()
        var guard = 0
        val ceiling = 100_000 // safety net against runaway infinite rules
        while (it.hasNext() && guard < ceiling) {
            val dt = it.nextDateTime()
            val ms = dt.timestamp
            if (ms >= rangeEndExclusive) break
            rawStarts += Instant.ofEpochMilli(ms).atZone(rule.tzId)
            guard++
        }

        // Splice in RDATEs that fall inside range; subtract EXDATEs.
        val exdateSet = rule.exdates.map { it.toInstant().toEpochMilli() }.toHashSet()
        val rdateExtras = rule.rdates
            .filter {
                val ms = it.toInstant().toEpochMilli()
                ms in rangeStartInstant until rangeEndExclusive && ms !in exdateSet
            }
        val starts = (rawStarts.filter { it.toInstant().toEpochMilli() !in exdateSet } + rdateExtras)
            .sortedBy { it.toInstant() }

        val excByDate = exceptions.groupBy { it.instanceDate }
            .mapValues { it.value.first() }

        starts.mapNotNull<ZonedDateTime, MaterializedInstance> { start ->
            val occurrenceDate = start.toLocalDate()
            val baseEnd = start.plus(rule.duration)
            val ex = excByDate[occurrenceDate]
            when (ex?.mode) {
                "cancel" -> null
                "move", "override" -> {
                    val effStart = ex.overrideStart ?: start
                    val effEnd = ex.overrideEnd ?: effStart.plus(rule.duration)
                    materializedFromRule(
                        rule = rule,
                        originalStart = start,
                        originalEnd = baseEnd,
                        effectiveStart = effStart,
                        effectiveEnd = effEnd,
                        title = ex.overrideTitle ?: rule.title,
                        body = rule.body,
                    )
                }
                "note" -> materializedFromRule(
                    rule = rule,
                    originalStart = start,
                    originalEnd = baseEnd,
                    effectiveStart = start,
                    effectiveEnd = baseEnd,
                    title = rule.title,
                    body = appendNote(rule.body, ex.noteBody),
                )
                else -> materializedFromRule(
                    rule = rule,
                    originalStart = start,
                    originalEnd = baseEnd,
                    effectiveStart = start,
                    effectiveEnd = baseEnd,
                    title = rule.title,
                    body = rule.body,
                )
            }
        }
    }

    /**
     * One-shot helper for the renderer: lift a single-instance event into
     * the same shape that [expand] produces. Lets the OverlayResolver
     * treat one-offs and rule instances uniformly.
     */
    fun fromOneOff(event: EventInput): MaterializedInstance = MaterializedInstance(
        source = InstanceSource.OneOff(event.ref),
        calendar = event.calendar,
        repo = event.repo,
        originalStart = event.start,
        originalEnd = event.end,
        effectiveStart = event.start,
        effectiveEnd = event.end,
        title = event.title,
        body = event.body,
        emoji = event.emoji,
        tags = event.tags,
        isPrivate = event.isPrivate,
        isBusy = event.isBusy,
        isAllDay = event.isAllDay,
        priorityOverride = event.priorityOverride,
        author = event.author,
        external = event.external,
        group = event.group,
    )

    private fun materializedFromRule(
        rule: RecurrenceInput,
        originalStart: ZonedDateTime,
        originalEnd: ZonedDateTime,
        effectiveStart: ZonedDateTime,
        effectiveEnd: ZonedDateTime,
        title: String,
        body: String,
    ): MaterializedInstance = MaterializedInstance(
        source = InstanceSource.RuleInstance(rule.rule, originalStart),
        calendar = rule.calendar,
        repo = rule.repo,
        originalStart = originalStart,
        originalEnd = originalEnd,
        effectiveStart = effectiveStart,
        effectiveEnd = effectiveEnd,
        title = title,
        body = body,
        emoji = rule.emoji,
        tags = rule.tags,
        isPrivate = rule.isPrivate,
        isBusy = rule.isBusy,
        author = rule.author,
        group = rule.group,
    )

    /**
     * RFC5545 says COUNT and UNTIL are mutually exclusive. If a user
     * hand-wrote both, defensively keep COUNT and drop UNTIL — lib-recur
     * throws otherwise and we'd surface a useless stacktrace. C.8 will
     * surface a validator warning at parse time.
     */
    private fun normalizedRrule(s: String): String {
        if (!s.contains("COUNT", ignoreCase = true) || !s.contains("UNTIL", ignoreCase = true))
            return s
        return s.split(";")
            .filterNot { it.trim().startsWith("UNTIL", ignoreCase = true) }
            .joinToString(";")
    }

    private fun appendNote(base: String, note: String?): String =
        if (note.isNullOrBlank()) base else "$base\n\n---\n$note"
}

/** Convenience: clip an instance to `[from, to)`. Used by the renderer. */
internal fun MaterializedInstance.clippedTo(
    from: ZonedDateTime,
    toExclusive: ZonedDateTime,
): MaterializedInstance? {
    if (!effectiveStart.isBefore(toExclusive) || !effectiveEnd.isAfter(from)) return null
    return this
}

internal fun LocalDate.atZone(zoneId: ZoneId): ZonedDateTime =
    this.atStartOfDay(zoneId)
