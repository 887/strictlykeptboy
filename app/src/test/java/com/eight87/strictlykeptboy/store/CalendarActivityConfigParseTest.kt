package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Round 2.1.B schema-extension — round-trip + back-compat for
 * `color_seed`, `active_windows`, `active_hours` on `calendar.toml`.
 */
class CalendarActivityConfigParseTest {

    @Test fun empty_table_yields_defaults() {
        val cfg = CalendarActivityConfig.read(TomlReader.parse(""))
        assertNull(cfg.colorSeed)
        assertTrue(cfg.activeWindows.isEmpty())
        assertTrue(cfg.activeHours.isEmpty())
    }

    @Test fun back_compat_legacy_calendar_toml_ignores_new_keys() {
        val toml = """
            name = "Morning"
            priority = 500
            active_toggle = true
            tz_id = "Europe/Berlin"
        """.trimIndent()
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertNull(cfg.colorSeed)
        assertTrue(cfg.activeWindows.isEmpty())
        assertTrue(cfg.activeHours.isEmpty())
    }

    @Test fun reads_color_seed_scalar() {
        val toml = """color_seed = 13435347"""
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertEquals(13435347, cfg.colorSeed)
    }

    @Test fun reads_active_windows_aot() {
        val toml = """
            [[active_windows]]
            from = 2026-01-01
            to = 2026-06-30
            [[active_windows]]
            from = 2026-09-01
            to = 2026-12-31
        """.trimIndent()
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertEquals(2, cfg.activeWindows.size)
        assertEquals(LocalDate.of(2026, 1, 1), cfg.activeWindows[0].from)
        assertEquals(LocalDate.of(2026, 6, 30), cfg.activeWindows[0].to)
    }

    @Test fun negative_active_window_dropped() {
        val toml = """
            [[active_windows]]
            from = 2026-07-14
            to = 2026-07-01
        """.trimIndent()
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertTrue(cfg.activeWindows.isEmpty())
    }

    @Test fun reads_active_hours_aot() {
        val toml = """
            [[active_hours]]
            day = "MON"
            from = "09:00"
            to = "17:00"
            [[active_hours]]
            day = "FRI"
            from = "08:00"
            to = "13:00"
        """.trimIndent()
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertEquals(2, cfg.activeHours.size)
        assertEquals(DayOfWeek.MONDAY, cfg.activeHours[0].day)
        assertEquals(LocalTime.of(9, 0), cfg.activeHours[0].from)
        assertEquals(LocalTime.of(17, 0), cfg.activeHours[0].to)
        assertEquals(DayOfWeek.FRIDAY, cfg.activeHours[1].day)
    }

    @Test fun invalid_day_dropped() {
        val toml = """
            [[active_hours]]
            day = "BLURSDAY"
            from = "09:00"
            to = "17:00"
        """.trimIndent()
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertTrue(cfg.activeHours.isEmpty())
    }

    @Test fun round_trip_emits_and_reads_same_values() {
        val original = CalendarActivityConfig(
            colorSeed = 0x4FB7D3,
            activeWindows = listOf(
                CalendarActivityConfig.DateRange(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                ),
            ),
            activeHours = listOf(
                CalendarActivityConfig.HourRange(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
                CalendarActivityConfig.HourRange(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(13, 0)),
            ),
        )
        val table = TomlTable()
        original.writeInto(table)
        val text = TomlWriter.emit(table)
        val parsed = CalendarActivityConfig.read(TomlReader.parse(text))
        assertEquals(original.colorSeed, parsed.colorSeed)
        assertEquals(original.activeWindows.size, parsed.activeWindows.size)
        assertEquals(original.activeWindows[0].from, parsed.activeWindows[0].from)
        assertEquals(original.activeWindows[0].to, parsed.activeWindows[0].to)
        assertEquals(original.activeHours.size, parsed.activeHours.size)
        assertEquals(original.activeHours[0].day, parsed.activeHours[0].day)
        assertEquals(original.activeHours[0].from, parsed.activeHours[0].from)
    }

    @Test fun coexists_with_routine_and_supersedence() {
        val toml = """
            name = "Work"
            priority = 600
            active_toggle = true
            color_seed = 4900691
            routine = false
            supersedes = ["cal-routine"]

            [[active_windows]]
            from = 2026-01-01
            to = 2026-12-31

            [[active_hours]]
            day = "MON"
            from = "09:00"
            to = "17:00"
        """.trimIndent()
        val table = TomlReader.parse(toml)
        val activity = CalendarActivityConfig.read(table)
        val routine = RoutineCalendarConfig.read(table)
        val supersedence = SupersedenceConfig.read(table)
        assertEquals(4900691, activity.colorSeed)
        assertEquals(1, activity.activeWindows.size)
        assertEquals(1, activity.activeHours.size)
        assertEquals(false, routine.routine)
        assertEquals(listOf("cal-routine"), supersedence.supersedes)
    }

    // Round 2.21.A.2 — meta_group_field round-trip + back-compat.

    @Test fun meta_group_field_absent_yields_null() {
        val cfg = CalendarActivityConfig.read(TomlReader.parse(""))
        assertNull(cfg.metaGroupField)
    }

    @Test fun meta_group_field_read_round_trip() {
        val toml = """meta_group_field = "phase""""
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertEquals("phase", cfg.metaGroupField)
    }

    @Test fun meta_group_field_blank_treated_as_null() {
        val toml = """meta_group_field = """""
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertNull(cfg.metaGroupField)
    }

    @Test fun meta_group_field_writes_when_set() {
        val out = TomlTable()
        CalendarActivityConfig(metaGroupField = "category").writeInto(out)
        val s = TomlWriter.emit(out)
        assertTrue(s.contains("meta_group_field"))
        assertTrue(s.contains("category"))
    }

    @Test fun meta_group_field_omits_when_null() {
        val out = TomlTable()
        CalendarActivityConfig(metaGroupField = null).writeInto(out)
        val s = TomlWriter.emit(out)
        assertTrue(!s.contains("meta_group_field"))
    }

    // Round 2.25 follow-up — hex `color = "#RRGGBB"` is the CalDAV-canonical
    // form. Reader accepts it as a fallback when `color_seed = <int>` is
    // absent, so externally-authored TOML (and our shipped demo repo) gets
    // non-null colorSeed values without an extra migration step.

    @Test fun reads_color_hex_string_when_color_seed_absent() {
        val toml = """color = "#f4a261""""
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        // Opaque 0xFFF4A261 → signed Int.
        assertEquals(0xFFF4A261.toInt(), cfg.colorSeed)
    }

    @Test fun reads_color_hex_without_hash_prefix() {
        val toml = """color = "f4a261""""
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertEquals(0xFFF4A261.toInt(), cfg.colorSeed)
    }

    @Test fun color_seed_int_wins_when_both_present() {
        val toml = """
            color_seed = 12345
            color = "#ffffff"
        """.trimIndent()
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertEquals(12345, cfg.colorSeed)
    }

    @Test fun malformed_hex_color_yields_null() {
        val toml = """color = "not-a-color""""
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertNull(cfg.colorSeed)
    }

    @Test fun short_hex_color_yields_null() {
        // 3-digit form (`#abc`) is not supported by the parser; falls through
        // to null instead of throwing.
        val toml = """color = "#abc""""
        val cfg = CalendarActivityConfig.read(TomlReader.parse(toml))
        assertNull(cfg.colorSeed)
    }
}
