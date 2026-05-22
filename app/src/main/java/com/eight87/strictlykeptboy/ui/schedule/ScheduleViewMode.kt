package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import com.eight87.strictlykeptboy.resolver.InstanceSource

/**
 * Schedule layout mode — toggled via a shutterboy-style floating
 * button on Day / 3-day / Week tabs. `Grid` is the multi-column
 * date-vs-time view each tab originally shipped; `Stacked` renders
 * the same date range as a single long agenda column (each day
 * following the next), which is the default per user direction.
 */
enum class ScheduleLayoutMode { Stacked, Grid }

/**
 * Three-way band category. Each [DayBand] falls into exactly one
 * bucket; the FAB filter menu (shutterboy-style multi-toggle) lets
 * the user combine which categories are visible.
 *
 * - **Important** — one-off events from non-base calendars (doctor
 *   visits, "Brighton weekend", convention attendance, etc.). The
 *   "stuff that breaks routine" bucket.
 * - **Active** — habits that the user has to *act on* (active habits
 *   per Round 2.X — the ⚡ glyph): rule instances that aren't passive,
 *   or any band carrying `requiresResponse` / `promptKind`. Excludes
 *   base-layer scaffolding.
 * - **Routine** — everything else: base layers, passive habits,
 *   timeboxes, regular recurring events. The "scaffolding" bucket.
 *
 * Per user direction these flags are *not* bundled — Important and
 * Active are independent and routing belongs to neither.
 */
enum class BandCategory { Important, Active, Routine }

fun DayBand.category(): BandCategory {
    val inst = instance
    val isOneOff = inst.source is InstanceSource.OneOff
    val isBase = kind == CalendarKind.Base
    val needsResponse = inst.requiresResponse || inst.promptKind != null
    return when {
        isBase -> BandCategory.Routine
        needsResponse -> BandCategory.Active
        isOneOff -> BandCategory.Important
        !inst.passive -> BandCategory.Active
        else -> BandCategory.Routine
    }
}

/**
 * Multi-toggle filter state. At least one flag should be on at any
 * given time; when the user un-toggles the last one the picker keeps
 * the most recently toggled-off flag back on as a courtesy (handled
 * in the FAB UI, not here).
 */
data class ScheduleFilterFlags(
    val important: Boolean,
    val active: Boolean,
    val routine: Boolean,
) {
    fun accepts(band: DayBand): Boolean = when (band.category()) {
        BandCategory.Important -> important
        BandCategory.Active -> active
        BandCategory.Routine -> routine
    }

    fun anyOn(): Boolean = important || active || routine

    companion object {
        val All = ScheduleFilterFlags(important = true, active = true, routine = true)
        val ImportantAndActive =
            ScheduleFilterFlags(important = true, active = true, routine = false)

        /**
         * Default per (tab × layout). Per user direction:
         *  - multi-column Grid Week → Important + Active (routine off)
         *  - any Stacked layout → All (single long column = everything)
         *  - Grid Day / Grid 3-day → All (Grid only filters down on Week)
         */
        fun defaultFor(
            tab: com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab,
            layout: ScheduleLayoutMode,
        ): ScheduleFilterFlags = when {
            layout == ScheduleLayoutMode.Stacked -> All
            tab == com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab.Week ->
                ImportantAndActive
            else -> All
        }
    }
}

/**
 * Per-tab default layout. Week + 3-day default to Stacked (single
 * long column with each day stacked vertically — the "agenda for
 * this range" rendering). Day defaults to Grid (time-positioned
 * bands on a single day column — the canonical Day view).
 */
fun defaultLayoutFor(
    tab: com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab,
): ScheduleLayoutMode = when (tab) {
    com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab.Week,
    com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab.ThreeDay ->
        ScheduleLayoutMode.Stacked
    else -> ScheduleLayoutMode.Grid
}

/** Wrap a [DayBandSource] with a [ScheduleFilterFlags] predicate. */
fun DayBandSource.filteredBy(flags: ScheduleFilterFlags): DayBandSource =
    DayBandSource { date -> bandsFor(date).filter { flags.accepts(it) } }
