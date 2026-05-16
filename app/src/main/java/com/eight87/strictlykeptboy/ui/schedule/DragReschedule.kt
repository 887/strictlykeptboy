package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.git.Uuid7
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Round 2.22 / Phase B / DD — pure helpers for the drag-to-reschedule
 * flow. **No Compose, no IO** — every function here is a pure
 * transformation so the gesture-wiring layer + the AlertDialog + the
 * commit handler can compose them without re-implementing the math.
 *
 * Decision references:
 * - D-2.22.b — drag semantics, snap, drop, recurrence prompt branches.
 * - Snap default: 15-minute grid.
 * - Commit-message format: `move event "<title>" from <old-iso> to <new-iso>`.
 *
 * What lives here:
 * - [snapToGrid] — round an instant to the nearest N-minute boundary.
 * - [moveSingleEvent] — duration-preserving new start/end on an [Event].
 * - [commitMessageFor] — canonical commit message string.
 * - [moveRecurringInstance] — produce an [Exception] (`mode = "move"`)
 *   for a single occurrence of a recurring rule.
 * - [rewriteRuleDtstart] — entire-series move: rewrite the rule's
 *   `dtstart` to the drop instant (preserves DURATION, RRULE, TZ).
 * - [splitRecurringRule] — "this and future" move: cap the existing
 *   rule with `UNTIL = day-before-drop` and produce a new rule rooted
 *   at the drop date.
 *
 * What deliberately does NOT live here:
 * - File IO / EntityWriter calls — caller wraps these results.
 * - Git commits — caller invokes `GitRepoRegistry.commitAll(...)`.
 * - Compose gesture wiring — see [ScheduleDayView] for the
 *   `detectDragGesturesAfterLongPress` integration.
 */
object DragReschedule {

    /** Default snap interval, per D-2.22.b. */
    const val DRAG_SNAP_MINUTES = 15

    /**
     * Round [instant] to the nearest [gridMinutes]-minute boundary.
     * Anchors on the start-of-day of [instant] (so a 15-minute grid
     * always lands on :00 / :15 / :30 / :45 of the local hour).
     */
    fun snapToGrid(instant: OffsetDateTime, gridMinutes: Int = DRAG_SNAP_MINUTES): OffsetDateTime {
        require(gridMinutes > 0) { "gridMinutes must be > 0, got $gridMinutes" }
        val startOfDay = instant.toLocalDate().atStartOfDay().atOffset(instant.offset)
        val minutesFromStart = ChronoUnit.MINUTES.between(startOfDay, instant)
        val snappedMinutes = ((minutesFromStart + gridMinutes / 2) / gridMinutes) * gridMinutes
        return startOfDay.plusMinutes(snappedMinutes)
    }

    /**
     * Build an updated [Event] whose `start` shifts to [newStart],
     * preserving original duration. Header is bumped (modifiedAt).
     */
    fun moveSingleEvent(event: Event, newStart: OffsetDateTime, nowIso: String): Event {
        val oldStart = OffsetDateTime.parse(event.start)
        val oldEnd = OffsetDateTime.parse(event.end)
        val duration = Duration.between(oldStart, oldEnd)
        val newEnd = newStart.plus(duration)
        return event.copy(
            start = newStart.toString(),
            end = newEnd.toString(),
            header = event.header.copy(updatedAt = nowIso),
        )
    }

    /**
     * Canonical commit message per D-2.22.b. Title is wrapped in
     * straight double-quotes; if the title contains a `"` we replace
     * it with `'` (no escaping in the commit subject — git logs
     * tolerate either fine; we pick the simpler one).
     */
    fun commitMessageFor(title: String, oldStart: String, newStart: String): String {
        val safeTitle = title.replace('"', '\'')
        return "move event \"$safeTitle\" from $oldStart to $newStart"
    }

    /**
     * "This instance only" — produce an [Exception] with `mode = "move"`
     * pinning [originalDate] of the recurring [rule] to a new start/end
     * pair anchored at [newStart] (duration preserved from the rule's
     * own `duration`).
     */
    fun moveRecurringInstance(
        rule: RecurrenceRule,
        originalDate: LocalDate,
        newStart: OffsetDateTime,
        author: String?,
        nowIso: String,
    ): Exception {
        val duration = Duration.parse(rule.duration)
        val newEnd = newStart.plus(duration)
        return Exception(
            header = EntityHeader(
                id = Uuid7.generate().toString(),
                schemaVersion = 1,
                createdAt = nowIso,
                updatedAt = nowIso,
                author = author ?: "",
            ),
            ruleId = rule.id,
            instanceDate = originalDate.toString(),
            mode = "move",
            calendarId = rule.calendarId,
            overrideStart = newStart.toString(),
            overrideEnd = newEnd.toString(),
        )
    }

    /**
     * "Entire series" — rewrite the rule's `dtstart` to [newStart].
     * Preserves DURATION, RRULE, TZ. Bumps `modifiedAt`.
     *
     * Note: `dtstart` on disk is a *local datetime* (no offset), so we
     * format with [LOCAL_DATETIME_FORMAT]. The rule's `tzId` already
     * carries the zone.
     */
    fun rewriteRuleDtstart(rule: RecurrenceRule, newStart: OffsetDateTime, nowIso: String): RecurrenceRule {
        val localDtstart = newStart.toLocalDateTime().format(LOCAL_DATETIME_FORMAT)
        return rule.copy(
            dtstart = localDtstart,
            header = rule.header.copy(updatedAt = nowIso),
        )
    }

    /**
     * "This and future" — return Pair(cappedOriginal, newRule):
     * - cappedOriginal: original rule with `RRULE` extended by
     *   `;UNTIL=<utc-of-day-before-drop>T235959Z`.
     * - newRule: a brand-new RecurrenceRule rooted at [newStart],
     *   sharing title / duration / tz / calendarId / rrule with the
     *   original (UNTIL stripped if present).
     */
    fun splitRecurringRule(
        rule: RecurrenceRule,
        newStart: OffsetDateTime,
        author: String?,
        nowIso: String,
    ): Pair<RecurrenceRule, RecurrenceRule> {
        val cutoff = newStart.toLocalDate().minusDays(1)
        // RRULE UNTIL must be UTC per RFC 5545.
        val untilUtc = cutoff.atTime(23, 59, 59).atOffset(ZoneOffset.UTC)
            .format(RRULE_UNTIL_FORMAT)
        val cappedRrule = appendOrReplaceUntil(rule.rrule, untilUtc)
        val capped = rule.copy(
            rrule = cappedRrule,
            header = rule.header.copy(updatedAt = nowIso),
        )
        val freshRrule = stripUntil(rule.rrule)
        val freshDtstart = newStart.toLocalDateTime().format(LOCAL_DATETIME_FORMAT)
        val fresh = RecurrenceRule(
            header = EntityHeader(
                id = Uuid7.generate().toString(),
                schemaVersion = 1,
                createdAt = nowIso,
                updatedAt = nowIso,
                author = author ?: "",
            ),
            title = rule.title,
            dtstart = freshDtstart,
            duration = rule.duration,
            tzId = rule.tzId,
            rrule = freshRrule,
            calendarId = rule.calendarId,
            rdate = emptyList(),
            exdate = emptyList(),
            location = rule.location,
            notifications = rule.notifications,
            tags = rule.tags,
            emoji = rule.emoji,
            busy = rule.busy,
            active = rule.active,
            inverted = rule.inverted,
            group = rule.group,
            body = rule.body,
        )
        return capped to fresh
    }

    private val LOCAL_DATETIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val RRULE_UNTIL_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    private fun appendOrReplaceUntil(rrule: String, untilValue: String): String {
        val parts = rrule.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val withoutUntil = parts.filterNot { it.startsWith("UNTIL=", ignoreCase = true) }
        return (withoutUntil + "UNTIL=$untilValue").joinToString(";")
    }

    private fun stripUntil(rrule: String): String {
        val parts = rrule.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.filterNot { it.startsWith("UNTIL=", ignoreCase = true) }.joinToString(";")
    }
}
