package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TomlRoundTripTest {

    @Test fun scalarsRoundTrip() {
        val src = """
            id = "01900000-0000-7000-8000-aaaaaaaaaaaa"
            count = 42
            ratio = 0.5
            done = true
            day = 2026-05-12
            ts = 2026-05-12T14:00:00+02:00
        """.trimIndent()
        val t = TomlReader.parse(src)
        assertEquals("01900000-0000-7000-8000-aaaaaaaaaaaa", t.getString("id"))
        assertEquals(42, t.getInt("count"))
        assertEquals(true, t.getBool("done"))
        assertEquals("2026-05-12", t.getLocalDate("day"))
        assertEquals("2026-05-12T14:00:00+02:00", t.getOffsetDateTime("ts"))

        val emitted = TomlWriter.emit(t)
        val reparsed = TomlReader.parse(emitted)
        assertEquals(t.scalars["id"], reparsed.scalars["id"])
        assertEquals(t.scalars["count"], reparsed.scalars["count"])
        assertEquals(t.scalars["done"], reparsed.scalars["done"])
        assertEquals(t.scalars["day"], reparsed.scalars["day"])
        assertEquals(t.scalars["ts"], reparsed.scalars["ts"])
    }

    @Test fun arraysAndInlineTables() {
        val src = """
            tags = ["a", "b", "c"]
            geo = { lat = 48.0, lon = 9.0 }
        """.trimIndent()
        val t = TomlReader.parse(src)
        assertEquals(listOf("a", "b", "c"), t.getStringArray("tags"))
        val geo = t.scalars["geo"] as TomlValue.InlineTable
        assertEquals(2, geo.entries.size)
        val emitted = TomlWriter.emit(t)
        val reparsed = TomlReader.parse(emitted)
        assertEquals(t.getStringArray("tags"), reparsed.getStringArray("tags"))
    }

    @Test fun sectionsAndArrayOfTables() {
        val src = """
            title = "Foo"

            [[active_hours]]
            day = "mon"
            from = "09:00"

            [[active_hours]]
            day = "tue"
            from = "10:00"
        """.trimIndent()
        val t = TomlReader.parse(src)
        val rows = t.aotables["active_hours"]!!
        assertEquals(2, rows.size)
        assertEquals("mon", rows[0].getString("day"))
        assertEquals("tue", rows[1].getString("day"))

        val emitted = TomlWriter.emit(t)
        val reparsed = TomlReader.parse(emitted)
        assertEquals(2, reparsed.aotables["active_hours"]!!.size)
        assertEquals("tue", reparsed.aotables["active_hours"]!![1].getString("day"))
    }

    @Test fun stringEscapes() {
        val src = """
            note = "line1\nline2\ttabbed\\backslash\"quote"
        """.trimIndent()
        val t = TomlReader.parse(src)
        val note = t.getString("note")
        assertTrue(note!!.contains("\n"))
        assertTrue(note.contains("\t"))
        assertTrue(note.contains("\\"))
        assertTrue(note.contains("\""))
        val emitted = TomlWriter.emit(t)
        val reparsed = TomlReader.parse(emitted)
        assertEquals(note, reparsed.getString("note"))
    }
}
