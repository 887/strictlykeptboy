package com.eight87.strictlykeptboy.port.ics

import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception
import com.eight87.strictlykeptboy.store.RecurrenceRule

/**
 * Phase P / NS-H + NS-I — parse + export results.
 *
 * Hand-rolled minimal RFC 5545 (VEVENT subset). Sufficient for V1
 * round-trip with `Thunderbird` / Google Calendar / Apple `.ics`
 * exports. ical4j (~700KB) deferred until profiling shows pain.
 *
 * VTIMEZONE blocks are dropped for v1 — every DTSTART/DTEND is
 * treated as UTC if it carries a `Z` suffix, naive local time
 * otherwise (mapped into RecurrenceRule.tzId = "UTC" fallback).
 */
data class IcsParseReport(
    val events: List<Event>,
    val rules: List<RecurrenceRule>,
    val exceptions: List<Exception>,
    /** Lines that couldn't be parsed; surfaced for the UI preview but never abort. */
    val warnings: List<String> = emptyList(),
) {
    val totalEntities: Int get() = events.size + rules.size + exceptions.size
}
