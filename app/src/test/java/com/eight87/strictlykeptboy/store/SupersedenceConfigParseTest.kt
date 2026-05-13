package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase BBB.1 — SupersedenceConfig round-trip + invariants S3 / DM-AA.5
 * + RV-Q isWithinBaseline.
 */
class SupersedenceConfigParseTest {

    @Test fun reads_supersedes_and_nonSuperseable() {
        val toml = """
            supersedes = ["cal-morning", "cal-workout"]
            nonSuperseable = true
        """.trimIndent()
        val cfg = SupersedenceConfig.read(TomlReader.parse(toml))
        assertEquals(listOf("cal-morning", "cal-workout"), cfg.supersedes)
        assertTrue(cfg.nonSuperseable)
        assertTrue(cfg.supersededDuring.isEmpty())
        assertNull(cfg.baselineCadence)
    }

    @Test fun self_supersede_rejected_S3() {
        val toml = """supersedes = ["cal-me", "cal-other"]"""
        val cfg = SupersedenceConfig.read(TomlReader.parse(toml), hostCalendarId = "cal-me")
        assertEquals(listOf("cal-other"), cfg.supersedes)
    }

    @Test fun superseded_during_array_of_tables() {
        val toml = """
            [[superseded_during]]
            from = 2026-07-01
            to = 2026-07-14
            [[superseded_during]]
            from = 2026-12-20
            to = 2027-01-02
        """.trimIndent()
        val cfg = SupersedenceConfig.read(TomlReader.parse(toml))
        assertEquals(2, cfg.supersededDuring.size)
        assertTrue(cfg.supersededDuring[0].contains(LocalDate.of(2026, 7, 7)))
        assertFalse(cfg.supersededDuring[0].contains(LocalDate.of(2026, 6, 30)))
    }

    @Test fun negative_range_dropped_DM_AA_5() {
        val toml = """
            [[superseded_during]]
            from = 2026-07-14
            to = 2026-07-01
        """.trimIndent()
        val cfg = SupersedenceConfig.read(TomlReader.parse(toml))
        assertTrue(cfg.supersededDuring.isEmpty())
    }

    @Test fun baseline_cadence_parsed() {
        val toml = """
            [baseline_cadence]
            weekdays = ["MON", "TUE", "WED", "THU", "FRI"]
            window = ["09:00", "17:00"]
            timezone = "Europe/Berlin"
        """.trimIndent()
        val cfg = SupersedenceConfig.read(TomlReader.parse(toml))
        val bc = cfg.baselineCadence
        assertNotNull(bc)
        bc!!
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), bc.weekdays)
        assertEquals(LocalTime.of(9, 0), bc.windowStart)
        assertEquals(LocalTime.of(17, 0), bc.windowEndExclusive)
        assertEquals(ZoneId.of("Europe/Berlin"), bc.timezone)
    }

    @Test fun isWithinBaseline_RV_Q_2() {
        val cfg = SupersedenceConfig(
            baselineCadence = SupersedenceConfig.BaselineCadence(
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                windowStart = LocalTime.of(9, 0),
                windowEndExclusive = LocalTime.of(17, 0),
                timezone = ZoneId.of("UTC"),
            ),
        )
        val saturday14h = ZonedDateTime.of(2026, 5, 16, 14, 0, 0, 0, ZoneId.of("UTC")) // Sat
        assertFalse(cfg.isWithinBaseline(saturday14h))

        val tuesday10h = ZonedDateTime.of(2026, 5, 12, 10, 0, 0, 0, ZoneId.of("UTC"))
        assertTrue(cfg.isWithinBaseline(tuesday10h))

        val tuesday17h = ZonedDateTime.of(2026, 5, 12, 17, 0, 0, 0, ZoneId.of("UTC"))
        assertFalse(cfg.isWithinBaseline(tuesday17h)) // end-exclusive
    }

    @Test fun isWithinBaseline_returns_true_when_no_cadence() {
        val cfg = SupersedenceConfig()
        val t = ZonedDateTime.of(2026, 5, 16, 14, 0, 0, 0, ZoneId.of("UTC"))
        assertTrue(cfg.isWithinBaseline(t)) // missing block ⇒ no flagging (RV-Q.1)
    }

    @Test fun round_trip_through_writeInto() {
        val cfg = SupersedenceConfig(
            supersedes = listOf("a", "b"),
            supersededDuring = listOf(
                SupersedenceConfig.DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14)),
            ),
            nonSuperseable = true,
            baselineCadence = SupersedenceConfig.BaselineCadence(
                weekdays = setOf(DayOfWeek.MONDAY),
                windowStart = LocalTime.of(8, 0),
                windowEndExclusive = LocalTime.of(12, 0),
                timezone = ZoneId.of("UTC"),
            ),
        )
        val t = TomlTable()
        cfg.writeInto(t)
        val parsed = SupersedenceConfig.read(t)
        assertEquals(cfg.supersedes, parsed.supersedes)
        assertEquals(1, parsed.supersededDuring.size)
        assertEquals(cfg.supersededDuring[0].from, parsed.supersededDuring[0].from)
        assertEquals(cfg.supersededDuring[0].to, parsed.supersededDuring[0].to)
        assertTrue(parsed.nonSuperseable)
        assertEquals(cfg.baselineCadence!!.weekdays, parsed.baselineCadence!!.weekdays)
    }
}
