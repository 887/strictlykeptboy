package com.eight87.strictlykeptboy.auto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.1.G.3 — off-schedule warning surfacing.
 *
 * Per D.80 / RV-Q the resolver tags an instance off-schedule when an
 * override / exception drifts it outside the rule's window. The Auto
 * row prefixes those rows with [AutoRowFormatter.OFF_SCHEDULE_PREFIX]
 * so a driver glancing at the row sees the deviation cue.
 */
class CarAppRuntimeOffScheduleTest {

    private val start = ZonedDateTime.of(2026, 5, 13, 16, 0, 0, 0, ZoneId.of("UTC"))

    @Test fun off_schedule_true_prefixes_row_with_warning() {
        val out = AutoRowFormatter.rowTitle(
            start = start,
            title = "Standup",
            identity = null,
            offSchedule = true,
        )
        assertTrue(out.startsWith(AutoRowFormatter.OFF_SCHEDULE_PREFIX))
        assertTrue("body must still include the time", out.contains("16:00"))
        assertTrue("body must still include the title", out.contains("Standup"))
    }

    @Test fun off_schedule_false_leaves_row_unprefixed() {
        val out = AutoRowFormatter.rowTitle(
            start = start,
            title = "Standup",
            identity = null,
            offSchedule = false,
        )
        assertFalse(out.startsWith(AutoRowFormatter.OFF_SCHEDULE_PREFIX))
    }
}
