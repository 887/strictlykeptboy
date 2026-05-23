package com.eight87.strictlykeptboy.resolver

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Phase E.3 / RV-C — overlay layer.
 *
 * Combines one-off events and materialized recurrence instances, tags
 * each with its source-calendar priority (per D.20; `priorityOverride`
 * on the event wins over the calendar's), and assigns each band to a
 * horizontal lane within its day cell so overlapping bands are visually
 * side-by-side.
 *
 * Collisions do NOT merge (D.34) — every event keeps its own band; the
 * lane assignment is what the UI uses to lay them out.
 *
 * Inversion (RV-N) and supersedence (RV-O) are tagged here; the renderer
 * downstream is responsible for filtering supersedence-marked bands per
 * view mode.
 */
class OverlayResolver(
    private val activeSetEvaluator: ActiveSetEvaluator = ActiveSetEvaluator(),
) {

    fun layer(
        activeCalendars: Set<CalendarRef>,
        instances: List<MaterializedInstance>,
        snapshot: RepoSnapshot,
        rangeFrom: ZonedDateTime,
        rangeTo: ZonedDateTime,
        deviations: List<DeviationInput> = emptyList(),
        overrides: List<OverrideInput> = emptyList(),
        now: ZonedDateTime,
    ): LayeredView {
        val calMetaByRef = snapshot.calendars.associateBy { it.ref }

        // Round 2026-05-23 — per-day suppressor presence. A supersedor
        // calendar (e.g. `vacation`) only suppresses on days where it
        // actually has an instance — a Brighton-weekend event Sat–Sun
        // should not silence the morning routine on the following
        // Tuesday. The previous range-wide map activated supersedence
        // for the whole render window whenever the suppressor was
        // active at "now", which over-suppressed in production
        // (vacation with empty activeWindows = always active) and
        // forced test fixtures to fabricate static activeWindows from
        // each one-off event's start/end. With this change the user
        // can author Brighton as a single weekend event + ship the
        // vacation overlay with no per-trip activeWindows toml edits.
        val suppressorDaysByRef: Map<CalendarRef, Set<LocalDate>> = instances
            .filter { it.calendar in activeCalendars }
            .filter { calMetaByRef[it.calendar]?.supersedes?.isNotEmpty() == true }
            .flatMap { inst -> daysCovered(inst, rangeFrom, rangeTo).map { it to inst.calendar } }
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, days) -> days.toSet() }

        // RV-O override re-include: an override pinned to a specific event/date forces show.
        val forcedShownByEventOnDate: Map<Pair<String, LocalDate>, Unit> = overrides
            .flatMap { ov ->
                when (val k = ov.kind) {
                    is OverrideKind.ForceShow -> listOf(ov.eventId to ov.instanceDate)
                    is OverrideKind.ForceShowForRange -> {
                        val from = k.from ?: ov.instanceDate
                        val to = k.to ?: ov.instanceDate
                        generateSequence(from) { d -> if (d.isBefore(to)) d.plusDays(1) else null }
                            .map { ov.eventId to it }
                            .toList()
                    }
                }
            }
            .associate { it to Unit }

        // Group by day (in renderer's effective tz: use each instance's own zone).
        val byDay = instances
            .filter { it.calendar in activeCalendars }
            .flatMap { inst -> daysCovered(inst, rangeFrom, rangeTo).map { it to inst } }
            .groupBy({ it.first }) { it.second }

        val bandsByDay = byDay.mapValues { (day, dayInstances) ->
            // Per-day supersedence: a suppressor is "in effect" on this
            // day if it has at least one instance covering this day.
            // No instance on this day → no supersedence (e.g. vacation
            // doesn't silence the Tuesday morning routine just because
            // a Brighton-weekend event exists on Sat–Sun).
            val suppressorsToday = activeCalendars
                .mapNotNull { calMetaByRef[it] }
                .filter { meta ->
                    if (meta.supersedes.isEmpty()) return@filter false
                    day in suppressorDaysByRef[meta.ref].orEmpty()
                }
            val perDaySupersedeMap: Map<CalendarRef, CalendarRef> = suppressorsToday
                .flatMap { suppressor ->
                    suppressor.supersedes
                        .filter { it in activeCalendars }
                        .map { suppressed -> suppressed to suppressor.ref }
                }
                .toMap()
            assignLanes(dayInstances, calMetaByRef).map { laneBand ->
                val supersededBy = perDaySupersedeMap[laneBand.instance.calendar]
                val instDay = laneBand.instance.effectiveStart.toLocalDate()
                val forceShown = (laneBand.instance.instanceId to instDay) in forcedShownByEventOnDate
                laneBand.copy(
                    supersededByCalendar = if (supersededBy != null && !forceShown) supersededBy else null,
                    completionState = inversionStateFor(laneBand.instance, deviations, now),
                )
            }
        }

        return LayeredView(rangeFrom, rangeTo, bandsByDay)
    }

    /**
     * Lane-assignment is a classic interval-graph coloring: sort by
     * `(priority desc, start asc, id asc)`; for each band, take the
     * lowest-numbered lane that doesn't currently hold a still-overlapping
     * predecessor.
     *
     * `totalLanes` is the max lane index used on that day + 1 so each
     * band knows the day's width.
     */
    private fun assignLanes(
        instances: List<MaterializedInstance>,
        calMetaByRef: Map<CalendarRef, CalendarMeta>,
    ): List<DayBand> {
        if (instances.isEmpty()) return emptyList()
        val withPriority = instances.map { inst ->
            val baseP = inst.priorityOverride ?: calMetaByRef[inst.calendar]?.priority ?: 500
            inst to baseP
        }
        val sorted = withPriority.sortedWith(
            compareByDescending<Pair<MaterializedInstance, Int>> { it.second }
                .thenBy { it.first.effectiveStart.toInstant() }
                .thenBy { it.first.instanceId },
        )

        // lane index -> end time of current occupant
        val laneEnds = mutableListOf<ZonedDateTime>()
        val assignments = mutableListOf<Triple<MaterializedInstance, Int, Int>>() // (inst, priority, lane)
        for ((inst, priority) in sorted) {
            val laneIdx = laneEnds.indexOfFirst { !it.isAfter(inst.effectiveStart) }
            val chosen = if (laneIdx == -1) {
                laneEnds += inst.effectiveEnd
                laneEnds.size - 1
            } else {
                laneEnds[laneIdx] = inst.effectiveEnd
                laneIdx
            }
            assignments += Triple(inst, priority, chosen)
        }
        val total = laneEnds.size

        return assignments.map { (inst, priority, lane) ->
            val meta = calMetaByRef[inst.calendar]
            // Round 2.1.C.1: seed defaults to displayName.hashCode() so even
            // calendars without an explicit colorSeed get stable per-calendar
            // tinting in the UI.
            val seed = meta?.colorSeed
                ?: meta?.displayName?.hashCode()
                ?: inst.calendar.id.hashCode()
            DayBand(
                instance = inst,
                priority = priority,
                laneIndex = lane,
                totalLanes = total,
                accentColorSeed = seed,
                kind = meta?.kind ?: CalendarKind.Regular,
            )
        }
    }

    private fun inversionStateFor(
        inst: MaterializedInstance,
        deviations: List<DeviationInput>,
        now: ZonedDateTime,
    ): CompletionState = CompletionStateResolver.resolveFor(inst, deviations, now)

    private fun daysCovered(
        inst: MaterializedInstance,
        rangeFrom: ZonedDateTime,
        rangeTo: ZonedDateTime,
    ): List<LocalDate> {
        val startDay = maxOf(inst.effectiveStart, rangeFrom).toLocalDate()
        // half-open end: subtract 1ns so a midnight-aligned end stays on prior day
        val endInclusive = minOf(inst.effectiveEnd.minusNanos(1), rangeTo.minusNanos(1))
            .toLocalDate()
        if (endInclusive.isBefore(startDay)) return emptyList()
        return generateSequence(startDay) { d ->
            if (d.isBefore(endInclusive)) d.plusDays(1) else null
        }.toList()
    }
}
