package com.eight87.strictlykeptboy.resolver

import java.time.Duration

/**
 * Round 2.25.x — density-driven "Auto" zoom for the Day / 3-day / Week
 * grids (D.127, refined by D.128, refined by D.129).
 *
 * Replaces the legacy fallback (`max(zoomOf visible overlays) ?: 2`)
 * with a content-aware picker. As of D.129 (Round 2.25.z), the picker
 * uses the *25th-percentile shortest* effective band as the driver,
 * not the minimum: isolated 5-min outliers (dom-overlay text pings,
 * social check-ins) no longer drag Auto to Spacious. The trade-off is
 * explicit: up to 25% of bands may stay sub-readable at the chosen
 * zoom — the UI compensates by giving sub-readable bands a tap-to-
 * expand affordance that routes to the full-screen detail view.
 *
 * Pure — no Android dependencies. Callers pass the materialized
 * instances visible on the day (or across the visible range) and
 * the resolver picks the level deterministically.
 */
object AutoZoomResolver {
    /** Minimum band height (dp) considered "readable" for ≥1 line of label text. */
    const val READABLE_BAND_DP = 14

    /** Mirrors `hourHeightForZoom` in `ui/schedule/ScheduleDayView.kt`. */
    private val LEVEL_DP_PER_HOUR = mapOf(1 to 40, 2 to 80, 3 to 160, 4 to 320)

    /** Default when no instances are visible — preserves historic 80 dp/h. */
    const val DEFAULT_ZOOM = 2

    /**
     * Pick the smallest zoom level ∈ {1..4} where the *25th-percentile*
     * shortest event in [instances] renders at least [READABLE_BAND_DP]
     * tall. Returns [DEFAULT_ZOOM] when [instances] is empty; clamps to
     * 4 when even the densest level can't satisfy the threshold.
     */
    fun derive(instances: List<MaterializedInstance>): Int {
        if (instances.isEmpty()) return DEFAULT_ZOOM
        val minutes = instances.map {
            Duration.between(it.effectiveStart, it.effectiveEnd).toMinutes().coerceAtLeast(1L)
        }
        return zoomForShortestMinutes(p25Minutes(minutes))
    }

    /** Convenience: derive from already-flattened [DayBand]s. */
    fun deriveFromBands(bands: List<DayBand>): Int =
        derive(bands.map { it.instance })

    /**
     * Round 2.25.y — grouped-aware Auto zoom (D.128); Round 2.25.z
     * (D.129) replaces minimum-wins with p25-wins.
     *
     * Take the *effective* band durations (in minutes) the user will
     * actually see after group-collapse at zoom 2 — atoms in the same
     * group fold into a single "Morning routine · N atoms" band. Then
     * pick the smallest zoom level where the 25th-percentile shortest
     * *effective* band clears [READABLE_BAND_DP]. Isolated short
     * outliers (e.g. 5-min text pings) no longer dominate.
     */
    fun deriveFromEffectiveBandMinutes(effectiveBandMinutes: List<Long>): Int {
        if (effectiveBandMinutes.isEmpty()) return DEFAULT_ZOOM
        val clamped = effectiveBandMinutes.map { it.coerceAtLeast(1L) }
        return zoomForShortestMinutes(p25Minutes(clamped))
    }

    /**
     * 25th-percentile shortest minute value. Sort ascending; p25 index
     * = `ceil(0.25 * n) - 1`, clamped to `0..n-1`. n=1→0, n=4→0, n=8→1,
     * n=12→2. The result is the smallest value such that ≥25% of bands
     * are ≤ it; using it as the readability driver means the algorithm
     * tolerates up to ~25% sub-readable outliers.
     */
    private fun p25Minutes(minutes: List<Long>): Long {
        val sorted = minutes.sorted()
        val n = sorted.size
        val idx = (Math.ceil(0.25 * n).toInt() - 1).coerceIn(0, n - 1)
        return sorted[idx]
    }

    private fun zoomForShortestMinutes(shortestMinutes: Long): Int {
        for (level in 1..4) {
            val dpPerHour = LEVEL_DP_PER_HOUR.getValue(level)
            val bandDp = shortestMinutes * dpPerHour / 60.0
            if (bandDp >= READABLE_BAND_DP) return level
        }
        return 4
    }
}
