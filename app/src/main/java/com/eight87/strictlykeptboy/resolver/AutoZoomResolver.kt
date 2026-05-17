package com.eight87.strictlykeptboy.resolver

import java.time.Duration

/**
 * Round 2.25.x — density-driven "Auto" zoom for the Day / 3-day / Week
 * grids (D.127).
 *
 * Replaces the legacy fallback (`max(zoomOf visible overlays) ?: 2`)
 * with a content-aware picker: choose the smallest zoom level where
 * the shortest visible event clears a readability threshold of
 * [READABLE_BAND_DP] tall. Demo schedules with atomic 5-minute events
 * land on zoom 4 (320 dp/h ⇒ ~27 dp band); 15-minute calendars land
 * on zoom 2; sparse hourly schedules stay on zoom 1.
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
     * Pick the smallest zoom level ∈ {1..4} where the shortest event in
     * [instances] renders at least [READABLE_BAND_DP] tall. Returns
     * [DEFAULT_ZOOM] when [instances] is empty; clamps to 4 when even
     * the densest level can't satisfy the threshold (5-min events at
     * level 4 yield ~26.7 dp, "readable enough").
     */
    fun derive(instances: List<MaterializedInstance>): Int {
        if (instances.isEmpty()) return DEFAULT_ZOOM
        val shortestMinutes = instances.minOf {
            Duration.between(it.effectiveStart, it.effectiveEnd).toMinutes()
        }.coerceAtLeast(1L)
        for (level in 1..4) {
            val dpPerHour = LEVEL_DP_PER_HOUR.getValue(level)
            val bandDp = shortestMinutes * dpPerHour / 60.0
            if (bandDp >= READABLE_BAND_DP) return level
        }
        return 4
    }

    /** Convenience: derive from already-flattened [DayBand]s. */
    fun deriveFromBands(bands: List<DayBand>): Int =
        derive(bands.map { it.instance })
}
