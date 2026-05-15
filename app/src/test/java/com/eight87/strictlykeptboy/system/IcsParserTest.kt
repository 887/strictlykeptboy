package com.eight87.strictlykeptboy.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.18.E.7 — VEVENT parse coverage for the system-intent
 * import path: one timed VEVENT, one recurring VEVENT (RRULE
 * FREQ=WEEKLY), and one VEVENT carrying VALARM + ATTENDEE.
 */
class IcsParserTest {

    @Test fun parsesTimedVevent() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:simple-1
            DTSTART:20260601T120000Z
            DTEND:20260601T130000Z
            SUMMARY:Lunch
            LOCATION:Cafe
            DESCRIPTION:Bring sketchbook
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val out = IcsParser.parse(ics)
        assertEquals(1, out.events.size)
        val ev = out.events[0]
        assertEquals("simple-1", ev.uid)
        assertEquals("Lunch", ev.summary)
        assertEquals("Cafe", ev.location)
        assertEquals("Bring sketchbook", ev.description)
        assertEquals(false, ev.allDay)
        assertNull(ev.rrule)
    }

    @Test fun parsesRecurringVevent() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:repeat-1
            DTSTART:20260601T140000Z
            DTEND:20260601T150000Z
            SUMMARY:Weekly standup
            RRULE:FREQ=WEEKLY;BYDAY=MO
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val out = IcsParser.parse(ics)
        assertEquals(1, out.events.size)
        val ev = out.events[0]
        assertEquals("FREQ=WEEKLY;BYDAY=MO", ev.rrule)
    }

    @Test fun parsesValarmAndAttendees() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:alarm-1
            DTSTART:20260601T160000Z
            DTEND:20260601T170000Z
            SUMMARY:Doctor
            ATTENDEE;CN=Doctor Bee:mailto:bee@example.com
            ATTENDEE:mailto:me@example.com
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val out = IcsParser.parse(ics)
        val ev = out.events.single()
        assertEquals(2, ev.attendees.size)
        assertEquals("Doctor Bee", ev.attendees[0].commonName)
        assertEquals("bee@example.com", ev.attendees[0].email)
        assertEquals(null, ev.attendees[1].commonName)
        assertEquals("me@example.com", ev.attendees[1].email)
        assertEquals(1, ev.reminders.size)
        assertEquals(15, ev.reminders[0].minutesBeforeStart)
        assertTrue(ev.reminders[0].relatedToStart)
    }

    @Test fun toleratesUnterminatedVeventWithWarning() {
        val ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:dangling\n"
        val out = IcsParser.parse(ics)
        assertTrue(out.events.isEmpty())
        assertTrue(out.warnings.any { it.contains("unterminated VEVENT") })
    }
}
