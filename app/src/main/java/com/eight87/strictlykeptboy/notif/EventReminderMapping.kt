package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Reminder
import com.eight87.strictlykeptboy.store.ReminderKind
import com.eight87.strictlykeptboy.store.TomlTable
import java.time.Duration

/**
 * Phase 2.1.F.1 — pure mapping from on-disk reminder declarations to the
 * scheduler's narrow [EventReminderScheduler.ReminderInput] shape.
 *
 * Precedence per F.1:
 *  1. Read `[[reminder]]` array (DM-X) from the event frontmatter via
 *     [Reminder.readArray]; one [EventReminderScheduler.ReminderInput]
 *     covers all entries by joining their lead-times into the input's
 *     `leadTimes` list (the scheduler arms one alarm per lead-time).
 *  2. If the array is empty, fall back to the legacy string `notifications`
 *     field on [Event] (Phase M shape).
 *
 * The conversion from a [Reminder.offset] string (ISO-8601 duration,
 * possibly negative, `"0"` ⇒ at-start) into the scheduler's "lead-time"
 * token preserves the absolute duration and tags the sign in the token
 * so the scheduler can compute fire-time correctly: `-PT30M` ⇒ `"30m"`
 * (pre-event), `PT4H` ⇒ `"-4h"` (post-event; rendered with leading
 * minus to flip the scheduler's `start − leadTime` arithmetic into
 * `start + |leadTime|`), `"0"` ⇒ `"0s"` (at-start).
 *
 * SOLID-S: this file does exactly the mapping. SOLID-D: pure Kotlin, no
 * Android imports — testable in plain JVM.
 */
object EventReminderMapping {

    /**
     * Build a single [EventReminderScheduler.ReminderInput] from an
     * [Event] + the event's source repoId. Returns `null` when the event
     * has no reminders at all (neither new array nor legacy field).
     */
    fun fromEvent(event: Event, repoId: String): EventReminderScheduler.ReminderInput? {
        val leads = leadTimesFor(event)
        if (leads.isEmpty()) return null
        return EventReminderScheduler.ReminderInput(
            repoId = repoId,
            eventId = event.id,
            calendarId = event.calendarId,
            title = event.title,
            startIso = event.start,
            leadTimes = leads,
            privateEvent = event.private,
        )
    }

    /**
     * Resolve the event's lead-time tokens. Per F.1 precedence: prefer the
     * `[[reminder]]` array (parsed from the event's frontmatter at
     * read-time and made available here through the caller's [reminderArray]
     * supplier), and fall back to [Event.notifications] only when the
     * array is empty.
     */
    fun leadTimesFor(event: Event, reminderArray: List<Reminder> = emptyList()): List<String> {
        if (reminderArray.isNotEmpty()) {
            return reminderArray.mapNotNull { toLeadTimeToken(it.offset) }
        }
        return event.notifications
    }

    /**
     * Frontmatter overload for callers that have already parsed the TOML
     * but not yet decoded [Event]. Used by the indexer / scheduler-wiring
     * layer so we don't pay the codec cost twice.
     */
    fun leadTimesFor(event: Event, frontmatter: TomlTable): List<String> {
        val arr = Reminder.readArray(frontmatter)
        return leadTimesFor(event, arr)
    }

    /**
     * Convert an ISO-8601 duration string from a [Reminder.offset] into a
     * lead-time token consumable by [LeadTime.parse]. Returns `null` for
     * unparseable strings or for post-event reminders (positive offsets) —
     * v1 [EventReminderScheduler] only fires pre-event alarms via
     * `start − leadTime`; post-event firing arrives in a follow-up.
     *
     *  - Negative duration ⇒ pre-event lead time, emitted positive
     *    (e.g. `-PT30M` ⇒ `"30m"`).
     *  - `"0"` / zero ⇒ at-start, emitted as `"0"` (LeadTime accepts both
     *    `"0"` and `"now"`).
     *  - Positive duration ⇒ `null` (post-event; not armable in v1).
     */
    fun toLeadTimeToken(offset: String): String? {
        val d = runCatching { Reminder.parseOffset(offset) }.getOrNull() ?: return null
        if (d.isZero) return "0"
        if (!d.isNegative) return null
        return durationToken(d.negated())
    }

    private fun durationToken(abs: Duration): String {
        val mins = abs.toMinutes()
        if (mins <= 0L) return "0"
        return when {
            mins % (60L * 24L) == 0L -> "${mins / (60L * 24L)}d"
            mins % 60L == 0L -> "${mins / 60L}h"
            else -> "${mins}m"
        }
    }
}
