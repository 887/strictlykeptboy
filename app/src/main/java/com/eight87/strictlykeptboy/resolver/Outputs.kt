package com.eight87.strictlykeptboy.resolver

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

// -----------------------------------------------------------------------------
// Resolver output shapes.
// -----------------------------------------------------------------------------

sealed interface InstanceSource {
    data class OneOff(val eventId: EventRef) : InstanceSource
    data class RuleInstance(val ruleId: RuleRef, val originalStart: ZonedDateTime) : InstanceSource
}

/**
 * Completion-state classifier (RV-N / AT-B). Derived from the (event, now,
 * deviations) triple. Never red, never warning glyphs — UI is told that
 * separately; this is a pure tag.
 */
enum class CompletionState {
    Scheduled,          // future
    InProgress,         // now in window, no deviation
    CompletedBySchedule,// past, no deviation (passive habit default per AT)
    Skipped,
    PartiallyDone,
    CompletedEarly,
    CompletedLate,
}

/**
 * A materialized recurrence instance.
 *
 * `effectiveStart`/`effectiveEnd` differ from `originalStart`/`originalEnd`
 * when an `override` exception moved the instance (RV-B).
 */
data class MaterializedInstance(
    val source: InstanceSource,
    val calendar: CalendarRef,
    val repo: RepoRef,
    val originalStart: ZonedDateTime,
    val originalEnd: ZonedDateTime,
    val effectiveStart: ZonedDateTime,
    val effectiveEnd: ZonedDateTime,
    val title: String,
    val body: String,
    val emoji: String? = null,
    val tags: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val isBusy: Boolean = true,
    val isAllDay: Boolean = false,
    val priorityOverride: Int? = null,
    val author: PersonRef? = null,
    /**
     * Round 2.18.C.7 — non-null when this instance was materialized
     * from an event that came in via
     * [com.eight87.strictlykeptboy.system.SystemEventsBridge]. The
     * resolver still ignores it; only the UI detail-sheet header +
     * (future) writeback layer consult it.
     */
    val external: ExternalSource? = null,
    /** Round 2.21.A.3 — see [com.eight87.strictlykeptboy.store.Event.group]. */
    val group: String? = null,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.requiresResponse]. */
    val requiresResponse: Boolean = false,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.promptKind]. */
    val promptKind: com.eight87.strictlykeptboy.store.PromptKind? = null,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.promptTarget]. */
    val promptTarget: com.eight87.strictlykeptboy.store.PromptTarget? = null,
    /**
     * Round 2.24 / D-2.24.a — source-of-truth tz pin carried from the
     * originating [EventInput.tzId] (one-offs) or [RecurrenceInput.tzId]
     * (rule instances). `null` ⇒ event was unpinned at the source.
     * Drives the UI's "✈ <zone>" badge (Phase C) and the renderer's
     * source→display conversion (Phase B.3).
     */
    val sourceTzId: String? = null,
    /**
     * Passive habit marker — propagated from the source rule (for
     * rule instances) or always `false` for one-offs. Drives the
     * 📏 glyph on bands and (downstream) the passive/active visual
     * differentiation in copy and notifications.
     */
    val passive: Boolean = false,
) {
    val effectiveInterval: ZonedInterval get() = ZonedInterval(effectiveStart, effectiveEnd)
    /** Stable per-render id usable as Compose key and as cache primary key. */
    val instanceId: String get() = when (val s = source) {
        is InstanceSource.OneOff -> s.eventId.id
        is InstanceSource.RuleInstance -> "${s.ruleId.id}@${s.originalStart.toLocalDate()}"
    }
}

/**
 * A materialized instance plus all resolver-tagged annotations: lane
 * placement (collision band, RV-C / D.34), inversion state (RV-N),
 * supersedence flag (RV-O), off-schedule flag (RV-P).
 */
data class DayBand(
    val instance: MaterializedInstance,
    val priority: Int,
    val laneIndex: Int,
    val totalLanes: Int,
    val completionState: CompletionState = CompletionState.Scheduled,
    val supersededByCalendar: CalendarRef? = null,
    val offSchedule: Boolean = false,
    /**
     * Round 2.1.C.1 — per-calendar color seed. Piped from
     * `CalendarMeta.colorSeed` when set, otherwise falls back to the
     * calendar `displayName.hashCode()` at construction. UI consumers
     * map the seed to a chip / stripe / fill tint.
     */
    val accentColorSeed: Int = 0,
    /**
     * Round 2.1.C.3 — kind glyph hint. Mirrors the source
     * `CalendarMeta.kind` so renderers do not need to look up the
     * calendar by ref to pick a glyph.
     */
    val kind: CalendarKind = CalendarKind.Regular,
)

data class LayeredView(
    val rangeFrom: ZonedDateTime,
    val rangeTo: ZonedDateTime,
    val bandsByDay: Map<LocalDate, List<DayBand>>,
)

/**
 * Final Compose-ready render output. `viewMode` is carried so the UI
 * can match on it without re-checking.
 */
data class RenderedSchedule(
    val rangeFrom: ZonedDateTime,
    val rangeTo: ZonedDateTime,
    val viewMode: ViewMode,
    val days: List<RenderedDay>,
    val sourceDigest: String,
)

data class RenderedDay(
    val date: LocalDate,
    val bands: List<DayBand>,
    val densityBucket: Int,
)

/** A free interval emitted by [CommonTimeFinder]. */
data class TimeSlot(
    val from: ZonedDateTime,
    val toExclusive: ZonedDateTime,
) {
    val lengthMillis: Long get() = java.time.Duration.between(from, toExclusive).toMillis()
}

/** A weekly working-hours envelope used for common-time queries. */
data class TimeWindow(
    val daysOfWeek: Set<DayOfWeek>,
    val from: LocalTime,
    val toExclusive: LocalTime,
)
