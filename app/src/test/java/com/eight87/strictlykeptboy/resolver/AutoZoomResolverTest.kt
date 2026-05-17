package com.eight87.strictlykeptboy.resolver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.25.x (D.127) — density-driven Auto zoom.
 * Round 2.25.y (D.128) — grouped-aware effective-band variant.
 * Round 2.25.z (D.129) — driver is 25th-percentile shortest, not min,
 * so isolated short outliers no longer drag Auto to Spacious.
 *
 * Pure unit test — no Android dependencies.
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

    @Test fun single_5min_band_is_its_own_p25_picks_level_4() {
        // n=1 → p25 index = ceil(0.25)-1 = 0 → the single 5-min band drives.
        assertEquals(4, AutoZoomResolver.deriveFromEffectiveBandMinutes(listOf(5L)))
    }

    @Test fun fifteen_minute_events_pick_level_2() {
        // 15 * {40,80,...} / 60 = 10, 20 — level 2 first clears 14.
        val instances = listOf(ev(0, 15), ev(30, 15), ev(60, 15))
        assertEquals(2, AutoZoomResolver.derive(instances))
    }

    @Test fun one_hour_events_pick_level_1() {
        val instances = listOf(ev(0, 60), ev(120, 60), ev(240, 60))
        assertEquals(1, AutoZoomResolver.derive(instances))
    }

    @Test fun thirty_minute_events_pick_level_1() {
        val instances = listOf(ev(0, 30), ev(60, 30))
        assertEquals(1, AutoZoomResolver.derive(instances))
    }

    // ── D.129 (p25) behaviour ────────────────────────────────────────

    @Test fun four_bands_one_outlier_p25_still_picks_the_outlier() {
        // n=4 → p25 idx = ceil(1)-1 = 0 → 25% IS the outlier, it wins.
        // sorted [5,60,60,60] → idx 0 = 5 → level 4.
        assertEquals(4, AutoZoomResolver.deriveFromEffectiveBandMinutes(listOf(5L, 60L, 60L, 60L)))
    }

    @Test fun eight_bands_two_short_p25_picks_the_5min() {
        // n=8 → p25 idx = ceil(2)-1 = 1. sorted [5,5,30,30,60,60,60,60]
        // → idx 1 = 5 → level 4.
        assertEquals(
            4,
            AutoZoomResolver.deriveFromEffectiveBandMinutes(
                listOf(5L, 5L, 30L, 30L, 60L, 60L, 60L, 60L),
            ),
        )
    }

    @Test fun twelve_bands_three_short_p25_still_picks_5min() {
        // n=12 → p25 idx = ceil(3)-1 = 2. sorted [5,5,5,30,30,60,...]
        // → idx 2 = 5 → level 4.
        assertEquals(
            4,
            AutoZoomResolver.deriveFromEffectiveBandMinutes(
                listOf(5L, 5L, 5L, 30L, 30L, 60L, 60L, 60L, 60L, 60L, 60L, 60L),
            ),
        )
    }

    @Test fun twelve_bands_two_short_p25_skips_outliers_picks_30min() {
        // n=12 → p25 idx = 2. sorted [5,5,30,30,30,60,...]
        // → idx 2 = 30 → level 1 (30 * 40 / 60 = 20 dp ≥ 14).
        assertEquals(
            1,
            AutoZoomResolver.deriveFromEffectiveBandMinutes(
                listOf(5L, 5L, 30L, 30L, 30L, 60L, 60L, 60L, 60L, 60L, 60L, 60L),
            ),
        )
    }

    @Test fun rich_demo_shape_isolated_outliers_do_not_dominate() {
        // ~12 visible effective bands typical of the rich demo:
        // 1 morning group (~25 min), 1 evening group (~30 min),
        // 4 work blocks (60-120 min), a couple medium chores (15-30 min),
        // and 2 isolated 5-min text pings.
        // sorted: [5,5,15,25,25,30,30,30,60,60,90,120] → n=12, idx=2 = 15 → level 2.
        assertEquals(
            2,
            AutoZoomResolver.deriveFromEffectiveBandMinutes(
                listOf(5L, 5L, 15L, 25L, 25L, 30L, 30L, 30L, 60L, 60L, 90L, 120L),
            ),
        )
    }

    // ── grouped-adapter passthrough (D.128) ──────────────────────────

    @Test fun grouped_single_25min_band_picks_low_zoom() {
        // Single 25-min effective band, n=1, p25 idx 0 → 25 → level 1.
        assertEquals(1, AutoZoomResolver.deriveFromEffectiveBandMinutes(listOf(25L)))
    }

    @Test fun empty_effective_bands_returns_default() {
        assertEquals(AutoZoomResolver.DEFAULT_ZOOM, AutoZoomResolver.deriveFromEffectiveBandMinutes(emptyList()))
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
