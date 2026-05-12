package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.ViewMode
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Phase F.4 / G.6 — state holder for SchedulePane.
 *
 * Phase F initially shipped a Day-only view. Phase G generalizes the
 * rendered range to match the selected tab: Day = 1 day, Week = 7 days
 * starting from the locale-aware week start, Month = the calendar
 * month containing [date], Agenda (timebox) = 1 day, Year = the
 * calendar year containing [date].
 *
 * View-mode persistence lives in [ScheduleViewModePrefs] and is wired
 * by the caller (MainActivity) — this class owns the in-memory tab
 * StateFlow only.
 */
@Immutable
data class ScheduleSnapshot(
    val date: LocalDate,
    val schedule: RenderedSchedule?,
)

class ScheduleViewState(
    private val scope: CoroutineScope,
    private val renderer: Renderer = Renderer(),
    private val snapshotFlow: StateFlow<RepoSnapshot>,
    private val sourcesFlow: StateFlow<Renderer.Sources>,
    initialDate: LocalDate = LocalDate.now(),
    initialTab: ScheduleViewTab = ScheduleViewTab.Day,
    private val tz: ZoneId = ZoneId.systemDefault(),
    private val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    private val _date = MutableStateFlow(initialDate)
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _selectedTab = MutableStateFlow(initialTab)
    val selectedTab: StateFlow<ScheduleViewTab> = _selectedTab.asStateFlow()

    private val _rendered = MutableStateFlow<RenderedSchedule?>(null)
    val rendered: StateFlow<RenderedSchedule?> = _rendered.asStateFlow()

    init {
        scope.launch {
            combine(_date, _selectedTab, snapshotFlow, sourcesFlow) { d, t, snap, src ->
                Quadruple(d, t, snap, src)
            }.collect { q ->
                val d = q.a; val t = q.b; val snap = q.c; val src = q.d
                val (range, viewMode) = rangeAndModeFor(d, t)
                _rendered.value = renderer.render(
                    range = range,
                    viewMode = viewMode,
                    snapshot = snap,
                    sources = src,
                    renderTz = tz,
                )
            }
        }
    }

    fun setDate(date: LocalDate) { _date.value = date }
    fun setSelectedTab(tab: ScheduleViewTab) { _selectedTab.value = tab }

    private fun rangeAndModeFor(d: LocalDate, t: ScheduleViewTab): Pair<DateRange, ViewMode> =
        when (t) {
            ScheduleViewTab.Day -> DateRange(d, d) to ViewMode.Day
            ScheduleViewTab.Week -> {
                val start = d.with(TemporalAdjusters.previousOrSame(weekStart))
                DateRange(start, start.plusDays(6)) to ViewMode.Week
            }
            ScheduleViewTab.Month -> {
                val first = d.withDayOfMonth(1)
                val last = d.with(TemporalAdjusters.lastDayOfMonth())
                DateRange(first, last) to ViewMode.Month
            }
            // Agenda tab maps to the "Timebox" view per Phase G.4 — today's
            // planned focus blocks edge-to-edge. Resolver-side, that's just
            // a single-day range with Agenda view-mode for sort-by-start.
            ScheduleViewTab.Agenda -> DateRange(d, d) to ViewMode.Agenda
            ScheduleViewTab.Year -> {
                val first = d.withDayOfYear(1)
                val last = d.withMonth(12).withDayOfMonth(31)
                DateRange(first, last) to ViewMode.Month // year heatmap reuses month-style bucketing
            }
        }

    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
