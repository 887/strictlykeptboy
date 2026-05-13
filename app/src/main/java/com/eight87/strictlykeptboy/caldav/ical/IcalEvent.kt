package com.eight87.strictlykeptboy.caldav.ical

/**
 * Phase Y — CalDAV event in our intermediate form. Independent of the
 * `store/Entities.kt::Event` type to keep the iCal codec narrowly
 * focused (SOLID.I). The mapper that translates this into a repo file
 * lives in [IcalRepoMapper].
 *
 * Times are stored as RFC5545 strings (`YYYYMMDDTHHMMSSZ` for UTC,
 * `YYYYMMDD` for all-day) to preserve fidelity through round-trips.
 * The resolver layer converts to/from `kotlinx.datetime` types when it
 * actually evaluates the calendar.
 */
data class IcalEvent(
    val uid: String,
    val summary: String,
    val dtStart: String,
    val dtEnd: String,
    val allDay: Boolean,
    val description: String? = null,
    val location: String? = null,
    val rrule: String? = null,
    val sequence: Int = 0,
    val lastModified: String? = null,
)
