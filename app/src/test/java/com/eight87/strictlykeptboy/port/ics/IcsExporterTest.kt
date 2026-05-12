package com.eight87.strictlykeptboy.port.ics

import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception
import com.eight87.strictlykeptboy.store.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExporterTest {

    private val cal = "0190a0aa-1c1d-7000-8a0a-000000000001"
    private val nowIso = "2026-05-12T12:00:00Z"
    private val author = "me@example.com"

    private fun header(id: String) = EntityHeader(
        id = id, createdAt = nowIso, updatedAt = nowIso, author = author,
    )

    @Test fun emits_vcalendar_envelope() {
        val out = IcsExporter.export(emptyList(), emptyList(), emptyList())
        assertTrue(out.contains("BEGIN:VCALENDAR\r\n"))
        assertTrue(out.contains("VERSION:2.0\r\n"))
        assertTrue(out.contains("END:VCALENDAR\r\n"))
        assertTrue(out.contains("PRODID:"))
    }

    @Test fun round_trips_single_event() {
        val e = Event(
            header = header("0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"),
            title = "Dentist",
            start = "2026-05-12T14:00:00Z",
            end = "2026-05-12T14:45:00Z",
            calendarId = cal,
            location = "Berlin",
        )
        val ics = IcsExporter.export(listOf(e), emptyList(), emptyList())
        val parsed = IcsParser.parse(ics, cal, author)
        assertEquals(1, parsed.events.size)
        val back = parsed.events[0]
        assertEquals(e.title, back.title)
        assertEquals(e.start, back.start)
        assertEquals(e.end, back.end)
        assertEquals(e.location, back.location)
    }

    @Test fun round_trips_recurrence_with_cancel() {
        val r = RecurrenceRule(
            header = header("0190d4a0-7fab-7c50-9c1e-2b7a44f6f010"),
            title = "Standup",
            dtstart = "2026-01-05T09:30:00",
            duration = "PT15M",
            tzId = "UTC",
            rrule = "FREQ=WEEKLY;BYDAY=MO",
            calendarId = cal,
        )
        val ex = Exception(
            header = header("0190d4a0-7fab-7c50-9c1e-2b7a44f6f011"),
            ruleId = r.id,
            instanceDate = "2026-01-19",
            mode = "cancel",
            calendarId = cal,
        )
        val ics = IcsExporter.export(emptyList(), listOf(r), listOf(ex))
        val parsed = IcsParser.parse(ics, cal, author)
        assertEquals(1, parsed.rules.size)
        assertEquals(1, parsed.exceptions.size)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", parsed.rules[0].rrule)
        assertEquals("2026-01-19", parsed.exceptions[0].instanceDate)
    }

    @Test fun escapes_text_commas_and_semicolons() {
        val esc = IcsExporter.escape("Hello, world; line\nbreak")
        assertEquals("Hello\\, world\\; line\\nbreak", esc)
    }

    @Test fun emits_crlf_terminated_lines() {
        val out = IcsExporter.export(emptyList(), emptyList(), emptyList())
        assertTrue(out.endsWith("\r\n"))
        assertTrue(out.lines().none { it.endsWith("\r") && it.length > 75 })
    }
}
