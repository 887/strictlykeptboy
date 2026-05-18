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
    // Rail order: Now · Day · 3-day · Week · Month · Year.
    // `Now` is the agenda-list (today + forward week), reusing
    // ScheduleAgendaView's renderer. Replaces the prior `Schedule` +
    // `Agenda` tabs; persisted values of either migrate to `Now` via
    // [ScheduleViewModePrefs.load].
    Now("Now"),
    Day("Day"),
    ThreeDay("ThreeDay"),
    Week("Week"),
    Month("Month"),
    Year("Year"),
}
