package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Phase BBB.7 / DM-X — Reminder round-trip + default cadences + HV-N.7
 * 60-second collision-window collapsing.
 */
class ReminderParseTest {

    @Test fun pre_event_round_trip() {
        val r = Reminder(offset = "-PT30M", kind = ReminderKind.PreEvent)
        val parsed = Reminder.fromTable(r.toTable())
        assertEquals(r, parsed)
        assertEquals(Duration.ofMinutes(-30), parsed!!.offsetDuration)
    }

    @Test fun zero_offset_is_at_start() {
        val r = Reminder(offset = "0", kind = ReminderKind.AtStart)
        assertEquals(Duration.ZERO, r.offsetDuration)
        val parsed = Reminder.fromTable(r.toTable())
        assertNotNull(parsed)
    }

    @Test fun unknown_kind_rejected() {
        val t = TomlTable().apply {
            putString("offset", "PT5M")
            putString("kind", "lol_random")
        }
        assertNull(Reminder.fromTable(t))
    }

    @Test fun malformed_offset_rejected() {
        val t = TomlTable().apply {
            putString("offset", "not-a-duration")
            putString("kind", "pre_event")
        }
        assertNull(Reminder.fromTable(t))
    }

    @Test fun lockscreen_visibility_round_trips() {
        val r = Reminder(
            offset = "0",
            kind = ReminderKind.AtStart,
            lockscreenVisibility = LockscreenVisibility.Private,
        )
        val parsed = Reminder.fromTable(r.toTable())
        assertEquals(LockscreenVisibility.Private, parsed!!.lockscreenVisibility)
    }

    @Test fun array_round_trips_in_order() {
        val list = listOf(
            Reminder("-PT1H", ReminderKind.PreEvent),
            Reminder("0", ReminderKind.AtStart),
            Reminder("PT30M", ReminderKind.PostEventCheckin),
        )
        val fm = TomlTable()
        Reminder.writeArray(fm, list)
        val parsed = Reminder.readArray(fm)
        assertEquals(list, parsed)
    }

    @Test fun normaliseForAllDay_drops_at_start_and_adds_banner() {
        val input = listOf(
            Reminder("0", ReminderKind.AtStart),
            Reminder("-P1D", ReminderKind.TomorrowBriefing),
        )
        val out = Reminder.normaliseForAllDay(input, allDay = true)
        assertTrue(out.none { it.kind == ReminderKind.AtStart })
        assertTrue(out.any { it.kind == ReminderKind.AllDayBanner })
        assertTrue(out.any { it.kind == ReminderKind.TomorrowBriefing })
    }

    @Test fun normaliseForAllDay_passthrough_for_non_all_day() {
        val input = listOf(Reminder("0", ReminderKind.AtStart))
        assertEquals(input, Reminder.normaliseForAllDay(input, allDay = false))
    }

    @Test fun defaultCadences_medical_has_three_reminders() {
        val list = DefaultCadences.forCategory("medical")
        assertEquals(3, list.size)
        assertTrue(list.any { it.kind == ReminderKind.AtStart })
    }

    @Test fun defaultCadences_unknown_category_empty() {
        assertTrue(DefaultCadences.forCategory("does-not-exist").isEmpty())
        assertTrue(DefaultCadences.forCategory(null).isEmpty())
    }

    @Test fun collapsing_within_60s_window_HV_N_7() {
        val t0 = Instant.parse("2026-05-12T10:00:00Z")
        val fires = listOf(
            t0,
            t0.plusSeconds(30),
            t0.plusSeconds(59),
            t0.plusSeconds(120), // outside window
        )
        val buckets = ReminderCollapsing.collapse(fires)
        assertEquals(2, buckets.size)
        assertEquals(3, buckets[0].members.size)
        assertEquals(1, buckets[1].members.size)
    }

    @Test fun collapsing_empty_input() {
        assertTrue(ReminderCollapsing.collapse(emptyList()).isEmpty())
    }
}
