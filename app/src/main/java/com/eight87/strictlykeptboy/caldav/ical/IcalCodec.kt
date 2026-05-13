package com.eight87.strictlykeptboy.caldav.ical

/**
 * Phase Y — minimal RFC5545 codec sufficient for VEVENT round-trip
 * through the CalDAV bridge. Handles:
 *  - line unfolding (RFC5545 §3.1 — continuation lines start with
 *    whitespace).
 *  - property-name + parameter-list + value split.
 *  - `\n` / `\,` / `\;` / `\\` escapes in TEXT values.
 *  - DATE vs DATE-TIME detection from `VALUE=DATE` param or
 *    `len == 8` heuristic for DTSTART/DTEND.
 *
 * NOT a full ical4j replacement — VALARM / VJOURNAL / RECURRENCE-ID /
 * VTIMEZONE handling is deferred to SE-Q.6..SE-Q.9 (which will pull in
 * ical4j proper). The library deps are declared in Y.1's
 * `libs.versions.toml` for that future wiring.
 */
object IcalCodec {

    fun decode(text: String): List<IcalEvent> {
        val unfolded = unfold(text)
        val out = mutableListOf<IcalEvent>()
        var inEvent = false
        var props = mutableMapOf<String, String>()
        var params = mutableMapOf<String, Map<String, String>>()
        for (raw in unfolded.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inEvent = true; props = mutableMapOf(); params = mutableMapOf(); continue
            }
            if (line.equals("END:VEVENT", ignoreCase = true)) {
                if (inEvent) buildEvent(props, params)?.let(out::add)
                inEvent = false; continue
            }
            if (!inEvent) continue
            val (nameWithParams, value) = splitName(line) ?: continue
            val parts = nameWithParams.split(';')
            val name = parts[0].uppercase()
            val pmap = parts.drop(1).mapNotNull { p ->
                val eq = p.indexOf('='); if (eq < 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1)
            }.toMap()
            props[name] = value
            if (pmap.isNotEmpty()) params[name] = pmap
        }
        return out
    }

    fun encode(event: IcalEvent): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//strictlykeptboy//CalDAV bridge//EN\r\n")
        sb.append("BEGIN:VEVENT\r\n")
        sb.append("UID:").append(escape(event.uid)).append("\r\n")
        sb.append("SUMMARY:").append(escape(event.summary)).append("\r\n")
        val dateParam = if (event.allDay) ";VALUE=DATE" else ""
        sb.append("DTSTART$dateParam:").append(event.dtStart).append("\r\n")
        sb.append("DTEND$dateParam:").append(event.dtEnd).append("\r\n")
        event.description?.let { sb.append("DESCRIPTION:").append(escape(it)).append("\r\n") }
        event.location?.let { sb.append("LOCATION:").append(escape(it)).append("\r\n") }
        event.rrule?.let { sb.append("RRULE:").append(it).append("\r\n") }
        sb.append("SEQUENCE:").append(event.sequence).append("\r\n")
        event.lastModified?.let { sb.append("LAST-MODIFIED:").append(it).append("\r\n") }
        sb.append("END:VEVENT\r\n")
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun unfold(text: String): String {
        // RFC5545: a CRLF + (space|tab) is a continuation; merge into prior line.
        val sb = StringBuilder()
        val lines = text.replace("\r\n", "\n").split('\n')
        for (line in lines) {
            if (sb.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                sb.append(line.substring(1))
            } else {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line)
            }
        }
        return sb.toString()
    }

    private fun splitName(line: String): Pair<String, String>? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        return line.substring(0, colon) to unescape(line.substring(colon + 1))
    }

    private fun buildEvent(props: Map<String, String>, params: Map<String, Map<String, String>>): IcalEvent? {
        val uid = props["UID"] ?: return null
        val dtStart = props["DTSTART"] ?: return null
        val dtEnd = props["DTEND"] ?: dtStart
        val allDay = (params["DTSTART"]?.get("VALUE")?.equals("DATE", ignoreCase = true) == true)
            || (dtStart.length == 8 && !dtStart.contains('T'))
        return IcalEvent(
            uid = uid,
            summary = props["SUMMARY"].orEmpty(),
            dtStart = dtStart,
            dtEnd = dtEnd,
            allDay = allDay,
            description = props["DESCRIPTION"],
            location = props["LOCATION"],
            rrule = props["RRULE"],
            sequence = props["SEQUENCE"]?.toIntOrNull() ?: 0,
            lastModified = props["LAST-MODIFIED"],
        )
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

    private fun unescape(value: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    ',' -> sb.append(',')
                    ';' -> sb.append(';')
                    '\\' -> sb.append('\\')
                    else -> sb.append(value[i + 1])
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
