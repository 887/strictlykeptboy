package com.eight87.strictlykeptboy.ui.scaffold

/**
 * Per-pane Schedule view-mode. Backing enum for the left-rail entries
 * the [SkbAppShell] renders while [TopDestination.Schedule] is active.
 *
 * Phase U.4 / F11 note: [label] is **wire-format** — used by
 * [com.eight87.strictlykeptboy.ui.schedule.ScheduleViewModePrefs] as the
 * persisted SharedPreferences key form (alongside `name`) and as a
 * stable toString fallback. Translatable UI display routes through
 * `ScheduleViewTab.labelString()` in `ui/a11y/EnumLabels.kt`.
 */
enum class ScheduleViewTab(val label: String) {
    // Round 2.21 Phase E.1 — Schedule + ThreeDay added per D-2.21.f to
    // mirror Google Calendar's view set (Year stays). Final rail order:
    // Schedule · Day · 3-day · Week · Month · Agenda · Year. `Schedule`
    // is the agenda-list mode; `Agenda` keeps its Phase G.4 meaning as
    // the timebox view — distinct surfaces.
    Schedule("Schedule"),
    Day("Day"),
    ThreeDay("ThreeDay"),
    Week("Week"),
    Month("Month"),
    Agenda("Agenda"),
    Year("Year"),
}
