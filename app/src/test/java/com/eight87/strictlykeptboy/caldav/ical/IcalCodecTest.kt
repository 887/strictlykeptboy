package com.eight87.strictlykeptboy.caldav.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase Y.7 — VEVENT round-trip + repo-file mapping tests. */
class IcalCodecTest {

    private val sampleVcal = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//acme//caldav//EN
BEGIN:VEVENT
UID:uid-aaa@server
SUMMARY:Dentist
DTSTART:20260413T120000Z
DTEND:20260413T130000Z
DESCRIPTION:Cleaning + checkup
LOCATION:Main St
SEQUENCE:2
END:VEVENT
END:VCALENDAR"""

    @Test fun decodes_basic_vevent() {
        val events = IcalCodec.decode(sampleVcal)
        assertEquals(1, events.size)
        val ev = events[0]
        assertEquals("uid-aaa@server", ev.uid)
        assertEquals("Dentist", ev.summary)
        assertEquals("20260413T120000Z", ev.dtStart)
        assertEquals("20260413T130000Z", ev.dtEnd)
        assertEquals(2, ev.sequence)
        assertEquals("Cleaning + checkup", ev.description)
    }

    @Test fun handles_all_day_via_value_date() {
        val v = """BEGIN:VCALENDAR
BEGIN:VEVENT
UID:allday-1
SUMMARY:Holiday
DTSTART;VALUE=DATE:20260101
DTEND;VALUE=DATE:20260102
END:VEVENT
END:VCALENDAR"""
        val ev = IcalCodec.decode(v).single()
        assertTrue(ev.allDay)
        assertEquals("20260101", ev.dtStart)
    }

    @Test fun encode_then_decode_preserves_fields() {
        val original = IcalCodec.decode(sampleVcal).single()
        val encoded = IcalCodec.encode(original)
        val redecoded = IcalCodec.decode(encoded).single()
        assertEquals(original, redecoded)
    }

    @Test fun text_escapes_round_trip() {
        val original = IcalEvent(
            uid = "u",
            summary = "a, b; c",
            dtStart = "20260101T000000Z",
            dtEnd = "20260101T010000Z",
            allDay = false,
            description = "line1\nline2",
        )
        val redecoded = IcalCodec.decode(IcalCodec.encode(original)).single()
        assertEquals("a, b; c", redecoded.summary)
        assertEquals("line1\nline2", redecoded.description)
    }

    @Test fun line_folding_is_unfolded() {
        val folded = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:u\r\nSUMMARY:Hello\r\n World\r\nDTSTART:20260101T000000Z\r\nDTEND:20260101T010000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val ev = IcalCodec.decode(folded).single()
        assertEquals("HelloWorld", ev.summary)
    }
}
