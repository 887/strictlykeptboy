package com.eight87.strictlykeptboy.store

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase XX.8 / AT-H — pure routine-materialization engine.
 *
 * A *routine* (XX.7) is an ordinary calendar whose `calendar.toml`
 * carries `routine = true`. Quick-start (AT-H) walks the routine's
 * atomic entries forward back-to-back from a chosen start-time using
 * each entry's `duration_minutes`, then writes each as a real event
 * file in the *target* calendar with audit frontmatter
 * (`materialized_from`, `materialized_source_event`, `materialized_at`).
 *
 * This object owns the **pure algorithm** — slot assignment + overlap
 * detection. Actual file IO + git commit live in the caller (the
 * Android UI sheet, the `:cli` `skb routine start` command). Splitting
 * pure-vs-IO here is the SOLID-D + SOLID-S move: both surfaces share
 * the algorithm without dragging in either Android or JGit.
 *
 * AT-H.3 algorithm (LOCKED):
 *   1. Read selected entries in routine-calendar order.
 *   2. Walk forward from start-time, assigning each entry
 *      `[cursor, cursor + duration_minutes)`.
 *   3. The caller writes the resulting [PlannedEvent]s as event files.
 *   4. Caller wraps everything in a single git commit
 *      `materialize routine "<name>" at <start-time>`.
 *
 * AT-H.4 overlap guard: [plan] returns the conflicts so the caller can
 * surface a sheet offering (a) shift past, (b) skip overlapping, (c)
 * cancel. The pure side never silently overwrites.
 */
object RoutineMaterializer {

    /** Compact input: the atomic-entry slice the user kept ticked. */
    data class Entry(
        val sourceEventId: String,
        val title: String,
        val durationMinutes: Int,
        val tags: List<String> = emptyList(),
        val subbeats: List<AtomicTemplateSubbeat> = emptyList(),
    )

    /** One existing event in the target window, narrow on purpose. */
    data class ExistingSlot(val start: OffsetDateTime, val end: OffsetDateTime)

    /** Plan output: events to write + any conflicts the caller must address. */
    data class Plan(
        val planned: List<PlannedEvent>,
        val conflicts: List<Conflict>,
    ) {
        val hasConflicts: Boolean get() = conflicts.isNotEmpty()
    }

    /** A single event ready to materialize. Frontmatter audit fields filled. */
    data class PlannedEvent(
        val sourceEventId: String,
        val title: String,
        val start: OffsetDateTime,
        val end: OffsetDateTime,
        val tags: List<String>,
        val subbeats: List<AtomicTemplateSubbeat>,
    )

    /** One overlap between a planned slot and an existing event. */
    data class Conflict(
        val sourceEventId: String,
        val plannedStart: OffsetDateTime,
        val plannedEnd: OffsetDateTime,
        val existing: ExistingSlot,
    )

    /**
     * Build the plan from [entries] starting at [startTime].
     *
     * @param entries entries the user ticked, in routine-calendar order.
     * @param startTime first slot's start.
     * @param existing existing events in the target calendar's window;
     *   only need entries that could conceivably overlap. Caller is
     *   responsible for pre-filtering to a sensible window.
     */
    fun plan(
        entries: List<Entry>,
        startTime: OffsetDateTime,
        existing: List<ExistingSlot> = emptyList(),
    ): Plan {
        val planned = mutableListOf<PlannedEvent>()
        val conflicts = mutableListOf<Conflict>()
        var cursor = startTime
        for (e in entries) {
            val end = cursor.plusMinutes(e.durationMinutes.toLong())
            val slotConflicts = existing.filter { overlaps(cursor, end, it.start, it.end) }
            for (c in slotConflicts) {
                conflicts += Conflict(
                    sourceEventId = e.sourceEventId,
                    plannedStart = cursor,
                    plannedEnd = end,
                    existing = c,
                )
            }
            planned += PlannedEvent(
                sourceEventId = e.sourceEventId,
                title = e.title,
                start = cursor,
                end = end,
                tags = e.tags,
                subbeats = e.subbeats,
            )
            cursor = end
        }
        return Plan(planned, conflicts)
    }

    /**
     * "Shift past" resolution (AT-H.4.a): if the planned window
     * conflicts with any existing slot, slide [startTime] forward to
     * the first free moment past every conflict, then re-plan.
     */
    fun shiftPast(
        entries: List<Entry>,
        startTime: OffsetDateTime,
        existing: List<ExistingSlot>,
    ): Plan {
        val totalMinutes = entries.sumOf { it.durationMinutes.toLong() }
        var probe = startTime
        // Walk forward across existing.end values that intersect the
        // running window until no overlap exists.
        var advanced = true
        while (advanced) {
            advanced = false
            val probeEnd = probe.plusMinutes(totalMinutes)
            for (slot in existing) {
                if (overlaps(probe, probeEnd, slot.start, slot.end)) {
                    probe = slot.end
                    advanced = true
                    break
                }
            }
        }
        return plan(entries, probe, existing)
    }

    private fun overlaps(
        aStart: OffsetDateTime,
        aEnd: OffsetDateTime,
        bStart: OffsetDateTime,
        bEnd: OffsetDateTime,
    ): Boolean = aStart.isBefore(bEnd) && bStart.isBefore(aEnd)

    /** ISO-8601 with offset — used for both `start` / `end` and `materialized_at`. */
    fun formatIso(t: OffsetDateTime): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t)
}
