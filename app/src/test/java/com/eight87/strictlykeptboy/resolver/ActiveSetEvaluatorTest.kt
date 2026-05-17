package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.TZ_BERLIN
import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.snapshot
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ActiveSetEvaluatorTest {

    private val eval = ActiveSetEvaluator()

    @Test fun alwaysOn_emptyWindowsAlwaysActive() {
        val c = cal("c1")
        assertTrue(eval.isActive(c, zdt("2026-07-15T12:00:00")))
    }

    @Test fun scopedDateRange_excludesOutsideWindow() {
        val c = cal(
            "vacation",
            windows = listOf(DateRange(LocalDate.parse("2026-05-10"), LocalDate.parse("2026-05-24"))),
        )
        assertTrue(eval.isActive(c, zdt("2026-05-15T10:00:00")))
        assertFalse(eval.isActive(c, zdt("2026-06-01T10:00:00")))
    }

    @Test fun workHours_inclusiveFromExclusiveTo() {
        val c = cal("work", hours = listOf(HourRange(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))))
        // 2026-05-11 is Mon
        assertTrue(eval.isActive(c, zdt("2026-05-11T09:00:00")))
        assertTrue(eval.isActive(c, zdt("2026-05-11T16:59:00")))
        assertFalse(eval.isActive(c, zdt("2026-05-11T17:00:00")))
        assertFalse(eval.isActive(c, zdt("2026-05-12T12:00:00")))
    }

    @Test fun midnightRollover_nightShift() {
        val c = cal(
            "nightshift",
            hours = listOf(HourRange(DayOfWeek.FRIDAY, LocalTime.of(22, 0), LocalTime.of(6, 0))),
        )
        assertTrue(eval.isActive(c, zdt("2026-05-15T23:30:00"))) // Fri night
        assertTrue(eval.isActive(c, zdt("2026-05-16T02:00:00"))) // Sat early am
        assertFalse(eval.isActive(c, zdt("2026-05-16T06:01:00")))
    }

    @Test fun zeroLengthHourRange_neverActive() {
        val c = cal(
            "broken",
            hours = listOf(HourRange(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 0))),
        )
        assertFalse(eval.isActive(c, zdt("2026-05-11T09:00:00")))
    }

    @Test fun multiRepoAggregation_returnsUnionOfActiveCalendars() {
        val snap = snapshot(
            cal("c1", repo = "r1"),
            cal("c2", repo = "r2", active = false),
            cal("c3", repo = "r3"),
            repos = listOf(
                RepoSnapshot.RepoEntry(RepoRef("r1"), "sha1"),
                RepoSnapshot.RepoEntry(RepoRef("r2"), "sha2"),
                RepoSnapshot.RepoEntry(RepoRef("r3"), "sha3"),
            ),
        )
        val active = eval.activeCalendarsAt(zdt("2026-05-11T12:00:00"), snap)
        assertEquals(setOf(CalendarRef("c1"), CalendarRef("c3")), active)
    }

    @Test fun supersedence_excludesSuppressedCalendar() {
        val snap = snapshot(
            cal("work"),
            cal("vacation", priority = 999, supersedes = listOf("work")),
        )
        val active = eval.activeCalendarsAt(zdt("2026-05-11T12:00:00"), snap)
        assertEquals(setOf(CalendarRef("vacation")), active)
    }

    @Test fun supersedence_overrideForcesReinclude() {
        val snap = snapshot(
            cal("work"),
            cal("vacation", priority = 999, supersedes = listOf("work")),
        )
        val override = OverrideInput(
            supersededCalendar = CalendarRef("work"),
            eventId = "evt-x",
            instanceDate = LocalDate.parse("2026-05-11"),
            kind = OverrideKind.ForceShow,
        )
        val active = eval.activeCalendarsAt(
            zdt("2026-05-11T12:00:00", TZ_BERLIN),
            snap,
            overrides = listOf(override),
        )
        assertTrue(CalendarRef("work") in active)
        assertTrue(CalendarRef("vacation") in active)
    }
}
