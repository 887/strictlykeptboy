package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.store.PromptTarget
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
 * Round 2.27 / Phase B.2 — instances where
 * [MaterializedInstance.requiresResponse] is `true` are routed to
 * [TaskSource.KeeperPrompt] instead. The projector also drops prompt
 * instances whose date already has a response file on disk (see
 * [com.eight87.strictlykeptboy.store.PromptResponseReader]). Self-
 * targeted prompts authored by the configured boy persona are skipped
 * altogether — single-user free-mode shouldn't see its own pings.
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
     * @param boyAuthorId persona id of the boy on the active repo. Used
     *   to drop self-targeted keeper prompts in single-user free-mode.
     *   Empty string disables the skip (default for callers that don't
     *   know their boy id yet — Round 2.27 B.2 lock-in).
     * @param responseReader resolves the set of answered [LocalDate]s
     *   for a given `(calId, ruleId)`. Used to drop closed prompt
     *   instances. Default returns empty (no responses on file).
     */
    fun project(
        instances: List<MaterializedInstance>,
        calendarsById: Map<String, CalendarMeta>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        boyAuthorId: String = "",
        responseReader: (calId: String, ruleId: String) -> Set<LocalDate> = { _, _ -> emptySet() },
    ): List<TaskItem> = instances.mapNotNull { inst ->
        if (inst.title.isBlank()) return@mapNotNull null
        val cal = calendarsById[inst.calendar.id]
        // Skip explicit timeboxes — those are focus blocks, not chores.
        if (cal?.kind == CalendarKind.Timebox) return@mapNotNull null
        val start = inst.effectiveStart.withZoneSameInstant(zone)
        val isPrompt = inst.requiresResponse
        // Non-prompt FromEvents stay strictly same-day. Prompts include
        // today + past instances (D-2.27.d carry-over) but never future.
        if (!isPrompt) {
            if (start.toLocalDate() != today) return@mapNotNull null
        } else {
            if (start.toLocalDate().isAfter(today)) return@mapNotNull null
        }
        // Round 2.27.B.2 — single-user free-mode skip: if the prompt is
        // self-targeted AND the boy IS the keeper (same persona id),
        // suppress the row entirely so we don't ping ourselves.
        if (isPrompt &&
            inst.promptTarget == PromptTarget.Self &&
            boyAuthorId.isNotBlank() &&
            inst.author?.id == boyAuthorId
        ) {
            return@mapNotNull null
        }

        val ruleOrEventId = when (val src = inst.source) {
            is com.eight87.strictlykeptboy.resolver.InstanceSource.OneOff -> src.eventId.id
            is com.eight87.strictlykeptboy.resolver.InstanceSource.RuleInstance -> src.ruleId.id
        }

        if (isPrompt) {
            // Drop closed prompt instances (date has a response file).
            val answered = responseReader(inst.calendar.id, ruleOrEventId)
            if (start.toLocalDate() in answered) return@mapNotNull null
        }

        TaskItem(
            id = if (isPrompt) "keeper-prompt:${inst.instanceId}" else "from-event:${inst.instanceId}",
            title = inst.title,
            todolist = TodolistInfo(
                id = inst.calendar.id,
                repoId = inst.repo.id,
                name = cal?.displayName ?: inst.calendar.id,
                colorSeed = cal?.colorSeed?.toString() ?: inst.calendar.id,
            ),
            due = if (isPrompt) start.toLocalDate() else today,
            source = if (isPrompt) TaskSource.KeeperPrompt else TaskSource.FromEvents,
            author = inst.author?.id ?: "",
            linkedEventStart = start,
            linkedEventId = ruleOrEventId,
            promptKind = if (isPrompt) inst.promptKind else null,
            promptCalendarId = if (isPrompt) inst.calendar.id else "",
            promptRuleId = if (isPrompt) ruleOrEventId else "",
        )
    }

    /** Convenience overload for the "now" instant. */
    fun projectNow(
        instances: List<MaterializedInstance>,
        calendarsById: Map<String, CalendarMeta>,
        now: ZonedDateTime = ZonedDateTime.now(),
        boyAuthorId: String = "",
        responseReader: (calId: String, ruleId: String) -> Set<LocalDate> = { _, _ -> emptySet() },
    ): List<TaskItem> = project(
        instances = instances,
        calendarsById = calendarsById,
        today = now.toLocalDate(),
        zone = now.zone,
        boyAuthorId = boyAuthorId,
        responseReader = responseReader,
    )
}
