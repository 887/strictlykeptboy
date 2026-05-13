package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Phase BBB.1 / DM-AA — calendar-level supersedence + off-schedule
 * baseline-cadence schema.
 *
 * On disk (per HV-E.1 + HV-N.4) `calendar.toml` may carry:
 *
 * ```toml
 * supersedes = ["cal-routine-morning", "cal-workout"]
 * superseded_during = [{ from = 2026-07-01, to = 2026-07-14 }]
 * nonSuperseable = false
 *
 * [baseline_cadence]
 * weekdays = ["MON", "TUE", "WED", "THU", "FRI"]
 * window   = ["09:00", "17:00"]
 * timezone = "Europe/Berlin"
 * ```
 *
 * SOLID-S: this file's single responsibility is the on-disk⇄in-memory
 * codec for the supersedence + baseline-cadence calendar additions.
 * SOLID-D: no Android imports; UI / resolver consume the parsed shape.
 * Round-trip-tested via [SupersedenceConfigParseTest].
 *
 * Invariants enforced (D.78 / DM-AA.5):
 *  - S3 self-supersede: rejected at read-time (the parsed list excludes
 *    the host calendar's own id when the caller provides it).
 *  - DM-AA.5 negative range: `to < from` ⇒ skipped.
 *  - DM-AA.5 cross-repo refs: not enforced here (calendar-id is a
 *    free-form string; the resolver layer's validator enforces).
 *
 * The Round-1 [RoutineCalendarConfig] addition stayed orthogonal —
 * routine calendars may carry both, neither, or one of these blocks.
 */
data class SupersedenceConfig(
    /** Calendar IDs this calendar suppresses while its active-windows match. */
    val supersedes: List<String> = emptyList(),
    /** Date ranges restricting supersedence (empty ⇒ always-on while calendar active). */
    val supersededDuring: List<DateRange> = emptyList(),
    /** When true, this calendar (and its events) ignore all supersedence (S4 + DM-AA.4). */
    val nonSuperseable: Boolean = false,
    /** Optional [baseline_cadence] block for off-schedule detection (RV-Q). */
    val baselineCadence: BaselineCadence? = null,
) {
    /** Inclusive-on-both-ends date interval; `to < from` is rejected upstream. */
    data class DateRange(val from: LocalDate, val to: LocalDate) {
        init {
            require(!to.isBefore(from)) { "DateRange inverted ($from..$to)" }
        }
        fun contains(d: LocalDate): Boolean =
            !d.isBefore(from) && !d.isAfter(to)
    }

    /** RV-Q baseline-cadence — weekday × time-window envelope. */
    data class BaselineCadence(
        val weekdays: Set<java.time.DayOfWeek>,
        val windowStart: LocalTime,
        val windowEndExclusive: LocalTime,
        val timezone: ZoneId,
    )

    companion object {
        /**
         * Read the additive supersedence + baseline-cadence fields out of
         * a `calendar.toml` table. Unknown / missing fields ⇒ defaults.
         * The optional [hostCalendarId] is excluded from the `supersedes`
         * list to enforce invariant S3 (self-supersede rejection).
         */
        fun read(table: TomlTable, hostCalendarId: String? = null): SupersedenceConfig {
            val supersedes = (table.getStringArray("supersedes") ?: emptyList())
                .filter { it.isNotBlank() && it != hostCalendarId }
            val supersededDuring = readDateRangeArray(table, "superseded_during")
            val nonSuperseable = table.getBool("nonSuperseable")
                ?: table.getBool("non_superseable")
                ?: false
            val baselineCadence = table.sections["baseline_cadence"]?.let { parseBaseline(it) }
            return SupersedenceConfig(
                supersedes = supersedes,
                supersededDuring = supersededDuring,
                nonSuperseable = nonSuperseable,
                baselineCadence = baselineCadence,
            )
        }

        fun readFrom(calendarTomlPath: Path, hostCalendarId: String? = null): SupersedenceConfig {
            val bytes = Files.readAllBytes(calendarTomlPath)
            val text = String(bytes, StandardCharsets.UTF_8)
            return read(TomlReader.parse(text), hostCalendarId)
        }

        private fun readDateRangeArray(table: TomlTable, key: String): List<DateRange> {
            val rows = table.aotables[key] ?: return emptyList()
            val out = mutableListOf<DateRange>()
            for (row in rows) {
                val from = row.getDateLike("from")?.let { LocalDate.parse(it.take(10)) } ?: continue
                val to = row.getDateLike("to")?.let { LocalDate.parse(it.take(10)) } ?: continue
                if (to.isBefore(from)) continue // DM-AA.5 reject negative ranges silently
                out += DateRange(from, to)
            }
            return out
        }

        private fun parseBaseline(t: TomlTable): BaselineCadence? {
            val weekdayStrings = t.getStringArray("weekdays") ?: return null
            val weekdays = weekdayStrings.mapNotNull { parseDayOfWeek(it) }.toSet()
            if (weekdays.isEmpty()) return null
            val window = t.getStringArray("window") ?: return null
            if (window.size != 2) return null
            val from = parseLocalTimeOrNull(window[0]) ?: return null
            val to = parseLocalTimeOrNull(window[1]) ?: return null
            val tz = t.getString("timezone")?.let {
                runCatching { ZoneId.of(it) }.getOrNull()
            } ?: ZoneId.systemDefault()
            return BaselineCadence(weekdays, from, to, tz)
        }

        private fun parseDayOfWeek(raw: String): java.time.DayOfWeek? {
            val up = raw.trim().uppercase()
            return when (up) {
                "MON", "MONDAY" -> java.time.DayOfWeek.MONDAY
                "TUE", "TUES", "TUESDAY" -> java.time.DayOfWeek.TUESDAY
                "WED", "WEDNESDAY" -> java.time.DayOfWeek.WEDNESDAY
                "THU", "THUR", "THURS", "THURSDAY" -> java.time.DayOfWeek.THURSDAY
                "FRI", "FRIDAY" -> java.time.DayOfWeek.FRIDAY
                "SAT", "SATURDAY" -> java.time.DayOfWeek.SATURDAY
                "SUN", "SUNDAY" -> java.time.DayOfWeek.SUNDAY
                else -> null
            }
        }

        private fun parseLocalTimeOrNull(raw: String): LocalTime? =
            runCatching { LocalTime.parse(raw.trim()) }.getOrNull()
    }

    /** Write additive fields into [table]. Existing non-supersedence fields are untouched. */
    fun writeInto(table: TomlTable) {
        if (supersedes.isNotEmpty()) table.putStringArray("supersedes", supersedes)
        if (supersededDuring.isNotEmpty()) {
            val rows = supersededDuring.map { r ->
                TomlTable().apply {
                    putLocalDate("from", r.from.toString())
                    putLocalDate("to", r.to.toString())
                }
            }.toMutableList()
            table.aotables["superseded_during"] = rows
        }
        if (nonSuperseable) table.putBool("nonSuperseable", true)
        baselineCadence?.let { bc ->
            val sub = TomlTable().apply {
                putStringArray(
                    "weekdays",
                    bc.weekdays
                        .sortedBy { it.value }
                        .map { it.name.take(3) },
                )
                putStringArray("window", listOf(bc.windowStart.toString(), bc.windowEndExclusive.toString()))
                putString("timezone", bc.timezone.id)
            }
            table.sections["baseline_cadence"] = sub
        }
    }

    /** RV-Q.2 helper: is [zonedStart] (in the cadence's tz) inside the envelope? */
    fun isWithinBaseline(zonedStart: java.time.ZonedDateTime): Boolean {
        val bc = baselineCadence ?: return true // missing block ⇒ no flagging
        val local = zonedStart.withZoneSameInstant(bc.timezone)
        if (local.dayOfWeek !in bc.weekdays) return false
        val t = local.toLocalTime()
        return !t.isBefore(bc.windowStart) && t.isBefore(bc.windowEndExclusive)
    }
}
