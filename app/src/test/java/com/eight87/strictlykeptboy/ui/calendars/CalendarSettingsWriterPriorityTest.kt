package com.eight87.strictlykeptboy.ui.calendars

import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlWriter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.22 / Fix 3 — TOML-level round-trip for the priority-only
 * write path. The full [CalendarSettingsWriter.writePriority] is wired
 * to AppGraph + a real GitRepoRegistry entry, which is expensive to
 * stand up under JVM-only tests. This test exercises the codec path
 * the writer's read+merge+write loop relies on so a future regression
 * in TOML scalar handling shows up loud.
 */
class CalendarSettingsWriterPriorityTest {

    @Test fun priority_scalar_round_trips_via_toml_codec() {
        // Simulate an existing calendar.toml with priority=100 + unrelated
        // keys (name + supersedes) that must survive the priority-only
        // rewrite.
        val initial = """
            id = "cal-routines"
            name = "Routines"
            priority = 100
            supersedes = ["cal-other"]

        """.trimIndent()
        val table: TomlTable = TomlReader.parse(initial)
        assertEquals(100, table.getInt("priority"))

        // Rewrite priority via the same TomlTable mutator the writer uses.
        table.putInt("priority", 250)
        val emitted = TomlWriter.emit(table)
        val reparsed = TomlReader.parse(emitted)

        assertEquals(250, reparsed.getInt("priority"))
        // Unrelated keys must survive.
        assertEquals("Routines", reparsed.getString("name"))
        assertEquals("cal-routines", reparsed.getString("id"))
        assertEquals(listOf("cal-other"), reparsed.getStringArray("supersedes"))
    }
}
