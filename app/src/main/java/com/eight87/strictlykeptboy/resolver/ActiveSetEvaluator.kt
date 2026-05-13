package com.eight87.strictlykeptboy.resolver

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Phase E.1 / RV-A — active-set evaluator.
 *
 * Determines which calendars / todolists are "in effect" at a given
 * instant across every configured repo. Pure predicate evaluator;
 * supersedence and override layers piggy-back here so the renderer
 * downstream gets a single coherent set.
 */
class ActiveSetEvaluator {

    /**
     * Returns the calendar IDs that are active at [at], with supersedence
     * applied: a calendar that another *active* calendar `supersedes` is
     * excluded UNLESS the [overrides] list contains a `force-show` override
     * for an event on that calendar covering [at]'s date (RV-O / HV-E).
     */
    fun activeCalendarsAt(
        at: ZonedDateTime,
        snapshot: RepoSnapshot,
        overrides: List<OverrideInput> = emptyList(),
    ): Set<CalendarRef> {
        val activeBeforeSupersedence = snapshot.calendars
            .filter { isActive(it, at) }
        val active = activeBeforeSupersedence.map { it.ref }.toSet()

        // Build supersedence map: targetCal -> set of supersedingCals that are active.
        val suppressedBy = mutableMapOf<CalendarRef, MutableSet<CalendarRef>>()
        activeBeforeSupersedence.forEach { c ->
            c.supersedes.forEach { target ->
                if (target in active) {
                    suppressedBy.getOrPut(target) { mutableSetOf() } += c.ref
                }
            }
        }

        val date = at.toLocalDate()
        val forcedShownCalendars = overrides
            .filter { ov ->
                when (ov.kind) {
                    "force-show" -> ov.instanceDate == date
                    "force-show-for-range" ->
                        (ov.rangeFrom == null || !date.isBefore(ov.rangeFrom)) &&
                            (ov.rangeTo == null || !date.isAfter(ov.rangeTo))
                    else -> false
                }
            }
            .map { it.supersededCalendar }
            .toSet()

        return active.filter { ref ->
            ref !in suppressedBy.keys || ref in forcedShownCalendars
        }.toSet()
    }

    /**
     * Round 2.1.C.4 — like [activeCalendarsAt] but does NOT drop superseded
     * calendars. The renderer uses this so it can render superseded bands
     * with a paused/strikethrough treatment instead of hiding them.
     * Override-driven re-inclusion is moot here (everything is already in).
     */
    fun activeCalendarsAtIncludingSuperseded(
        at: ZonedDateTime,
        snapshot: RepoSnapshot,
    ): Set<CalendarRef> =
        snapshot.calendars.filter { isActive(it, at) }.map { it.ref }.toSet()

    /** As [activeCalendarsAt], but for todolists. Todolists do not supersede. */
    fun activeTodolistsAt(at: ZonedDateTime, snapshot: RepoSnapshot): Set<TodolistRef> =
        snapshot.todolists.filter { isActive(it, at) }.map { it.ref }.toSet()

    /**
     * Pure predicate: is [meta] active at [at]?
     *
     * Empty `activeWindows` and `activeHours` mean "always" (RV-A).
     * DST fall-back overlap is handled implicitly: callers convert their
     * `Instant` to a `ZonedDateTime` in the calendar's tz before invoking,
     * and `ZonedDateTime` already represents one or the other offset; the
     * resolver does not attempt to match "both offsets" simultaneously
     * since the caller's clock has already disambiguated. v1 TODO: when
     * RV-H lands, expose a `bothOffsetsActive` helper for queries that
     * span the overlap window.
     */
    fun isActive(meta: CalendarMeta, at: ZonedDateTime): Boolean = activeAtCommon(
        toggle = meta.activeToggle,
        windows = meta.activeWindows,
        hours = meta.activeHours,
        tzId = meta.tzId,
        at = at,
    )

    fun isActive(meta: TodolistMeta, at: ZonedDateTime): Boolean = activeAtCommon(
        toggle = meta.activeToggle,
        windows = meta.activeWindows,
        hours = meta.activeHours,
        tzId = meta.tzId,
        at = at,
    )

    private fun activeAtCommon(
        toggle: Boolean,
        windows: List<DateRange>,
        hours: List<HourRange>,
        tzId: java.time.ZoneId,
        at: ZonedDateTime,
    ): Boolean {
        if (!toggle) return false
        val local = at.withZoneSameInstant(tzId)
        val date = local.toLocalDate()
        val time = local.toLocalTime()
        val dow = local.dayOfWeek

        val inWindow = windows.isEmpty() || windows.any { it.contains(date) }
        if (!inWindow) return false

        return hours.isEmpty() || hours.any { matchesHour(it, dow, time) }
    }

    internal fun matchesHour(h: HourRange, dow: DayOfWeek, t: LocalTime): Boolean = when {
        h.to == h.from -> false
        h.to.isAfter(h.from) ->
            h.day == dow && !t.isBefore(h.from) && t.isBefore(h.to)
        else -> {
            // midnight rollover: covers `[from, 24:00)` on `day` ∪ `[00:00, to)` on `day+1`.
            (h.day == dow && !t.isBefore(h.from)) ||
                (h.day == dow.minus(1) && t.isBefore(h.to))
        }
    }
}
