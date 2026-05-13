package com.eight87.strictlykeptboy.caldav.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase Y.7 — VEVENT ↔ repo file round-trip via the mapper. */
class IcalRepoMapperTest {

    @Test fun materializes_event_under_correct_bucket() {
        val ev = IcalEvent(
            uid = "uid-1@server",
            summary = "Dentist",
            dtStart = "20260413T120000Z",
            dtEnd = "20260413T130000Z",
            allDay = false,
            description = "Cleaning",
            location = "Main St",
            sequence = 1,
        )
        val file = IcalRepoMapper.toRepoFile(
            calendarId = "work",
            event = ev,
            sourceServerHost = "caldav.example.com",
        )
        assertTrue(file.relativePath.startsWith("calendars/work/events/2026/04/"))
        assertTrue(file.relativePath.endsWith(".md"))
        assertTrue(file.content.contains("title = \"Dentist\""))
        assertTrue(file.content.contains("external_uid = \"uid-1@server\""))
        assertTrue(file.content.contains("source = \"caldav\""))
    }

    @Test fun round_trips_through_repo_file() {
        val ev = IcalEvent(
            uid = "uid-rt",
            summary = "Standup",
            dtStart = "20260105T090000Z",
            dtEnd = "20260105T093000Z",
            allDay = false,
            location = "Zoom",
            sequence = 0,
        )
        val file = IcalRepoMapper.toRepoFile("team", ev, sourceServerHost = "host.example")
        val back = IcalRepoMapper.fromRepoFile(file.content)
        assertNotNull(back)
        assertEquals("uid-rt", back!!.uid)
        assertEquals("Standup", back.summary)
        assertEquals("20260105T090000Z", back.dtStart)
        assertEquals("Zoom", back.location)
    }

    @Test fun reuses_existing_event_id() {
        val ev = IcalEvent("u", "S", "20260105T090000Z", "20260105T093000Z", false)
        val a = IcalRepoMapper.toRepoFile("c", ev, existingEventId = "fixed-id-1", sourceServerHost = "x")
        assertEquals("calendars/c/events/2026/01/fixed-id-1.md", a.relativePath)
    }
}
