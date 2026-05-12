package com.eight87.strictlykeptboy.port.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalformedIcsTest {

    private val cal = "0190a0aa-1c1d-7000-8a0a-000000000001"
    private val author = "me@example.com"

    @Test fun empty_input_returns_empty_report() {
        val r = IcsParser.parse("", cal, author)
        assertEquals(0, r.totalEntities)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun unterminated_vevent_surfaces_warning() {
        val r = IcsParser.parse("BEGIN:VEVENT\r\nUID:x\r\nDTSTART:20260512T140000Z\r\n", cal, author)
        assertEquals(0, r.totalEntities)
        assertTrue(r.warnings.any { it.contains("unterminated", ignoreCase = true) })
    }

    @Test fun vevent_missing_dtstart_surfaces_warning() {
        val ics = "BEGIN:VEVENT\r\nUID:x\r\nSUMMARY:no-times\r\nEND:VEVENT\r\n"
        val r = IcsParser.parse(ics, cal, author)
        assertEquals(0, r.totalEntities)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun garbage_lines_skipped_silently() {
        val ics = """
            BEGIN:VCALENDAR
            !!! garbage line !!!
            xxxxxxxx
            BEGIN:VEVENT
            UID:keepme
            DTSTAMP:20260512T120000Z
            DTSTART:20260512T140000Z
            DTEND:20260512T150000Z
            SUMMARY:OK
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val r = IcsParser.parse(ics, cal, author)
        assertEquals(1, r.events.size)
    }
}
