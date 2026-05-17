package com.eight87.strictlykeptboy.resolver

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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
    /** Round 2.21.A.3 — see [com.eight87.strictlykeptboy.store.Event.group]. */
    val group: String? = null,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.requiresResponse]. */
    val requiresResponse: Boolean = false,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.promptKind]. */
    val promptKind: com.eight87.strictlykeptboy.store.PromptKind? = null,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.promptTarget]. */
    val promptTarget: com.eight87.strictlykeptboy.store.PromptTarget? = null,
    /**
     * Round 2.24 / D-2.24.a — per-event timezone pin. `null` ⇒ event is
     * unpinned and resolves in the repo-default (or system) zone at
     * render time. When set, the event's `start`/`end` are already
     * anchored to this zone by the caller, and the renderer will
     * convert to the display zone (if any) at the render lip.
     */
    val tzId: String? = null,
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
    /** Round 2.21.A.3 — see [com.eight87.strictlykeptboy.store.Event.group]. */
    val group: String? = null,
    /** Round 2.27 / D-2.27.a — recurring keeper-prompt; propagates to every materialized instance. */
    val requiresResponse: Boolean = false,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.promptKind]. */
    val promptKind: com.eight87.strictlykeptboy.store.PromptKind? = null,
    /** Round 2.27 / D-2.27.a — see [com.eight87.strictlykeptboy.store.Event.promptTarget]. */
    val promptTarget: com.eight87.strictlykeptboy.store.PromptTarget? = null,
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

sealed interface DeviationKind {
    val wireValue: String
    data object Skipped : DeviationKind { override val wireValue: String get() = "skipped" }
    data object Partial : DeviationKind { override val wireValue: String get() = "partial" }
    data object CompletedEarly : DeviationKind { override val wireValue: String get() = "completed-early" }
    data object CompletedLate : DeviationKind { override val wireValue: String get() = "completed-late" }
    companion object {
        fun fromWire(s: String): DeviationKind? = when (s) {
            "skipped" -> Skipped
            "partial" -> Partial
            "completed-early" -> CompletedEarly
            "completed-late" -> CompletedLate
            else -> null
        }
    }
}

data class DeviationInput(
    val targetId: String,
    val instanceDate: LocalDate,
    val kind: DeviationKind,
    val at: ZonedDateTime,
    val note: String? = null,
)

sealed interface OverrideKind {
    val wireValue: String
    data object ForceShow : OverrideKind { override val wireValue: String get() = "force-show" }
    data class ForceShowForRange(val from: LocalDate?, val to: LocalDate?) : OverrideKind {
        override val wireValue: String get() = "force-show-for-range"
    }
}

data class OverrideInput(
    val supersededCalendar: CalendarRef,
    val eventId: String,
    val instanceDate: LocalDate,
    val kind: OverrideKind,
)
