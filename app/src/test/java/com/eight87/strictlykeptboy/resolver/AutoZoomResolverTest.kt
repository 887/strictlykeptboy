package com.eight87.strictlykeptboy.resolver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.25.x (D.127) — density-driven Auto zoom.
 *
 * Pure unit test — no Android dependencies. Covers the threshold
 * walk-up across the 4 zoom levels for 5-min / 15-min / 1h / mixed /
 * empty inputs.
 */
class AutoZoomResolverTest {
    private val cal = CalendarRef("cal-x")
    private val repo = RepoRef("repo-x")
    private val tz = ZoneId.of("UTC")
    private val base = ZonedDateTime.of(2026, 5, 17, 0, 0, 0, 0, tz)

    private fun ev(startMin: Int, durationMin: Int, id: String = "ev-$startMin"): MaterializedInstance =
        MaterializedInstance(
            source = InstanceSource.OneOff(EventRef(id)),
            calendar = cal,
            repo = repo,
            originalStart = base.plusMinutes(startMin.toLong()),
            originalEnd = base.plusMinutes((startMin + durationMin).toLong()),
            effectiveStart = base.plusMinutes(startMin.toLong()),
            effectiveEnd = base.plusMinutes((startMin + durationMin).toLong()),
            title = id,
            body = "",
        )

    @Test fun empty_returns_default_zoom() {
        assertEquals(AutoZoomResolver.DEFAULT_ZOOM, AutoZoomResolver.derive(emptyList()))
    }

    @Test fun five_minute_events_pick_level_4() {
        // 5 * {40,80,160,320} / 60 = 3.3, 6.7, 13.3, 26.7 — only 4 clears 14.
        val instances = (0..5).map { ev(it * 10, 5, "e$it") }
        assertEquals(4, AutoZoomResolver.derive(instances))
    }

    @Test fun fifteen_minute_events_pick_level_2() {
        // 15 * {40,80,...} / 60 = 10, 20 — level 2 first clears 14.
        val instances = listOf(ev(0, 15), ev(30, 15), ev(60, 15))
        assertEquals(2, AutoZoomResolver.derive(instances))
    }

    @Test fun one_hour_events_pick_level_1() {
        // 60 * 40 / 60 = 40 — level 1 already clears 14.
        val instances = listOf(ev(0, 60), ev(120, 60), ev(240, 60))
        assertEquals(1, AutoZoomResolver.derive(instances))
    }

    @Test fun thirty_minute_events_pick_level_1() {
        // 30 * 40 / 60 = 20 — level 1 clears 14.
        val instances = listOf(ev(0, 30), ev(60, 30))
        assertEquals(1, AutoZoomResolver.derive(instances))
    }

    @Test fun mixed_is_driven_by_shortest_event() {
        // Mix 1h + 5min: shortest is 5 → level 4.
        val instances = listOf(ev(0, 60), ev(120, 5), ev(180, 30))
        assertEquals(4, AutoZoomResolver.derive(instances))
    }

    @Test fun derive_from_bands_matches_derive_from_instances() {
        val instances = listOf(ev(0, 15), ev(60, 15))
        val bands = instances.map { DayBand(it, priority = 500, laneIndex = 0, totalLanes = 1) }
        assertEquals(
            AutoZoomResolver.derive(instances),
            AutoZoomResolver.deriveFromBands(bands),
        )
    }
}
