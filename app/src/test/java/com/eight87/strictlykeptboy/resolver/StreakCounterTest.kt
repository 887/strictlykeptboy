package com.eight87.strictlykeptboy.resolver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Phase XX.11 / AT-K.7 — count-only streak.
 *
 * Scenario: daily recurring event scheduled D-7..today (8 days). A
 * `skipped` deviation on D-3 breaks the streak. Walking back from
 * today (D-0) we count D-0, D-1, D-2 → 3.
 */
class StreakCounterTest {

    @Test fun streakBrokenBySkippedDeviation() {
        val today = LocalDate.parse("2026-05-13")
        val scheduled = (0..7).map { today.minusDays(it.toLong()) }.toSet()
        val skipped = setOf(today.minusDays(3))
        val streak = StreakCounter.compute(today, scheduled, skipped)
        assertEquals(3, streak)
    }

    @Test fun partialDoesNotBreakStreak() {
        // `partial` deviations are NOT in `skipped` per AT-J.1 LOCKED.
        // The streak walks past them like any non-skip day.
        val today = LocalDate.parse("2026-05-13")
        val scheduled = (0..6).map { today.minusDays(it.toLong()) }.toSet()
        val skipped = emptySet<LocalDate>()
        assertEquals(7, StreakCounter.compute(today, scheduled, skipped))
    }

    @Test fun zeroWhenTodayIsSkipped() {
        val today = LocalDate.parse("2026-05-13")
        val scheduled = setOf(today)
        val skipped = setOf(today)
        assertEquals(0, StreakCounter.compute(today, scheduled, skipped))
    }

    @Test fun statusOnHelperClassifiesThreeWay() {
        val today = LocalDate.parse("2026-05-13")
        val scheduled = setOf(today)
        val skipped = setOf<LocalDate>()
        assertEquals(StreakCounter.DayStatus.ScheduledNoSkip, StreakCounter.statusOn(today, scheduled, skipped))
        assertEquals(StreakCounter.DayStatus.NotScheduled, StreakCounter.statusOn(today.minusDays(99), scheduled, skipped))
        assertEquals(StreakCounter.DayStatus.Skipped, StreakCounter.statusOn(today, scheduled, setOf(today)))
    }
}
