package com.eight87.strictlykeptboy.system

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Round 2.18.E.7 — minimal VCALENDAR parser used by the system-intent
 * `.ics` import flow.
 *
 * Scope: parse RFC 5545 line-folded VCALENDAR text into a list of
 * [ParsedIcsEvent]s. Each event carries:
 *
 *   - core fields: SUMMARY / DESCRIPTION / LOCATION / DTSTART / DTEND /
 *     all-day flag / UID
 *   - RRULE (verbatim string, expanded later by `lib-recur`)
 *   - ATTENDEEs (CN, email)
 *   - VALARM TRIGGERs (offset relative to start, minutes-before-start)
 *
 * Why not [com.eight87.strictlykeptboy.port.ics.IcsParser]? That
 * parser was authored for Phase P / NS-I and emits skb-native
 * `Event` / `RecurrenceRule` / `Exception` rows (TOML-frontmatter
 * shaped). The Round 2.18.E import path needs attendee + alarm
 * records too, which the Phase P shape never carried — so a parallel
 * parser lives here while the on-disk one stays unchanged.
 *
 * Line unfolding (CRLF + leading SP/HTAB) per RFC 5545 §3.1. Property
 * parameters tolerated; TZID retained, others discarded. Malformed
 * VEVENT blocks raise an entry in [ParsedIcs.warnings] but never abort
 * the parse.
 *
 * No new dependency was added — `ical4j-core` is declared in
 * `gradle/libs.versions.toml` for a future round when we need full
 * VTIMEZONE handling, but the Phase E import payload is well-served
 * by this hand-rolled subset and avoids the Android-side classpath
 * cost.
 */
object IcsParser {

    fun parse(text: String): ParsedIcs {
        val unfolded = unfold(text)
        val lines = unfolded.lines()
        val events = mutableListOf<ParsedIcsEvent>()
        val warnings = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i].trim()
            if (raw.equals("BEGIN:VEVENT", ignoreCase = true)) {
                val end = findEnd(lines, i, "END:VEVENT")
                if (end < 0) {
                    warnings += "unterminated VEVENT at line ${i + 1}"
                    break
                }
                runCatching {
                    materialize(lines.subList(i + 1, end))
                }.fold(
                    onSuccess = { events += it },
                    onFailure = { t ->
                        warnings += "VEVENT at line ${i + 1}: ${t.message ?: t::class.simpleName}"
                    },
                )
                i = end + 1
            } else {
                i++
            }
        }
        return ParsedIcs(events = events, warnings = warnings)
    }

    private fun materialize(body: List<String>): ParsedIcsEvent {
        val attendees = mutableListOf<ParsedAttendee>()
        val alarms = mutableListOf<ParsedReminder>()
        val flat = mutableListOf<Prop>()
        var i = 0
        while (i < body.size) {
            val line = body[i].trim()
            if (line.equals("BEGIN:VALARM", ignoreCase = true)) {
                val end = findEnd(body, i, "END:VALARM")
                if (end < 0) {
                    throw IllegalStateException("unterminated VALARM")
                }
                materializeAlarm(body.subList(i + 1, end))?.let { alarms += it }
                i = end + 1
                continue
            }
            val p = parseLine(line)
            if (p == null) { i++; continue }
            if (p.name.equals("ATTENDEE", ignoreCase = true)) {
                attendees += parseAttendee(p)
            } else {
                flat += p
            }
            i++
        }

        val uid = flat.firstValue("UID")
        val summary = unescape(flat.firstValue("SUMMARY") ?: "(no title)")
        val description = unescape(flat.firstValue("DESCRIPTION") ?: "")
        val location = flat.firstValue("LOCATION")?.let(::unescape)
        val dtstart = flat.first("DTSTART") ?: error("missing DTSTART")
        val dtend = flat.first("DTEND")
        val duration = flat.firstValue("DURATION")
        val rrule = flat.firstValue("RRULE")

        val allDay = dtstart.params["VALUE"]?.equals("DATE", ignoreCase = true) == true ||
            (dtstart.value.length == 8 && !dtstart.value.contains('T'))
        val (start, end) = computeTimes(dtstart, dtend, duration, allDay)

        return ParsedIcsEvent(
            uid = uid,
            summary = summary,
            description = description,
            location = location,
            start = start,
            end = end,
            allDay = allDay,
            rrule = rrule,
            attendees = attendees.toList(),
            reminders = alarms.toList(),
        )
    }

    private fun materializeAlarm(body: List<String>): ParsedReminder? {
        val props = body.mapNotNull { parseLine(it.trim()) }
        val trigger = props.firstOrNull { it.name.equals("TRIGGER", ignoreCase = true) }
            ?: return null
        // RFC 5545 §3.8.6.3: TRIGGER can be a DURATION (relative, default
        // RELATED=START) or a DATE-TIME (absolute). We surface a
        // minutes-before-start integer; absolute triggers are recorded as
        // their epoch-second.
        val raw = trigger.value
        val related = trigger.params["RELATED"]?.uppercase() ?: "START"
        return try {
            if (raw.startsWith("P") || raw.startsWith("-P") || raw.startsWith("+P")) {
                val d = parseTriggerDuration(raw)
                // negative duration ⇒ before start.
                val minutesBefore = (-d.toMinutes()).toInt().coerceAtLeast(0)
                ParsedReminder(
                    minutesBeforeStart = minutesBefore,
                    relatedToStart = related == "START",
                )
            } else {
                // absolute DATE-TIME — we don't surface as minutesBeforeStart
                // (the import-screen UI won't model this for v1); skip.
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseAttendee(p: Prop): ParsedAttendee {
        val cn = p.params["CN"]
        // Per RFC: value is `mailto:foo@bar`. Strip the mailto: prefix.
        val raw = p.value.trim()
        val email = if (raw.startsWith("mailto:", ignoreCase = true)) raw.substring(7) else raw
        return ParsedAttendee(commonName = cn, email = email.ifBlank { null })
    }

    // ---------- shared helpers (small duplicate of port/ics/IcsParser
    // internals — kept private to this file for SRP and to avoid
    // coupling the disk-shape parser to attendee/alarm extensions). ---

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    private fun List<Prop>.first(name: String): Prop? =
        firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun List<Prop>.firstValue(name: String): String? = first(name)?.value

    private fun parseLine(line: String): Prop? {
        if (line.isEmpty()) return null
        if (line.startsWith("BEGIN:", ignoreCase = true) ||
            line.startsWith("END:", ignoreCase = true)
        ) return null
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = head.split(';')
        val name = parts[0]
        val params = parts.drop(1).mapNotNull {
            val eq = it.indexOf('=')
            if (eq < 0) null else it.substring(0, eq).uppercase() to it.substring(eq + 1)
        }.toMap()
        return Prop(name, params, value)
    }

    internal fun unfold(text: String): String {
        val lf = text.replace("\r\n", "\n").replace('\r', '\n')
        val sb = StringBuilder(lf.length)
        var i = 0
        while (i < lf.length) {
            val c = lf[i]
            if (c == '\n' && i + 1 < lf.length && (lf[i + 1] == ' ' || lf[i + 1] == '\t')) {
                i += 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun findEnd(lines: List<String>, from: Int, marker: String): Int {
        for (j in from + 1 until lines.size) {
            if (lines[j].trim().equals(marker, ignoreCase = true)) return j
        }
        return -1
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    '\\', ',', ';' -> sb.append(n)
                    else -> { sb.append(c); sb.append(n) }
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun computeTimes(
        dtstart: Prop,
        dtend: Prop?,
        duration: String?,
        allDay: Boolean,
    ): Pair<ZonedDateTime, ZonedDateTime> {
        val tz = dtstart.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneOffset.UTC
        if (allDay) {
            val s = LocalDate.parse(dtstart.value, BASIC_DATE)
            val e = dtend?.let { LocalDate.parse(it.value, BASIC_DATE) } ?: s.plusDays(1)
            return s.atStartOfDay(tz) to e.atStartOfDay(tz)
        }
        val s = parseDateTime(dtstart.value, tz)
        val e = when {
            dtend != null -> parseDateTime(dtend.value, tz)
            duration != null -> s.plus(Duration.parse(duration))
            else -> s.plus(Duration.ofHours(1))
        }
        return s to e
    }

    private fun parseDateTime(v: String, tz: ZoneId): ZonedDateTime {
        // `Z` suffix forces UTC regardless of TZID.
        return if (v.endsWith("Z")) {
            LocalDateTime.parse(v.dropLast(1), BASIC_DATE_TIME).atZone(ZoneOffset.UTC)
        } else {
            LocalDateTime.parse(v, BASIC_DATE_TIME).atZone(tz)
        }
    }

    private fun parseTriggerDuration(raw: String): Duration {
        // ical4j accepts e.g. `-PT15M` / `P1D`. `java.time.Duration.parse`
        // accepts the same form.
        return Duration.parse(raw)
    }

    private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val BASIC_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}

/** Output of [IcsParser.parse]. */
data class ParsedIcs(
    val events: List<ParsedIcsEvent>,
    val warnings: List<String> = emptyList(),
)

/**
 * One VEVENT after parsing. Carries the fields needed to either:
 *   - present a preview in [com.eight87.strictlykeptboy.ui.import_export.IcsImportScreen]
 *   - hand off to [com.eight87.strictlykeptboy.store.EntityWriter] for
 *     a skb-repo import, or
 *   - hand off to [com.eight87.strictlykeptboy.system.CalendarContractWriter]
 *     for an external-calendar import.
 */
data class ParsedIcsEvent(
    val uid: String?,
    val summary: String,
    val description: String,
    val location: String?,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val allDay: Boolean,
    val rrule: String? = null,
    val attendees: List<ParsedAttendee> = emptyList(),
    val reminders: List<ParsedReminder> = emptyList(),
)

data class ParsedAttendee(
    val commonName: String? = null,
    val email: String? = null,
)

data class ParsedReminder(
    /** Number of minutes before the event start. */
    val minutesBeforeStart: Int,
    val relatedToStart: Boolean = true,
)
