package com.eight87.strictlykeptboy.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ----------------------------------------------------------------
    // Round 2.18.J.5 — real-world vendor fixtures
    //
    // Three synthetic-but-shape-accurate VCALENDAR payloads modeled on
    // the canonical Google / Outlook (Exchange) / Apple Calendar
    // emitters. These exercise the parser against the kinds of quirks
    // each vendor adds that hand-rolled minimal fixtures don't catch:
    //
    //  - Google: 75-col line folding, multi-line ATTENDEE, VTIMEZONE
    //    with daylight + standard sub-components, UTF-8 chars in CN,
    //    escaped commas in LOCATION, METHOD:REQUEST.
    //  - Outlook: `W. Europe Standard Time` TZID (NOT an IANA zone),
    //    deeply-folded DESCRIPTION across many lines with embedded
    //    underscores, X-MICROSOFT-* extension props, RRULE WKST.
    //  - Apple: `X-WR-ALARMUID` on VALARM, GMT+N TZ names, fixture
    //    ordering DTEND-before-DTSTART.
    //
    // Cannot vendor the bitfireAT/ical4android fixtures — that
    // project is GPLv3, incompatible with skb's Apache-2.0 release.
    // These files are original synthetic samples in this repo.
    // ----------------------------------------------------------------

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("ics-fixtures/$name")) {
            "fixture not on test classpath: $name"
        }.bufferedReader().use { it.readText() }

    @Test fun parsesGoogleEmittedInvite() {
        val ics = loadFixture("google.ics")
        val out = IcsParser.parse(ics)
        assertEquals(emptyList<String>(), out.warnings)
        val ev = out.events.single()
        assertEquals("abcd1234-google@google.com", ev.uid)
        assertEquals("Doctor visit", ev.summary)
        // Comma-escape inside LOCATION survives the unescape pass.
        assertEquals("Praxis Dr. Bee, Berlin", ev.location)
        // Multi-line attendee with X-NUM-GUESTS param survives line unfolding.
        assertEquals(2, ev.attendees.size)
        assertEquals("Bee Doctor", ev.attendees[0].commonName)
        assertEquals("bee@example.com", ev.attendees[0].email)
        // UTF-8 in attendee CN survives.
        assertEquals("Alex 887", ev.attendees[1].commonName)
        assertEquals(1, ev.reminders.size)
        assertEquals(30, ev.reminders[0].minutesBeforeStart)
        // Berlin TZ resolves through ZoneId.of.
        assertEquals("Europe/Berlin", ev.start.zone.id)
        assertEquals(10, ev.start.hour)
        assertEquals(false, ev.allDay)
        assertNull(ev.rrule)
    }

    @Test fun parsesOutlookExchangeRecurringSync() {
        val ics = loadFixture("outlook.ics")
        val out = IcsParser.parse(ics)
        val ev = out.events.single()
        assertEquals("Weekly project sync", ev.summary)
        // RRULE retained verbatim for lib-recur to expand later.
        assertEquals("FREQ=WEEKLY;BYDAY=TU;WKST=MO", ev.rrule)
        // RELATED=START on TRIGGER param survives.
        assertEquals(1, ev.reminders.size)
        assertEquals(15, ev.reminders[0].minutesBeforeStart)
        assertTrue(ev.reminders[0].relatedToStart)
        // Heavily-folded DESCRIPTION reassembles without leaking the
        // CRLF-SP fold markers back into the text body.
        assertFalse(
            "unfolded description must not contain the fold sentinel",
            ev.description.contains("\n ") || ev.description.contains("\n\t"),
        )
        // Microsoft `W. Europe Standard Time` is not an IANA zone —
        // the parser falls back to UTC rather than throwing.
        assertNotNull(ev.start)
        // UID with the 040000008200E0... Exchange globalObjectId
        // round-trips intact.
        assertTrue(ev.uid!!.startsWith("040000008200E000"))
    }

    @Test fun parsesAppleCalendarPublish() {
        val ics = loadFixture("apple.ics")
        val out = IcsParser.parse(ics)
        val ev = out.events.single()
        assertEquals("Coffee with Sam", ev.summary)
        assertEquals("E9C5B7A0-AAAA-4BBB-CCCC-DDEEFF001122", ev.uid)
        // Apple emits LOCATION with two escaped commas.
        assertEquals("Café Einstein, Unter den Linden 42, Berlin", ev.location)
        assertEquals(1, ev.reminders.size)
        assertEquals(10, ev.reminders[0].minutesBeforeStart)
        // DTEND appearing BEFORE DTSTART in source order still resolves.
        assertEquals(17, ev.start.hour)
        assertEquals(18, ev.end.hour)
        assertEquals("Europe/Berlin", ev.start.zone.id)
    }

    @Test fun allThreeVendorFixturesRoundTripWithoutWarnings() {
        // Parameterized smoke: every vendor fixture should parse
        // cleanly (no malformed-VEVENT entries surface as warnings).
        for (name in listOf("google.ics", "outlook.ics", "apple.ics")) {
            val out = IcsParser.parse(loadFixture(name))
            assertEquals(
                "$name should parse with no warnings",
                emptyList<String>(),
                out.warnings,
            )
            assertEquals("$name should yield exactly one VEVENT", 1, out.events.size)
        }
    }
}
