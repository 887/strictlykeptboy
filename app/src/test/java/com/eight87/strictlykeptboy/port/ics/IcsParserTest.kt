package com.eight87.strictlykeptboy.port.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {

    private val cal = "0190a0aa-1c1d-7000-8a0a-000000000001"
    private val author = "me@example.com"

    @Test fun parses_simple_vevent() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//test//EN
            BEGIN:VEVENT
            UID:simple-one@example.com
            DTSTAMP:20260512T120000Z
            DTSTART:20260512T140000Z
            DTEND:20260512T144500Z
            SUMMARY:Dentist
            LOCATION:Köhler\, Berlin
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val r = IcsParser.parse(ics, cal, author)
        assertEquals(1, r.events.size)
        assertEquals(0, r.rules.size)
        val e = r.events[0]
        assertEquals("Dentist", e.title)
        assertEquals("2026-05-12T14:00:00Z", e.start)
        assertEquals("2026-05-12T14:45:00Z", e.end)
        assertEquals("Köhler, Berlin", e.location)
        assertEquals("simple-one@example.com", e.externalUid)
    }

    @Test fun parses_weekly_rrule() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly-standup@example.com
            DTSTAMP:20260105T080000Z
            DTSTART:20260105T093000Z
            DTEND:20260105T094500Z
            SUMMARY:Standup
            RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val r = IcsParser.parse(ics, cal, author)
        assertEquals(0, r.events.size)
        assertEquals(1, r.rules.size)
        val rule = r.rules[0]
        assertEquals("Standup", rule.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", rule.rrule)
        assertEquals(cal, rule.calendarId)
        // Duration computed from DTSTART/DTEND.
        assertEquals("PT15M", rule.duration)
    }

    @Test fun exdate_emits_cancel_exception() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly@example.com
            DTSTAMP:20260105T080000Z
            DTSTART:20260105T093000Z
            DTEND:20260105T094500Z
            SUMMARY:Standup
            RRULE:FREQ=WEEKLY
            EXDATE:20260119T093000Z,20260202T093000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val r = IcsParser.parse(ics, cal, author)
        assertEquals(1, r.rules.size)
        assertEquals(2, r.exceptions.size)
        assertTrue(r.exceptions.all { it.mode == "cancel" })
        assertEquals(setOf("2026-01-19", "2026-02-02"), r.exceptions.map { it.instanceDate }.toSet())
        assertEquals(r.rules[0].id, r.exceptions[0].ruleId)
    }

    @Test fun line_unfolding_works() {
        val raw = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:f@x\r\nDTSTAMP:20260512T120000Z\r\nDTSTART:20260512T140000Z\r\nDTEND:20260512T150000Z\r\nSUMMARY:long title that\r\n  continues across folds\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val r = IcsParser.parse(raw, cal, author)
        assertEquals(1, r.events.size)
        assertEquals("long title that continues across folds", r.events[0].title)
    }

    @Test fun all_day_event() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:ad@x\r\nDTSTAMP:20260512T120000Z\r\nDTSTART;VALUE=DATE:20260512\r\nDTEND;VALUE=DATE:20260513\r\nSUMMARY:All-day\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val r = IcsParser.parse(ics, cal, author)
        assertEquals(1, r.events.size)
        assertTrue(r.events[0].allDay)
    }

    @Test fun malformed_input_tolerated() {
        val ics = "garbage garbage\r\nBEGIN:VEVENT\r\nno-properties\r\nEND:VEVENT\r\n"
        val r = IcsParser.parse(ics, cal, author)
        assertEquals(0, r.totalEntities)
        assertTrue(r.warnings.isNotEmpty())
    }
}
