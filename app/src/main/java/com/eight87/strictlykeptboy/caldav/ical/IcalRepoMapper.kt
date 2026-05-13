package com.eight87.strictlykeptboy.caldav.ical

import com.eight87.strictlykeptboy.git.Uuid7

/**
 * Phase Y.4 / Y.5 — map between an [IcalEvent] and the on-disk
 * Markdown+TOML-frontmatter representation under
 * `calendars/<targetCalendarId>/events/<yyyy>/<mm>/<event-id>.md`.
 *
 * The repo file is intentionally a *minimal projection* of the iCal
 * source — only the fields CalDAV defines round-trip; routine-template
 * fields (`materialized_from`, `subbeats`, etc. in `store/Entities.kt`)
 * are not produced by this mapper. The `mirror.source = "caldav"`
 * frontmatter key marks the file as bridge-owned so the app rejects
 * local edits in PULL_ONLY mode (Y.4).
 *
 * `imported_uid` is the join key: same value across all syncs of the
 * same VEVENT, surviving re-pulls and re-bucketings.
 */
object IcalRepoMapper {

    data class RepoFile(
        val relativePath: String,
        val content: String,
        val eventId: String,
    )

    fun toRepoFile(
        calendarId: String,
        event: IcalEvent,
        existingEventId: String? = null,
        sourceServerHost: String,
    ): RepoFile {
        val eventId = existingEventId ?: Uuid7.generate().toString()
        val (yyyy, mm) = bucket(event.dtStart)
        val rel = "calendars/$calendarId/events/$yyyy/$mm/$eventId.md"
        val content = buildString {
            append("+++\n")
            append("schema_version = 1\n")
            append("kind = \"event\"\n")
            append("id = \"").append(eventId).append("\"\n")
            append("calendar_id = \"").append(escape(calendarId)).append("\"\n")
            append("title = \"").append(escape(event.summary)).append("\"\n")
            append("start = \"").append(toIso(event.dtStart, event.allDay)).append("\"\n")
            append("end = \"").append(toIso(event.dtEnd, event.allDay)).append("\"\n")
            if (event.allDay) append("all_day = true\n")
            event.location?.let { append("location = \"").append(escape(it)).append("\"\n") }
            append("external_uid = \"").append(escape(event.uid)).append("\"\n")
            append("\n[mirror]\n")
            append("source = \"caldav\"\n")
            append("server_host = \"").append(escape(sourceServerHost)).append("\"\n")
            append("sequence = ").append(event.sequence).append("\n")
            event.lastModified?.let { append("last_modified = \"").append(it).append("\"\n") }
            append("+++\n")
            event.description?.let { append(it) }
        }
        return RepoFile(relativePath = rel, content = content, eventId = eventId)
    }

    /**
     * Parse a repo file back into an [IcalEvent] for push (Y.5). Reads
     * only the fields this mapper writes — does not try to handle the
     * full app-side Event schema. Returns null if the file is missing
     * mandatory iCal fields.
     */
    fun fromRepoFile(content: String): IcalEvent? {
        val fm = extractFrontmatter(content) ?: return null
        val uid = fm["external_uid"] ?: return null
        val title = fm["title"].orEmpty()
        val start = fm["start"] ?: return null
        val end = fm["end"] ?: start
        val allDay = fm["all_day"] == "true"
        val seq = fm["sequence"]?.toIntOrNull() ?: 0
        return IcalEvent(
            uid = uid,
            summary = title,
            dtStart = fromIso(start, allDay),
            dtEnd = fromIso(end, allDay),
            allDay = allDay,
            description = bodyOf(content).takeIf { it.isNotBlank() },
            location = fm["location"],
            sequence = seq,
            lastModified = fm["last_modified"],
        )
    }

    private fun bucket(dt: String): Pair<String, String> {
        // Accept both "20260413T120000Z" and "2026-04-13T12:00:00Z".
        val compact = dt.filter { it.isDigit() || it == 'T' || it == 'Z' }
        val yyyy = compact.take(4).ifBlank { "0000" }
        val mm = compact.drop(4).take(2).ifBlank { "00" }
        return yyyy to mm
    }

    private fun toIso(rfc5545: String, allDay: Boolean): String {
        // VEVENT DTSTART canonical forms:
        //   YYYYMMDDTHHMMSSZ   → 2026-04-13T12:00:00Z
        //   YYYYMMDDTHHMMSS    → 2026-04-13T12:00:00
        //   YYYYMMDD (all-day) → 2026-04-13
        val s = rfc5545
        if (allDay && s.length >= 8) return "${s.substring(0,4)}-${s.substring(4,6)}-${s.substring(6,8)}"
        if (s.length >= 15) {
            val date = "${s.substring(0,4)}-${s.substring(4,6)}-${s.substring(6,8)}"
            val time = "${s.substring(9,11)}:${s.substring(11,13)}:${s.substring(13,15)}"
            return date + "T" + time + (if (s.endsWith("Z")) "Z" else "")
        }
        return s
    }

    private fun fromIso(iso: String, allDay: Boolean): String {
        val cleaned = iso.replace("-", "").replace(":", "")
        if (allDay) return cleaned.take(8)
        return cleaned
    }

    private fun extractFrontmatter(content: String): Map<String, String>? {
        val start = content.indexOf("+++")
        if (start < 0) return null
        val rest = content.substring(start + 3)
        val end = rest.indexOf("+++")
        if (end < 0) return null
        val fm = rest.substring(0, end)
        val out = mutableMapOf<String, String>()
        for (line in fm.lineSequence()) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("[")) continue
            val eq = l.indexOf('=')
            if (eq < 0) continue
            val key = l.substring(0, eq).trim()
            var v = l.substring(eq + 1).trim()
            if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) v = unescape(v.substring(1, v.length - 1))
            out[key] = v
        }
        return out
    }

    private fun bodyOf(content: String): String {
        val first = content.indexOf("+++"); if (first < 0) return content
        val second = content.indexOf("+++", first + 3); if (second < 0) return ""
        return content.substring(second + 3).trimStart('\n')
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun unescape(s: String): String = s
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")
}
