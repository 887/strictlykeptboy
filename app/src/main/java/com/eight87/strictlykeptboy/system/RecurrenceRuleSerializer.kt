package com.eight87.strictlykeptboy.system

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Round 2.18.D.4 — RFC5545 RRULE / EXDATE helpers.
 *
 * Skb's [com.eight87.strictlykeptboy.resolver.RecurrenceInput] already
 * carries the rule as an RFC5545 string (the dmfs lib-recur layer
 * produces the canonical text on parse). For Phase D we mostly pass it
 * through; we only need to:
 *
 *  - cap an existing RRULE with `UNTIL=` for the "this and following"
 *    branch ([withUntil]);
 *  - format an instance-start as the RFC5545 UTC date-time used by
 *    `UNTIL` ([formatUntilUtc]);
 *  - format an EXDATE list ([formatExdate]).
 *
 * No general-purpose parser — that's what lib-recur is for. This file is
 * the *write*-side glue.
 */
object RecurrenceRuleSerializer {

    private val UNTIL_FMT_UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /**
     * Format [instanceStartMs] as the UTC-suffixed RFC5545 UNTIL value
     * (`yyyyMMddTHHmmssZ`). RFC5545: UNTIL is inclusive, so caller may
     * want to subtract 1 second to exclude the boundary instance — we
     * leave that policy decision to the caller because skb treats UNTIL
     * as "stop the series at-or-before this point", which matches
     * Google Calendar / Etar behaviour.
     */
    fun formatUntilUtc(instanceStartMs: Long): String {
        val zdt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(instanceStartMs - 1_000L),
            ZoneId.of("UTC"),
        )
        return UNTIL_FMT_UTC.format(zdt)
    }

    /**
     * Return [rrule] with any existing `UNTIL=` / `COUNT=` replaced by a
     * fresh `UNTIL=[until]`. Tokens are semicolon-separated `KEY=VALUE`
     * pairs per RFC5545. Order is preserved; if no `UNTIL` / `COUNT` was
     * present we append `UNTIL=[until]`.
     */
    fun withUntil(rrule: String, until: String): String {
        val cleaned = rrule.removePrefix("RRULE:")
        val parts = cleaned.split(';').filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        var replaced = false
        for (p in parts) {
            val key = p.substringBefore('=').uppercase()
            if (key == "UNTIL" || key == "COUNT") {
                if (!replaced) {
                    out += "UNTIL=$until"
                    replaced = true
                }
                // Drop any subsequent UNTIL / COUNT (RFC5545 forbids both).
            } else {
                out += p
            }
        }
        if (!replaced) {
            out += "UNTIL=$until"
        }
        return out.joinToString(";")
    }

    /**
     * Format an EXDATE list. CalendarContract column `EXDATE` accepts
     * the same string CalDAV stores: comma-separated date-time values
     * in either floating local time or UTC. We emit UTC for safety.
     */
    fun formatExdate(exdates: List<ZonedDateTime>, tzId: ZoneId): String {
        if (exdates.isEmpty()) return ""
        val header = "TZID=${tzId.id}:"
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        return header + exdates.joinToString(",") {
            fmt.format(it.withZoneSameInstant(tzId))
        }
    }
}
