package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.1.D.8 — reciprocal "spawned-from-event" task projector.
 *
 * Maps a slice of [MaterializedInstance]s into synthetic [TaskItem]s
 * tagged with [TaskSource.FromEvents] so they show up in the Today
 * view's "from events" section. The genesis use-case: recurring chores
 * (washing, household checks) live as recurring events in the calendar
 * + their per-day occurrences should also appear in Tasks → Today.
 *
 * Heuristic for "this event spawns a task": the event's calendar is
 * `Regular` (timeboxes are explicit focus blocks, not chores) AND its
 * title is not empty. Caller can pre-filter for `chore`-tagged
 * calendars by passing a subset of [calendarsById] only containing
 * chore calendars.
 *
 * Pure-function, no I/O. Single-responsibility: it does NOT join with
 * the todolist roster — instead it fabricates a `TodolistInfo` keyed
 * on the calendar's id + display name. This keeps the projector
 * compatible with both repos that have a 1-to-1 calendar↔todolist
 * pairing and repos that don't.
 */
object FromEventsProjector {

    /**
     * @param instances all materialized instances visible to the UI.
     * @param calendarsById map of CalendarRef.id → CalendarMeta (used
     *   for kind + display).
     * @param today date scope: only same-day instances become FromEvents
     *   tasks. The caller usually pipes `LocalDate.now()`.
     */
    fun project(
        instances: List<MaterializedInstance>,
        calendarsById: Map<String, CalendarMeta>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TaskItem> = instances.mapNotNull { inst ->
        if (inst.title.isBlank()) return@mapNotNull null
        val cal = calendarsById[inst.calendar.id]
        // Skip explicit timeboxes — those are focus blocks, not chores.
        if (cal?.kind == CalendarKind.Timebox) return@mapNotNull null
        val start = inst.effectiveStart.withZoneSameInstant(zone)
        if (start.toLocalDate() != today) return@mapNotNull null
        TaskItem(
            id = "from-event:${inst.instanceId}",
            title = inst.title,
            todolist = TodolistInfo(
                id = inst.calendar.id,
                repoId = inst.repo.id,
                name = cal?.displayName ?: inst.calendar.id,
                colorSeed = cal?.colorSeed?.toString() ?: inst.calendar.id,
            ),
            due = today,
            source = TaskSource.FromEvents,
            author = inst.author?.id ?: "",
            linkedEventStart = start,
            linkedEventId = when (val src = inst.source) {
                is com.eight87.strictlykeptboy.resolver.InstanceSource.OneOff -> src.eventId.id
                is com.eight87.strictlykeptboy.resolver.InstanceSource.RuleInstance -> src.ruleId.id
            },
        )
    }

    /** Convenience overload for the "now" instant. */
    fun projectNow(
        instances: List<MaterializedInstance>,
        calendarsById: Map<String, CalendarMeta>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): List<TaskItem> = project(
        instances = instances,
        calendarsById = calendarsById,
        today = now.toLocalDate(),
        zone = now.zone,
    )
}
