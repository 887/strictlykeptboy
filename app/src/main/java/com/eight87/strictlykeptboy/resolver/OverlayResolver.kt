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
        now: ZonedDateTime = ZonedDateTime.now(),
    ): LayeredView {
        val calMetaByRef = snapshot.calendars.associateBy { it.ref }

        // RV-O: per-day supersedence map. For each active calendar that
        // supersedes others, mark the suppressed calendar's bands.
        val supersedeMap: Map<CalendarRef, CalendarRef> = activeCalendars
            .mapNotNull { calMetaByRef[it] }
            .flatMap { suppressor ->
                suppressor.supersedes
                    .filter { it in activeCalendars }
                    .map { suppressed -> suppressed to suppressor.ref }
            }
            .toMap()

        // RV-O override re-include: an override pinned to a specific event/date forces show.
        val forcedShownByEventOnDate: Map<Pair<String, LocalDate>, Unit> = overrides
            .filter { it.kind == "force-show" || it.kind == "force-show-for-range" }
            .flatMap { ov ->
                when (ov.kind) {
                    "force-show" -> listOf(ov.eventId to ov.instanceDate)
                    else -> {
                        val from = ov.rangeFrom ?: ov.instanceDate
                        val to = ov.rangeTo ?: ov.instanceDate
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

        val bandsByDay = byDay.mapValues { (_, dayInstances) ->
            assignLanes(dayInstances, calMetaByRef).map { laneBand ->
                val supersededBy = supersedeMap[laneBand.instance.calendar]
                val day = laneBand.instance.effectiveStart.toLocalDate()
                val forceShown = (laneBand.instance.instanceId to day) in forcedShownByEventOnDate
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
            DayBand(
                instance = inst,
                priority = priority,
                laneIndex = lane,
                totalLanes = total,
            )
        }
    }

    private fun inversionStateFor(
        inst: MaterializedInstance,
        deviations: List<DeviationInput>,
        now: ZonedDateTime,
    ): CompletionState {
        val targetId = when (val s = inst.source) {
            is InstanceSource.OneOff -> s.eventId.id
            is InstanceSource.RuleInstance -> s.ruleId.id
        }
        val day = inst.effectiveStart.toLocalDate()
        val dev = deviations.firstOrNull { it.targetId == targetId && it.instanceDate == day }
        if (dev != null) return when (dev.kind) {
            "skipped" -> CompletionState.Skipped
            "partial" -> CompletionState.PartiallyDone
            "completed-early" -> CompletionState.CompletedEarly
            "completed-late" -> CompletionState.CompletedLate
            else -> CompletionState.Scheduled
        }
        return when {
            now.isBefore(inst.effectiveStart) -> CompletionState.Scheduled
            now.isBefore(inst.effectiveEnd) -> CompletionState.InProgress
            else -> CompletionState.CompletedBySchedule
        }
    }

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
