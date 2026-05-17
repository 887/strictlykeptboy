package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.25.z (D.129) — sub-readable cue trigger.
 *
 * `isSubReadableBand(duration, zoom)` returns true when a band of the
 * given duration would render below the AutoZoomResolver readability
 * threshold (14 dp) at the given zoom level. The UI uses this to
 * decide whether to overlay the tap-to-expand glyph; the existing
 * Surface.onClick already routes to the full-screen detail view.
 */
class SubReadableBandTest {
    @Test fun five_min_at_level_2_is_sub_readable() {
        // 5 min * 80 dp/h / 60 = 6.67 dp < 14 → sub-readable.
        assertTrue(isSubReadableBand(durationMinutes = 5L, zoom = 2))
    }

    @Test fun thirty_min_at_level_2_is_readable() {
        // 30 * 80 / 60 = 40 dp ≥ 14.
        assertFalse(isSubReadableBand(durationMinutes = 30L, zoom = 2))
    }

    @Test fun five_min_at_level_4_is_still_readable_enough() {
        // 5 * 320 / 60 = 26.67 dp ≥ 14 — level 4 promises readability.
        assertFalse(isSubReadableBand(durationMinutes = 5L, zoom = 4))
    }

    @Test fun fifteen_min_at_level_1_is_sub_readable() {
        // 15 * 40 / 60 = 10 dp < 14.
        assertTrue(isSubReadableBand(durationMinutes = 15L, zoom = 1))
    }

    @Test fun fifteen_min_at_level_2_is_readable() {
        // 15 * 80 / 60 = 20 dp ≥ 14.
        assertFalse(isSubReadableBand(durationMinutes = 15L, zoom = 2))
    }
}
