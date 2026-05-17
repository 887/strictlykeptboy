package com.eight87.strictlykeptboy.resolver

import java.time.LocalDate

/**
 * Narrow accessor over [RenderedSchedule] used by leaf schedule views.
 *
 * Schedule view composables don't need the whole rendered schedule
 * (range / view-mode / source-digest / etc.) — they only need the
 * bands for the date(s) they paint. Passing the full god-handle to
 * every leaf is the canonical ISP violation called out in the SOLID
 * audit (`refactor-solid.md` §R.X.1 / 2026-05-17 audit item #10).
 *
 * Implementations return an empty list for dates outside the rendered
 * range; leaf views are free to call this for any date they iterate.
 */
fun interface DayBandSource {
    fun bandsFor(date: LocalDate): List<DayBand>

    companion object {
        /** Constant empty source — used when the host has no rendered schedule yet. */
        val Empty: DayBandSource = DayBandSource { _ -> emptyList() }
    }
}

/** Bridge: project a [RenderedSchedule] to the narrow [DayBandSource] surface. */
fun RenderedSchedule.asDayBandSource(): DayBandSource =
    DayBandSource { date -> days.firstOrNull { it.date == date }?.bands.orEmpty() }
