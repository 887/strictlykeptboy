package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.ExternalSource
import com.eight87.strictlykeptboy.system.ExternalReminder

/**
 * Round 2.18.F.2 — translate `CalendarContract.Reminders` rows into
 * skb's existing lead-time-token vocabulary so the
 * [EventReminderScheduler] can arm them through the unchanged
 * pre-event `AlarmManager` path.
 *
 * **In-memory only.** External reminders are never persisted to disk —
 * `CalendarContract` is the source of truth; the diff-and-reschedule
 * loop in [ExternalReminderScheduler] re-reads them on every observer
 * tick.
 *
 * Method filtering: `METHOD_EMAIL` / `METHOD_SMS` reminders are
 * upstream-app responsibilities (Gmail / the sync adapter fires those).
 * skb only mirrors `METHOD_ALERT` / `METHOD_ALARM` / `METHOD_DEFAULT`
 * — the on-device notifying methods. Filtering happens here so the
 * scheduler stays kind-agnostic.
 */
object ExternalReminderMapping {

    /**
     * Repo-id namespace for external events. Stable enough to use as the
     * cancel key — every alarm scheduled for a CalendarContract event
     * goes under `external/<accountType>/<accountName>:<eventId>`.
     */
    fun externalRepoId(source: ExternalSource): String =
        "external/${source.accountType}/${source.accountName}"

    /** Stable per-instance event id for cancel-by-id. */
    fun externalEventKey(source: ExternalSource): String = source.eventId.toString()

    /** Returns lead-time tokens (e.g. `"15m"`, `"1h"`, `"1d"`). */
    fun leadTimesFor(reminders: List<ExternalReminder>): List<String> =
        reminders
            .filter { isOnDeviceMethod(it.method) }
            .map { minutesToToken(it.minutes) }
            .distinct()

    /**
     * Build a [EventReminderScheduler.ReminderInput] for an external
     * event + its CalendarContract reminders. Returns `null` when there
     * are no on-device reminders to schedule.
     */
    fun toReminderInput(
        event: EventInput,
        reminders: List<ExternalReminder>,
    ): EventReminderScheduler.ReminderInput? {
        val source = event.external ?: return null
        val leads = leadTimesFor(reminders)
        if (leads.isEmpty()) return null
        return EventReminderScheduler.ReminderInput(
            repoId = externalRepoId(source),
            eventId = externalEventKey(source),
            calendarId = event.calendar.id,
            title = event.title,
            startIso = event.start.toOffsetDateTime().toString(),
            leadTimes = leads,
            privateEvent = event.isPrivate,
        )
    }

    private fun minutesToToken(minutes: Int): String {
        val m = if (minutes < 0) 0 else minutes
        if (m == 0) return "0"
        return when {
            m % (60 * 24) == 0 -> "${m / (60 * 24)}d"
            m % 60 == 0 -> "${m / 60}h"
            else -> "${m}m"
        }
    }

    private fun isOnDeviceMethod(method: Int): Boolean {
        // METHOD_DEFAULT(0), METHOD_ALERT(1), METHOD_ALARM(4) are device-side.
        // METHOD_EMAIL(2) and METHOD_SMS(3) are upstream-fired; we skip them.
        return method == 0 || method == 1 || method == 4
    }
}
