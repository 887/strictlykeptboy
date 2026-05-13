package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Phase FFF / EC-G.4 — overlap-detection unit tests.
 */
class OverlapDetectorTest {
    private fun t(s: String): OffsetDateTime = OffsetDateTime.parse(s)

    @Test fun no_overlap_when_disjoint() {
        val hit = OverlapDetector.firstOverlap(
            start = t("2026-05-13T10:00:00+02:00"),
            end = t("2026-05-13T11:00:00+02:00"),
            existing = listOf(Triple("Other", t("2026-05-13T12:00:00+02:00"), t("2026-05-13T13:00:00+02:00"))),
        )
        assertNull(hit)
    }

    @Test fun overlap_returned_when_touching_inside() {
        val hit = OverlapDetector.firstOverlap(
            start = t("2026-05-13T10:30:00+02:00"),
            end = t("2026-05-13T11:00:00+02:00"),
            existing = listOf(Triple("Lunch", t("2026-05-13T10:00:00+02:00"), t("2026-05-13T11:00:00+02:00"))),
        )
        assertEquals("Lunch", hit?.otherTitle)
    }

    @Test fun first_overlap_wins() {
        val hit = OverlapDetector.firstOverlap(
            start = t("2026-05-13T10:00:00+02:00"),
            end = t("2026-05-13T12:00:00+02:00"),
            existing = listOf(
                Triple("First", t("2026-05-13T10:30:00+02:00"), t("2026-05-13T11:00:00+02:00")),
                Triple("Second", t("2026-05-13T11:15:00+02:00"), t("2026-05-13T11:45:00+02:00")),
            ),
        )
        assertEquals("First", hit?.otherTitle)
    }

    @Test fun adjacent_intervals_do_not_overlap() {
        val hit = OverlapDetector.firstOverlap(
            start = t("2026-05-13T11:00:00+02:00"),
            end = t("2026-05-13T12:00:00+02:00"),
            existing = listOf(Triple("Prev", t("2026-05-13T10:00:00+02:00"), t("2026-05-13T11:00:00+02:00"))),
        )
        assertNull(hit)
    }
}
