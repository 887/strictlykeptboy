package com.eight87.strictlykeptboy.resolver

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase E (resolver) — public data model.
 *
 * Pure Kotlin / java.time only. The resolver runs on `Dispatchers.Default`
 * and never imports `android.*`; UI in Phase F-H is the sole consumer of
 * the structures defined here.
 *
 * Identifier wrappers are kept as plain inline `value class` aliases over
 * `String` rather than UUID instances — every entity in the store is a
 * UUIDv7-stringed file name (DM-D) and the resolver is comparison-only.
 */

@JvmInline value class RepoRef(val id: String)
@JvmInline value class CalendarRef(val id: String)
@JvmInline value class TodolistRef(val id: String)
@JvmInline value class RuleRef(val id: String)
@JvmInline value class EventRef(val id: String)
@JvmInline value class PersonRef(val id: String)

/** Open-ended on the upper bound iff [endInclusive] is `null` (per RV-A). */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate?) {
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && (endInclusive == null || !date.isAfter(endInclusive))
}

/**
 * Active-hours predicate, evaluated in the calendar's tz.
 *
 * `from` inclusive, `to` exclusive (RV-A locked decision).
 * `to <= from` (but not equal) means the range crosses midnight.
 * `to == from` is an explicit "never active" signal.
 */
data class HourRange(val day: DayOfWeek, val from: LocalTime, val to: LocalTime)

/** A frozen [from, to] half-open instant interval. */
data class InstantInterval(val from: Instant, val toExclusive: Instant) {
    init { require(!toExclusive.isBefore(from)) { "InstantInterval inverted" } }
    fun overlaps(other: InstantInterval): Boolean =
        from.isBefore(other.toExclusive) && other.from.isBefore(toExclusive)
    fun lengthMillis(): Long = toExclusive.toEpochMilli() - from.toEpochMilli()
}

/** Closed half-open `[from, to)` zoned interval used by the renderer. */
data class ZonedInterval(val from: ZonedDateTime, val toExclusive: ZonedDateTime) {
    fun toInstantInterval(): InstantInterval =
        InstantInterval(from.toInstant(), toExclusive.toInstant())
    fun lengthMillis(): Long = java.time.Duration.between(from, toExclusive).toMillis()
}

/**
 * Round 2.18.A.3 — kind of calendar surface.
 *
 * - [Regular]: file-backed event calendar in a skb repo.
 * - [Timebox]: file-backed timebox calendar (focus sessions, work blocks).
 * - [External]: synthetic calendar backed by Android's CalendarContract
 *   (Google, Exchange, iCloud, etc. — provided by the OS sync adapters).
 *   External calendars are read-only in Phase A; Phase D adds write-back.
 *   Resolver treats `External` as regular-equivalent for active-windows,
 *   priority, supersedence, and inversion semantics — the distinction
 *   only matters for the writeback layer + UI badges.
 */
enum class CalendarKind { Regular, Timebox, External }

/**
 * Resolver-facing calendar metadata. Caller (UI / view-model layer)
 * builds this from a parsed `calendars/<id>/calendar.md` plus any
 * runtime settings overrides; the resolver only reads.
 *
 * `priority` is a per-overlay integer per D.20 (1..1000; 999 = special).
 * `supersedes` lists the calendar IDs this calendar suppresses while
 * its own active-windows match — see RV-O / HV-E.
 */
data class CalendarMeta(
    val ref: CalendarRef,
    val repo: RepoRef,
    val displayName: String,
    val priority: Int,
    val activeToggle: Boolean = true,
    val activeWindows: List<DateRange> = emptyList(),
    val activeHours: List<HourRange> = emptyList(),
    val tzId: ZoneId = ZoneId.systemDefault(),
    val kind: CalendarKind = CalendarKind.Regular,
    val supersedes: List<CalendarRef> = emptyList(),
    /**
     * Baseline cadence in days for off-schedule detection (RV-P).
     * `null` disables the check. When set, any event whose distance
     * from its calendar's nearest recurrence is greater than this is
     * tagged `offSchedule`.
     */
    val baselineCadenceDays: Int? = null,
    val colorSeed: Int? = null,
    /**
     * Round 2.18.A.11 — `CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL`
     * for external calendars (one of `CAL_ACCESS_*` constants). `null` for
     * non-external calendars. Drives the edit-affordance gating in the UI:
     * the detail sheet hides edit / delete actions when this is below
     * `CAL_ACCESS_CONTRIBUTOR` (500).
     */
    val externalAccessLevel: Int? = null,
)

/**
 * Round 2.18.A.7 — sidecar source-of-origin tag for events that came in
 * via [com.eight87.strictlykeptboy.system.SystemEventsBridge] from
 * Android's `CalendarContract`. Carries the writeback-relevant tuple so
 * Phase D can route an edit back to the right account.
 *
 * Defended: resolver code never reads this; only the UI badge layer +
 * the (future) writeback path consult it. Existing file-backed events
 * leave it `null` — those go through `GitRepo.commitAll`.
 */
data class ExternalSource(
    val accountType: String,
    val accountName: String,
    /** `CalendarContract.Events._ID`. */
    val eventId: Long,
    /** One of `CalendarContract.Calendars.CAL_ACCESS_*`. */
    val accessLevel: Int,
    /** Owner email/account, may be `null` for some sync adapters. */
    val ownerAccount: String? = null,
)

data class TodolistMeta(
    val ref: TodolistRef,
    val repo: RepoRef,
    val displayName: String,
    val activeToggle: Boolean = true,
    val activeWindows: List<DateRange> = emptyList(),
    val activeHours: List<HourRange> = emptyList(),
    val tzId: ZoneId = ZoneId.systemDefault(),
)

/**
 * Frozen view of all repos at one point in time. The resolver memoizes
 * on `contentHash`, which is a stable hash of `(repoId, lastIndexedHeadSha)`
 * tuples — when any repo's HEAD changes, the cache key changes.
 */
data class RepoSnapshot(
    val repos: List<RepoEntry>,
    val calendars: List<CalendarMeta>,
    val todolists: List<TodolistMeta>,
) {
    data class RepoEntry(val ref: RepoRef, val lastIndexedHeadSha: String?)

    val contentHash: String by lazy {
        val canon = repos
            .sortedBy { it.ref.id }
            .joinToString("\n") { "${it.ref.id}=${it.lastIndexedHeadSha ?: ""}" }
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(canon.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

// -----------------------------------------------------------------------------
// Source-side input shapes (caller-provided; resolver does not load these).
// -----------------------------------------------------------------------------

/** Per-event single-instance input. Times are zoned. */
data class EventInput(
    val ref: EventRef,
    val calendar: CalendarRef,
    val repo: RepoRef,
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val isAllDay: Boolean = false,
    val emoji: String? = null,
    val body: String = "",
    val tags: List<String> = emptyList(),
    val priorityOverride: Int? = null,
    val isPrivate: Boolean = false,
    val isBusy: Boolean = true,
    val location: String? = null,
    val externalUid: String? = null,
    val author: PersonRef? = null,
    /**
     * Round 2.18.A.7 — non-null when this event came in via
     * [com.eight87.strictlykeptboy.system.SystemEventsBridge]. The
     * resolver ignores this field; only the writeback + UI-badge layers
     * read it.
     */
    val external: ExternalSource? = null,
)

/**
 * A materialized recurrence-rule input.
 *
 * Recurrence is expanded against [range] by [RecurrenceMaterializer].
 * `duration` is parsed by `java.time.Duration.parse` (ISO-8601, e.g. PT1H30M).
 */
data class RecurrenceInput(
    val rule: RuleRef,
    val calendar: CalendarRef,
    val repo: RepoRef,
    val title: String,
    val dtstart: ZonedDateTime,
    val duration: java.time.Duration,
    val rrule: String,
    val tzId: ZoneId,
    val rdates: List<ZonedDateTime> = emptyList(),
    val exdates: List<ZonedDateTime> = emptyList(),
    val active: Boolean = true,
    val emoji: String? = null,
    val body: String = "",
    val tags: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val isBusy: Boolean = true,
    val author: PersonRef? = null,
)

/**
 * Exception applied to a recurrence instance.
 *
 * `mode = "cancel"` removes the instance.
 * `mode = "move" | "override"` shifts/replaces the per-key frontmatter.
 * `mode = "note"` appends to the rendered body only (file body unchanged
 * by the resolver — RV-B.4).
 */
data class ExceptionInput(
    val ruleId: RuleRef,
    val instanceDate: LocalDate,
    val mode: String,
    val overrideStart: ZonedDateTime? = null,
    val overrideEnd: ZonedDateTime? = null,
    val overrideTitle: String? = null,
    val overrideLocation: String? = null,
    val noteBody: String? = null,
)

/** Post-hoc reality report attached to a scheduled instance (per RV-N / AT-B). */
data class DeviationInput(
    val targetId: String,
    val instanceDate: LocalDate,
    /** `skipped` | `partial` | `completed-early` | `completed-late` */
    val kind: String,
    val at: ZonedDateTime,
    val note: String? = null,
)

/** Force-show opt-out for supersedence (RV-O / HV-E). */
data class OverrideInput(
    val supersededCalendar: CalendarRef,
    val eventId: String,
    val instanceDate: LocalDate,
    /** `force-show` | `force-show-for-range` */
    val kind: String,
    val rangeFrom: LocalDate? = null,
    val rangeTo: LocalDate? = null,
)

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
    CompletedBySchedule,// past, no deviation (inverted default per AT)
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

sealed interface ViewMode {
    data object Day : ViewMode
    data object Week : ViewMode
    data object Month : ViewMode
    data object Agenda : ViewMode
    data class Todolist(val todolistRef: TodolistRef) : ViewMode
}
