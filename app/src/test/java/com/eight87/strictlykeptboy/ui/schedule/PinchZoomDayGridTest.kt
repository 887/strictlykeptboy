package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.21 Phase D.5 — pure tests for the two functions that back
 * the pinch-to-zoom gesture on the day grid: [snapZoomFromScale]
 * (step snapping from accumulated pinch scale) + [pickBandAtY]
 * (hit-test for the topmost overlay at the gesture-center Y).
 *
 * These functions are deterministic and don't need Robolectric; the
 * gesture *plumbing* is exercised by the live AVD, but the math
 * gates here keep the algorithm honest.
 */
class PinchZoomDayGridTest {

    private val date = LocalDate.of(2026, 5, 16)
    private val tz: ZoneId = ZoneId.of("UTC")

    private fun band(
        id: String,
        startHour: Int,
        endHour: Int,
        calendarId: String = "c1",
        repoId: String = "r1",
    ): DayBand {
        val start = ZonedDateTime.of(date.atTime(startHour, 0), tz)
        val end = ZonedDateTime.of(date.atTime(endHour, 0), tz)
        val inst = MaterializedInstance(
            source = InstanceSource.OneOff(EventRef(id)),
            calendar = CalendarRef(calendarId),
            repo = RepoRef(repoId),
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = id,
            body = "",
        )
        return DayBand(instance = inst, priority = 500, laneIndex = 0, totalLanes = 1)
    }

    // ---- snapZoomFromScale ---------------------------------------

    @Test fun pinch_out_past_threshold_bumps_one_step() {
        assertEquals(3, snapZoomFromScale(currentZoom = 2, scale = 1.5f))
        assertEquals(4, snapZoomFromScale(currentZoom = 3, scale = 1.6f))
    }

    @Test fun pinch_in_past_threshold_bumps_one_step_down() {
        assertEquals(1, snapZoomFromScale(currentZoom = 2, scale = 0.5f))
        assertEquals(2, snapZoomFromScale(currentZoom = 3, scale = 0.7f))
    }

    @Test fun jitter_below_threshold_keeps_zoom() {
        assertEquals(2, snapZoomFromScale(currentZoom = 2, scale = 1.0f))
        assertEquals(2, snapZoomFromScale(currentZoom = 2, scale = 1.2f))
        assertEquals(2, snapZoomFromScale(currentZoom = 2, scale = 0.9f))
    }

    @Test fun clamps_to_1_4_range() {
        assertEquals(4, snapZoomFromScale(currentZoom = 4, scale = 2.0f))
        assertEquals(1, snapZoomFromScale(currentZoom = 1, scale = 0.25f))
    }

    @Test fun out_of_range_current_zoom_is_clamped_first() {
        assertEquals(4, snapZoomFromScale(currentZoom = 99, scale = 1.5f))
        assertEquals(1, snapZoomFromScale(currentZoom = -5, scale = 0.5f))
    }

    // ---- pickBandAtY ---------------------------------------------

    @Test fun picks_band_at_y_inside_band_interval() {
        // 80dp/h ≈ 80px/h ≈ 1.33px/min — at density 1.0 the math is
        // straightforward: hourHeightPx = 80, band 09:00–10:00 spans
        // y ∈ [720, 800].
        val bands = listOf(band("morning", 9, 10), band("midday", 13, 14))
        val picked = pickBandAtY(bands = bands, centerYPx = 760f, hourHeightPx = 80f)
        assertNotNull(picked)
        assertEquals("morning", picked!!.instance.title)
    }

    @Test fun returns_null_when_no_band_contains_y() {
        val bands = listOf(band("morning", 9, 10))
        val picked = pickBandAtY(bands = bands, centerYPx = 100f, hourHeightPx = 80f)
        assertNull(picked)
    }

    @Test fun picks_first_matching_band_when_overlapping() {
        // Two bands sharing the same vertical interval — pickBandAtY
        // returns the first match, which mirrors how `bands` is
        // emitted by the resolver (collision-band ordered).
        val a = band("a", 10, 12, calendarId = "ca")
        val b = band("b", 10, 12, calendarId = "cb")
        val picked = pickBandAtY(bands = listOf(a, b), centerYPx = 880f, hourHeightPx = 80f)
        assertSame(a, picked)
    }

    @Test fun rejects_non_positive_hour_height_px() {
        val bands = listOf(band("morning", 9, 10))
        assertNull(pickBandAtY(bands = bands, centerYPx = 760f, hourHeightPx = 0f))
        assertNull(pickBandAtY(bands = bands, centerYPx = 760f, hourHeightPx = -1f))
    }
}
