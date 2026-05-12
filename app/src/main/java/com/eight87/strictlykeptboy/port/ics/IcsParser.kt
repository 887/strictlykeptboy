package com.eight87.strictlykeptboy.port.ics

import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception
import com.eight87.strictlykeptboy.store.RecurrenceRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Phase P / NS-I — minimal RFC 5545 parser.
 *
 * Hand-rolled VEVENT subset:
 *
 *   - VCALENDAR / VEVENT envelope
 *   - SUMMARY, DESCRIPTION, LOCATION, UID
 *   - DTSTART, DTEND (both DATE-TIME with optional Z, and DATE for all-day)
 *   - RRULE → kept verbatim as the rule's [RecurrenceRule.rrule] string
 *   - EXDATE → emitted as [Exception] (kind = cancel)
 *   - VTIMEZONE: dropped (parameter `TZID=` retained on the RRULE rule for round-trip)
 *
 * Line unfolding (CRLF + leading space/tab continuation) per RFC 5545 §3.1.
 * Property parameters tolerated and partially preserved (TZID, VALUE).
 *
 * UIDs that aren't UUIDv7-shaped are preserved as `external_uid` in the
 * emitted entity, with a fresh UUIDv7 minted for the entity ID.
 *
 * **Liskov (R.X.5):** every public surface is implemented; no
 * NotImplementedError fallbacks.
 */
object IcsParser {

    fun parse(text: String, calendarId: String, author: String, now: OffsetDateTime = OffsetDateTime.now()): IcsParseReport {
        val unfolded = unfold(text)
        val events = mutableListOf<Event>()
        val rules = mutableListOf<RecurrenceRule>()
        val exceptions = mutableListOf<Exception>()
        val warnings = mutableListOf<String>()

        val lines = unfolded.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.equals("BEGIN:VEVENT", ignoreCase = true)) {
                val end = findEnd(lines, i, "END:VEVENT")
                if (end < 0) {
                    warnings += "unterminated VEVENT at line ${i + 1}"
                    break
                }
                try {
                    materialize(lines.subList(i + 1, end), calendarId, author, now, events, rules, exceptions)
                } catch (t: Throwable) {
                    warnings += "VEVENT at line ${i + 1}: ${t.message ?: t::class.simpleName}"
                }
                i = end + 1
            } else {
                i++
            }
        }
        return IcsParseReport(events, rules, exceptions, warnings)
    }

    // --- envelope ----------------------------------------------------------

    private fun findEnd(lines: List<String>, from: Int, marker: String): Int {
        for (j in from + 1 until lines.size) {
            if (lines[j].trim().equals(marker, ignoreCase = true)) return j
        }
        return -1
    }

    /** RFC 5545 §3.1 — a CRLF followed by SP/HTAB is continuation. */
    internal fun unfold(text: String): String {
        // Normalise to LF first, then unfold "\n[ \t]" → "".
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

    // --- VEVENT materialization -------------------------------------------

    private fun materialize(
        body: List<String>,
        calendarId: String,
        author: String,
        now: OffsetDateTime,
        events: MutableList<Event>,
        rules: MutableList<RecurrenceRule>,
        exceptions: MutableList<Exception>,
    ) {
        val props = parseProperties(body)
        val uid = props.firstValue("UID")
        val summary = unescape(props.firstValue("SUMMARY") ?: "(no title)")
        val description = unescape(props.firstValue("DESCRIPTION") ?: "")
        val location = props.firstValue("LOCATION")?.let(::unescape)

        val dtstartProp = props.first("DTSTART") ?: error("missing DTSTART")
        val dtendProp = props.first("DTEND")
        val durationProp = props.firstValue("DURATION")

        val allDay = (dtstartProp.params["VALUE"]?.equals("DATE", ignoreCase = true) == true) ||
            (dtstartProp.value.length == 8 && !dtstartProp.value.contains('T'))

        val (startIso, endIso, tzId) = computeTimes(dtstartProp, dtendProp, durationProp, allDay)

        val rrule = props.firstValue("RRULE")
        val exdates = props.all("EXDATE").flatMap { it.value.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }

        val entityId = makeId(uid)
        val externalUid = uid?.takeIf { !isUuidShaped(it) }

        val nowIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val header = EntityHeader(
            id = entityId.toString(),
            createdAt = nowIso,
            updatedAt = nowIso,
            author = author,
        )

        if (rrule != null) {
            val rule = RecurrenceRule(
                header = header,
                title = summary,
                dtstart = stripZ(startIso),
                duration = computeDurationIso(startIso, endIso, durationProp),
                tzId = tzId ?: "UTC",
                rrule = rrule,
                calendarId = calendarId,
                exdate = exdates.mapNotNull { normaliseExdate(it) },
                location = location,
                body = description,
            )
            rules += rule
            // Each EXDATE → an Exception with mode = "cancel" (D.34).
            for (raw in exdates) {
                val date = exdateToLocalDate(raw) ?: continue
                exceptions += Exception(
                    header = EntityHeader(
                        id = Uuid7.generate().toString(),
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        author = author,
                    ),
                    ruleId = rule.id,
                    instanceDate = date,
                    mode = "cancel",
                    calendarId = calendarId,
                )
            }
        } else {
            events += Event(
                header = header,
                title = summary,
                start = startIso,
                end = endIso,
                calendarId = calendarId,
                allDay = allDay,
                location = location,
                externalUid = externalUid,
                body = description,
            )
        }
    }

    private fun makeId(uid: String?): java.util.UUID {
        if (uid != null && isUuidShaped(uid)) {
            return runCatching { java.util.UUID.fromString(uid) }.getOrElse { Uuid7.generate() }
        }
        return Uuid7.generate()
    }

    private fun isUuidShaped(s: String): Boolean =
        s.length == 36 && s[8] == '-' && s[13] == '-' && s[18] == '-' && s[23] == '-'

    // --- time conversion ---------------------------------------------------

    private fun computeTimes(
        dtstart: IcsProperty,
        dtend: IcsProperty?,
        duration: String?,
        allDay: Boolean,
    ): Triple<String, String, String?> {
        val tzId = dtstart.params["TZID"]
        if (allDay) {
            val s = LocalDate.parse(dtstart.value, BASIC_DATE)
            val e = when {
                dtend != null -> LocalDate.parse(dtend.value, BASIC_DATE)
                else -> s.plusDays(1)
            }
            return Triple(
                s.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                e.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                tzId,
            )
        }
        val s = parseDateTime(dtstart.value)
        val e = when {
            dtend != null -> parseDateTime(dtend.value)
            duration != null -> s.plus(parseIsoDuration(duration))
            else -> s.plus(Duration.ofHours(1))
        }
        return Triple(format(s), format(e), tzId)
    }

    private fun parseDateTime(v: String): OffsetDateTime {
        // BASIC: 20260512T143000Z | 20260512T143000
        return if (v.endsWith("Z")) {
            LocalDateTime.parse(v.dropLast(1), BASIC_DATE_TIME).atOffset(ZoneOffset.UTC)
        } else {
            LocalDateTime.parse(v, BASIC_DATE_TIME).atOffset(ZoneOffset.UTC)
        }
    }

    private fun format(odt: OffsetDateTime): String = odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun computeDurationIso(startIso: String, endIso: String, durationProp: String?): String {
        durationProp?.let { return it }
        val s = OffsetDateTime.parse(startIso)
        val e = OffsetDateTime.parse(endIso)
        return Duration.between(s, e).let { d ->
            // ISO-8601 PT<h>H<m>M
            val mins = d.toMinutes()
            if (mins % 60L == 0L) "PT${mins / 60}H" else "PT${mins}M"
        }
    }

    private fun parseIsoDuration(s: String): Duration = Duration.parse(s)

    private fun exdateToLocalDate(raw: String): String? {
        return when {
            raw.length >= 8 && raw[0].isDigit() -> {
                val datePart = raw.substringBefore('T').take(8)
                if (datePart.length == 8) "${datePart.substring(0, 4)}-${datePart.substring(4, 6)}-${datePart.substring(6, 8)}" else null
            }
            raw.length == 10 && raw[4] == '-' -> raw
            else -> null
        }
    }

    private fun normaliseExdate(raw: String): String? {
        val d = exdateToLocalDate(raw) ?: return null
        // RecurrenceRule.exdate is List<String> of LocalDateTime ISO format.
        return "${d}T00:00:00"
    }

    private fun stripZ(iso: String): String {
        // RecurrenceRule.dtstart is a LocalDateTime; drop the offset suffix.
        val odt = OffsetDateTime.parse(iso)
        return odt.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private val BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    // --- properties --------------------------------------------------------

    /** A single CONTENTLINE = name *(";" param) ":" value. */
    internal data class IcsProperty(val name: String, val params: Map<String, String>, val value: String)

    internal class IcsPropertyBag(val props: List<IcsProperty>) {
        fun first(name: String): IcsProperty? = props.firstOrNull { it.name.equals(name, ignoreCase = true) }
        fun firstValue(name: String): String? = first(name)?.value
        fun all(name: String): List<IcsProperty> = props.filter { it.name.equals(name, ignoreCase = true) }
    }

    internal fun parseProperties(lines: List<String>): IcsPropertyBag {
        val out = mutableListOf<IcsProperty>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("BEGIN:", ignoreCase = true) || line.startsWith("END:", ignoreCase = true)) continue
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val parts = head.split(';')
            val name = parts[0]
            val params = parts.drop(1).mapNotNull {
                val eq = it.indexOf('=')
                if (eq < 0) null else it.substring(0, eq).uppercase() to it.substring(eq + 1)
            }.toMap()
            out += IcsProperty(name, params, value)
        }
        return IcsPropertyBag(out)
    }

    /** RFC 5545 §3.3.11 — un-escape `\\`, `\,`, `\;`, `\n`, `\N`. */
    internal fun unescape(s: String): String {
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
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
