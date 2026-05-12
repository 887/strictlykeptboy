package com.eight87.strictlykeptboy.port.ics

import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception
import com.eight87.strictlykeptboy.store.RecurrenceRule
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Phase P / NS-H — minimal RFC 5545 emitter.
 *
 * Emits a single VCALENDAR with one VEVENT per [Event] / [RecurrenceRule]
 * plus EXDATE lines for cancellation [Exception]s. VTIMEZONE blocks are
 * omitted for v1 — all DTSTART/DTEND values are emitted as UTC with the
 * `Z` suffix.
 *
 * Line-folding per §3.1: each output line wrapped at 75 octets with CRLF
 * followed by a single space.
 *
 * Escaping per §3.3.11: `\\`, `,`, `;`, and CR/LF in TEXT values.
 */
object IcsExporter {

    private const val PRODID = "-//eight87//strictlykeptboy//EN"
    private const val CRLF = "\r\n"

    fun export(
        events: List<Event>,
        rules: List<RecurrenceRule>,
        exceptions: List<Exception>,
    ): String {
        val out = StringBuilder()
        out.append("BEGIN:VCALENDAR").append(CRLF)
        out.append("VERSION:2.0").append(CRLF)
        out.append("PRODID:").append(PRODID).append(CRLF)

        for (e in events) emitEvent(out, e)
        // Group exceptions by ruleId for EXDATE collation.
        val exByRule = exceptions.filter { it.mode == "cancel" }.groupBy { it.ruleId }
        for (r in rules) emitRule(out, r, exByRule[r.id].orEmpty())

        out.append("END:VCALENDAR").append(CRLF)
        return out.toString()
    }

    private fun emitEvent(out: StringBuilder, e: Event) {
        out.appendFolded("BEGIN:VEVENT")
        out.appendFolded("UID:" + (e.externalUid ?: e.id))
        out.appendFolded("DTSTAMP:" + dtstamp(e.header.updatedAt))
        if (e.allDay) {
            out.appendFolded("DTSTART;VALUE=DATE:" + dateBasic(e.start))
            out.appendFolded("DTEND;VALUE=DATE:" + dateBasic(e.end))
        } else {
            out.appendFolded("DTSTART:" + dtBasic(e.start))
            out.appendFolded("DTEND:" + dtBasic(e.end))
        }
        out.appendFolded("SUMMARY:" + escape(e.title))
        e.location?.takeIf { it.isNotBlank() }?.let { out.appendFolded("LOCATION:" + escape(it)) }
        if (e.body.isNotBlank()) out.appendFolded("DESCRIPTION:" + escape(e.body.trimEnd()))
        out.appendFolded("END:VEVENT")
    }

    private fun emitRule(out: StringBuilder, r: RecurrenceRule, cancels: List<Exception>) {
        out.appendFolded("BEGIN:VEVENT")
        out.appendFolded("UID:" + r.id)
        out.appendFolded("DTSTAMP:" + dtstamp(r.header.updatedAt))
        // RecurrenceRule.dtstart is a naive LocalDateTime; emit with TZID parameter.
        val tz = r.tzId.ifBlank { "UTC" }
        out.appendFolded("DTSTART;TZID=$tz:" + localBasic(r.dtstart))
        out.appendFolded("DURATION:" + r.duration)
        out.appendFolded("SUMMARY:" + escape(r.title))
        r.location?.takeIf { it.isNotBlank() }?.let { out.appendFolded("LOCATION:" + escape(it)) }
        if (r.body.isNotBlank()) out.appendFolded("DESCRIPTION:" + escape(r.body.trimEnd()))
        out.appendFolded("RRULE:" + r.rrule)
        // EXDATEs: cancels supply dates; the rule's own exdate list is a parallel source.
        val exDates: List<String> = buildList {
            cancels.forEach { c -> add(c.instanceDate) }
            r.exdate.forEach { add(it.substringBefore('T')) }
        }.distinct()
        if (exDates.isNotEmpty()) {
            val joined = exDates.joinToString(",") { d -> dateBasic("${d}T00:00:00Z") }
            out.appendFolded("EXDATE;VALUE=DATE:$joined")
        }
        out.appendFolded("END:VEVENT")
    }

    // --- formatting --------------------------------------------------------

    private fun dtstamp(updatedAt: String): String {
        return try {
            OffsetDateTime.parse(updatedAt).withOffsetSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        } catch (_: Throwable) {
            OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        }
    }

    private fun dtBasic(iso: String): String {
        val odt = OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC)
        return odt.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    }

    private fun dateBasic(iso: String): String {
        // iso may be OffsetDateTime or "yyyy-MM-dd".
        val datePart = if (iso.length >= 10 && iso[4] == '-') iso.substring(0, 10) else iso
        return datePart.replace("-", "")
    }

    private fun localBasic(local: String): String {
        // LocalDateTime ISO: 2026-05-12T14:30:00 → 20260512T143000
        val cleaned = local.replace("-", "").replace(":", "")
        return cleaned.take(15) // yyyyMMddTHHmmss
    }

    internal fun escape(s: String): String {
        val sb = StringBuilder(s.length + 4)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                ',' -> sb.append("\\,")
                ';' -> sb.append("\\;")
                '\n' -> sb.append("\\n")
                '\r' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Append [line] with CRLF + RFC 5545 §3.1 line-folding at 75 octets. */
    private fun StringBuilder.appendFolded(line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) {
            append(line).append(CRLF)
            return
        }
        // Fold by octet count, decoding back to a string at the split boundary.
        var i = 0
        var first = true
        while (i < bytes.size) {
            val take = if (first) 75 else 74
            // Find a safe split that doesn't break a UTF-8 multibyte sequence.
            var end = (i + take).coerceAtMost(bytes.size)
            while (end < bytes.size && (bytes[end].toInt() and 0xC0) == 0x80) end--
            val chunk = String(bytes, i, end - i, Charsets.UTF_8)
            if (first) append(chunk) else append(' ').append(chunk)
            append(CRLF)
            i = end
            first = false
        }
    }
}
