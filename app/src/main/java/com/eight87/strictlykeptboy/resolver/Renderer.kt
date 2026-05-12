package com.eight87.strictlykeptboy.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase E.4 / RV-D — render pipeline.
 *
 * Orchestrates `ActiveSetEvaluator → RecurrenceMaterializer →
 * OverlayResolver → view-mode-specific transformations`. The output is
 * pure data; Compose in Phase F-H consumes it without further work.
 *
 * Off-schedule detection (RV-P) is layered here because it needs the
 * full set of materialized rule instances per calendar before it can
 * decide what's "outside cadence" for a given event.
 */
class Renderer(
    private val activeSet: ActiveSetEvaluator = ActiveSetEvaluator(),
    private val materializer: RecurrenceMaterializer = RecurrenceMaterializer(),
    private val overlay: OverlayResolver = OverlayResolver(activeSet),
) {

    /**
     * Caller-supplied bundle of source rows for [render].
     *
     * The resolver does not call DAOs directly — the view-model layer
     * collects from `EventDao.byDateRange`, `RecurrenceRuleDao.byCalendar`,
     * etc., and hands the materialized lists here. Keeps the resolver
     * Android-free (CLAUDE.md quality bar) and unit-testable.
     */
    data class Sources(
        val events: List<EventInput>,
        val rules: List<RecurrenceInput>,
        val exceptionsByRule: Map<RuleRef, List<ExceptionInput>>,
        val deviations: List<DeviationInput>,
        val overrides: List<OverrideInput>,
    )

    /**
     * Render [range] in [viewMode] from [sources]. [now] is injected for
     * deterministic inversion-state tests (RV-N).
     */
    suspend fun render(
        range: DateRange,
        viewMode: ViewMode,
        snapshot: RepoSnapshot,
        sources: Sources,
        renderTz: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(renderTz),
    ): RenderedSchedule = withContext(Dispatchers.Default) {
        val rangeFrom = range.start.atStartOfDay(renderTz)
        val rangeToExclusive = (range.endInclusive ?: range.start).plusDays(1).atStartOfDay(renderTz)

        // Active calendars are evaluated at the midpoint of the range for
        // a single-snapshot answer. Day-by-day re-evaluation is a v2 nicety
        // and not needed for v1's coarse calendar-toggle semantics.
        val sampleAt = rangeFrom.plus(java.time.Duration.between(rangeFrom, rangeToExclusive).dividedBy(2))
        val active = activeSet.activeCalendarsAt(sampleAt, snapshot, sources.overrides)

        val materializedRules = sources.rules
            .filter { it.calendar in active }
            .flatMap { rule ->
                materializer.expand(
                    rule,
                    sources.exceptionsByRule[rule.rule].orEmpty(),
                    range,
                )
            }

        val oneOffs = sources.events
            .filter { it.calendar in active }
            .map { materializer.fromOneOff(it) }
            .filter { it.effectiveStart.isBefore(rangeToExclusive) && it.effectiveEnd.isAfter(rangeFrom) }

        val allInstances = oneOffs + materializedRules

        val layered = overlay.layer(
            activeCalendars = active,
            instances = allInstances,
            snapshot = snapshot,
            rangeFrom = rangeFrom,
            rangeTo = rangeToExclusive,
            deviations = sources.deviations,
            overrides = sources.overrides,
            now = now,
        )

        val offSchedule = computeOffScheduleEventIds(snapshot, sources, oneOffs)

        val days = enumerateDays(range).map { d ->
            val bands = (layered.bandsByDay[d] ?: emptyList())
                .map { b ->
                    if (b.instance.instanceId in offSchedule) b.copy(offSchedule = true) else b
                }
                .let { filterForViewMode(it, viewMode) }
                .let { sortBandsForView(it, viewMode) }
            RenderedDay(
                date = d,
                bands = bands,
                densityBucket = bucketize(bands.size),
            )
        }

        RenderedSchedule(
            rangeFrom = rangeFrom,
            rangeTo = rangeToExclusive,
            viewMode = viewMode,
            days = days,
            sourceDigest = snapshot.contentHash,
        )
    }

    /**
     * RV-P off-schedule: a one-off event whose calendar declares a
     * `baselineCadenceDays` and whose start is further than that cadence
     * from every materialized rule instance on the same calendar inside
     * the rendered range is tagged.
     *
     * The check is intentionally local-to-range to keep cost bounded.
     */
    private fun computeOffScheduleEventIds(
        snapshot: RepoSnapshot,
        sources: Sources,
        oneOffs: List<MaterializedInstance>,
    ): Set<String> {
        val calMeta = snapshot.calendars.associateBy { it.ref }
        val ruleStartsByCal: Map<CalendarRef, List<Long>> = sources.rules
            .groupBy { it.calendar }
            .mapValues { (_, rs) -> rs.map { it.dtstart.toInstant().toEpochMilli() } }
        return oneOffs.mapNotNull { inst ->
            val cadence = calMeta[inst.calendar]?.baselineCadenceDays ?: return@mapNotNull null
            val cadenceMs = cadence * 86_400_000L
            val starts = ruleStartsByCal[inst.calendar] ?: return@mapNotNull inst.instanceId
            val instMs = inst.effectiveStart.toInstant().toEpochMilli()
            val minDelta = starts.minOf { kotlin.math.abs(it - instMs) }
            if (minDelta > cadenceMs) inst.instanceId else null
        }.toSet()
    }

    private fun filterForViewMode(bands: List<DayBand>, viewMode: ViewMode): List<DayBand> {
        // RV-O: render pipeline drops superseded bands (the overlay layer tagged them).
        val notSuperseded = bands.filter { it.supersededByCalendar == null }
        return when (viewMode) {
            is ViewMode.Todolist -> notSuperseded // todolist mode is event-agnostic but harmless to include
            else -> notSuperseded
        }
    }

    private fun sortBandsForView(bands: List<DayBand>, viewMode: ViewMode): List<DayBand> {
        val byStart = compareBy<DayBand> { it.instance.effectiveStart.toInstant() }
            .thenByDescending { it.priority }
            .thenBy { it.instance.instanceId }
        return when (viewMode) {
            ViewMode.Agenda -> bands.sortedWith(byStart)
            else -> bands.sortedWith(
                compareBy<DayBand> { it.laneIndex }.thenBy { it.instance.effectiveStart.toInstant() },
            )
        }
    }

    private fun bucketize(count: Int): Int = when {
        count == 0 -> 0
        count <= 3 -> 1
        count <= 7 -> 2
        else -> 3
    }

    private fun enumerateDays(range: DateRange): List<LocalDate> {
        val end = range.endInclusive ?: range.start
        if (end.isBefore(range.start)) return emptyList()
        return generateSequence(range.start) { d ->
            if (d.isBefore(end)) d.plusDays(1) else null
        }.toList()
    }
}
