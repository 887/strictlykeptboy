package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.ViewMode
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.settings.VisibilityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    /**
     * Round 2.1.B.2 / B.8 — full calendar list across all repos, overlaid
     * with TOML fields by [com.eight87.strictlykeptboy.resolver.CalendarRegistry].
     * Surfaced for the [CalendarFilterChipStrip] rendered above the
     * schedule grid. Defaults to derived-from-snapshot when not injected.
     */
    val calendarsFlow: StateFlow<List<CalendarMeta>>? = null,
    /**
     * Round 2.1.B.8 — phone-local visibility overrides. The schedule
     * filters its rendered output to only calendars that are visible
     * here. `null` ⇒ all calendars visible (no override).
     */
    private val visibilityFlow: StateFlow<VisibilityState>? = null,
    /**
     * Round 2.5.D.1 — per-repo overlay flags. When non-null, the view-state
     * filters [RepoSnapshot.repos] / `calendars` / sources to only repos
     * with `showOnSchedule = true` BEFORE handing to the renderer. Bands
     * from hidden repos are excluded entirely. Null preserves the legacy
     * behaviour (all repos visible) for tests / previews.
     */
    private val repoConfigsFlow: StateFlow<List<RepoConfig>>? = null,
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
        val visFlow = visibilityFlow ?: MutableStateFlow(VisibilityState())
        val repoCfgFlow = repoConfigsFlow ?: MutableStateFlow(emptyList())
        // Round 2.25 follow-up — when the caller wires the registry-
        // enriched `calendarsFlow`, fold its CalendarMeta entries into
        // the snapshot we hand to the renderer. The raw IndexerSnapshot
        // synthesizes CalendarMeta(displayName=id, colorSeed=null) from
        // Room rows, so without this overlay the OverlayResolver never
        // sees per-calendar colorSeed values and every band ends up at
        // the `inst.calendar.id.hashCode()` fallback hue (which
        // coincidentally clusters green for the demo's calendar ids,
        // matching the user feedback 2026-05-17 "all the stuff in the
        // demo calendars is still green").
        val enrichedCalendars: StateFlow<List<CalendarMeta>?> =
            calendarsFlow?.let { src ->
                // Adapt non-null `StateFlow<List<CalendarMeta>>` into a
                // nullable-element flow so the combine arm can be `null`
                // when the caller didn't wire `calendarsFlow` at all.
                @Suppress("UNCHECKED_CAST")
                src as StateFlow<List<CalendarMeta>?>
            } ?: MutableStateFlow<List<CalendarMeta>?>(null)
        scope.launch {
            combine(
                _date, _selectedTab, snapshotFlow, sourcesFlow, visFlow, repoCfgFlow,
                enrichedCalendars,
            ) { args ->
                @Suppress("UNCHECKED_CAST")
                Septuple(
                    args[0] as LocalDate,
                    args[1] as ScheduleViewTab,
                    args[2] as RepoSnapshot,
                    args[3] as Renderer.Sources,
                    args[4] as VisibilityState,
                    args[5] as List<RepoConfig>,
                    args[6] as List<CalendarMeta>?,
                )
            }.collect { q ->
                val (range, viewMode) = rangeAndModeFor(q.a, q.b)
                val enrichedSnap = q.g?.let { enriched ->
                    overlayEnrichedCalendars(q.c, enriched)
                } ?: q.c
                val (repoFilteredSnap, repoFilteredSrc) =
                    applyRepoOverlay(enrichedSnap, q.d, q.f)
                val (filteredSnap, filteredSrc) =
                    applyVisibility(repoFilteredSnap, repoFilteredSrc, q.e)
                _rendered.value = renderer.render(
                    range = range,
                    viewMode = viewMode,
                    snapshot = filteredSnap,
                    sources = filteredSrc,
                    renderTz = tz,
                )
            }
        }
    }

    /**
     * Round 2.25 follow-up — replace the synthesized [CalendarMeta]
     * entries (one-per-id-with-only-displayName) with the registry-
     * enriched ones from disk (colorSeed, priority, supersedence,
     * activeWindows, etc.). Keyed on `(repoId, calendarId)`. Any
     * synthesized meta without a matching enriched entry is preserved
     * (handles in-flight calendars whose `calendar.toml` hasn't been
     * read yet).
     */
    private fun overlayEnrichedCalendars(
        snap: RepoSnapshot,
        enriched: List<CalendarMeta>,
    ): RepoSnapshot {
        if (enriched.isEmpty()) return snap
        val byKey = enriched.associateBy { it.repo.id to it.ref.id }
        val merged = snap.calendars.map { synth ->
            byKey[synth.repo.id to synth.ref.id] ?: synth
        }
        // Include any enriched entries with no matching synth (rare —
        // happens when calendar.toml exists but no events do yet).
        val synthKeys = snap.calendars.map { it.repo.id to it.ref.id }.toSet()
        val extras = enriched.filter { (it.repo.id to it.ref.id) !in synthKeys }
        return snap.copy(calendars = merged + extras)
    }

    private fun applyRepoOverlay(
        snap: RepoSnapshot,
        src: Renderer.Sources,
        repoConfigs: List<RepoConfig>,
    ): Pair<RepoSnapshot, Renderer.Sources> = applyRepoOverlayFilter(snap, src, repoConfigs)

    /**
     * Round 2.1.B.8 — apply phone-local visibility to the snapshot +
     * sources pair before handing to the renderer. Calendars hidden in
     * [VisibilityState] are filtered from snapshot.calendars (so the
     * renderer's `active` set never contains them) and their events /
     * rules are stripped from sources. No resolver change required.
     */
    private fun applyVisibility(
        snap: RepoSnapshot,
        src: Renderer.Sources,
        vis: VisibilityState,
    ): Pair<RepoSnapshot, Renderer.Sources> {
        if (vis.ordered.isEmpty()) return snap to src
        // visible-by-default unless explicit `visible = false`.
        val hiddenKeys = vis.ordered
            .filter { !it.visible }
            .map { it.repoId to it.id }
            .toSet()
        if (hiddenKeys.isEmpty()) return snap to src
        val keptCalendars = snap.calendars.filter { (it.repo.id to it.ref.id) !in hiddenKeys }
        val keptCalendarRefs = keptCalendars.map { it.ref }.toSet()
        val newSnap = snap.copy(calendars = keptCalendars)
        val newSrc = src.copy(
            events = src.events.filter { it.calendar in keptCalendarRefs },
            rules = src.rules.filter { it.calendar in keptCalendarRefs },
        )
        return newSnap to newSrc
    }

    fun setDate(date: LocalDate) { _date.value = date }
    fun setSelectedTab(tab: ScheduleViewTab) { _selectedTab.value = tab }

    private fun rangeAndModeFor(d: LocalDate, t: ScheduleViewTab): Pair<DateRange, ViewMode> =
        when (t) {
            // Round 2.21 Phase E.1 — Schedule (agenda list) renders the
            // current week's worth of bands sorted by start; resolver-side
            // it's a 7-day range with Agenda view-mode for sort + grouping.
            ScheduleViewTab.Schedule -> {
                val start = d.with(TemporalAdjusters.previousOrSame(weekStart))
                DateRange(start, start.plusDays(6)) to ViewMode.Agenda
            }
            ScheduleViewTab.Day -> DateRange(d, d) to ViewMode.Day
            ScheduleViewTab.ThreeDay -> DateRange(d, d.plusDays(2)) to ViewMode.Week
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

    @Suppress("unused")
    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
    private data class Sextuple<A, B, C, D, E, F>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F,
    )
    private data class Septuple<A, B, C, D, E, F, G>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G,
    )
}

/**
 * Round 2.5.D.1 — filter snapshot + sources to only repos with
 * `RepoConfig.showOnSchedule = true`. Pure function exposed as
 * top-level for direct unit-testing (PerRepoOverlayResolverTest).
 *
 * Empty [repoConfigs] ⇒ no filter (back-compat).
 */
fun applyRepoOverlayFilter(
    snap: RepoSnapshot,
    src: Renderer.Sources,
    repoConfigs: List<RepoConfig>,
): Pair<RepoSnapshot, Renderer.Sources> {
    if (repoConfigs.isEmpty()) return snap to src
    val visibleRepoIds = repoConfigs
        .filter { it.showOnSchedule }
        .map { it.repoId }
        .toSet()
    val snapRepoIds = snap.repos.map { it.ref.id }.toSet()
    if (snapRepoIds.all { it in visibleRepoIds }) return snap to src
    val keptRepos = snap.repos.filter { it.ref.id in visibleRepoIds }
    val keptCalendars = snap.calendars.filter { it.repo.id in visibleRepoIds }
    val keptTodolists = snap.todolists.filter { it.repo.id in visibleRepoIds }
    val newSnap = snap.copy(
        repos = keptRepos,
        calendars = keptCalendars,
        todolists = keptTodolists,
    )
    val keptCalendarRefs = keptCalendars.map { it.ref }.toSet()
    val newSrc = src.copy(
        events = src.events.filter {
            it.repo.id in visibleRepoIds && it.calendar in keptCalendarRefs
        },
        rules = src.rules.filter {
            it.repo.id in visibleRepoIds && it.calendar in keptCalendarRefs
        },
    )
    return newSnap to newSrc
}
