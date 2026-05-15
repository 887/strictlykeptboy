package com.eight87.strictlykeptboy.notif

import android.content.Context
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.system.ExternalReminder
import com.eight87.strictlykeptboy.system.SystemRemindersReader

/**
 * Round 2.18.F.3 / F.6 — diff-and-reschedule for external (CalendarContract)
 * events.
 *
 * Holds the last set of `(externalRepoId, externalEventKey, leadTime)`
 * triples it scheduled. On every refresh:
 *   - reads `CalendarContract.Reminders` for the supplied event ids
 *     (batched via [SystemRemindersReader.readForEvents]);
 *   - maps each `(EventInput, List<ExternalReminder>)` pair into a
 *     [EventReminderScheduler.ReminderInput];
 *   - schedules new alarms via [EventReminderScheduler.scheduleAll];
 *   - cancels alarms whose triples disappeared since the previous tick
 *     (event deleted, fell out of the window, lost its reminder rows).
 *
 * **In-memory only.** No persisted snapshot; the source is
 * CalendarContract and the observer in
 * [com.eight87.strictlykeptboy.system.SystemEventsBridge] is what drives
 * re-emission.
 *
 * Identity / register copy fall-back: the lead-time + repoId is enough
 * for [ReminderBroadcastReceiver] to look up the *active* repo's
 * identity via its existing seam — no change required here.
 *
 * SOLID-S: this class owns only the diff + dispatch. The reader, the
 * mapper, and the broad scheduler stay isolated.
 */
class ExternalReminderScheduler(
    private val context: Context,
    private val remindersReader: SystemRemindersReader = SystemRemindersReader(context),
    private val scheduler: EventReminderScheduler = EventReminderScheduler(context),
) {

    /** Last-known scheduled tag set per `(repoId, eventKey) → leadTimes`. */
    private val lastScheduled: MutableMap<EventKey, Set<String>> = mutableMapOf()

    /**
     * Refresh alarms for [externalEvents] (events whose `external` is
     * non-null). Returns the count of newly-armed alarms.
     */
    fun refresh(externalEvents: List<EventInput>): Refresh {
        val withSource = externalEvents.filter { it.external != null }
        val eventIds = withSource.mapNotNull { it.external?.eventId }
        val byEvent: Map<Long, List<ExternalReminder>> =
            remindersReader.readForEvents(eventIds)

        val current = mutableMapOf<EventKey, Set<String>>()
        val inputs = mutableListOf<EventReminderScheduler.ReminderInput>()
        for (ev in withSource) {
            val src = ev.external ?: continue
            val rem = byEvent[src.eventId].orEmpty()
            val input = ExternalReminderMapping.toReminderInput(ev, rem) ?: continue
            inputs += input
            current[EventKey(input.repoId, input.eventId)] = input.leadTimes.toSet()
        }

        // Cancel anything that's no longer present (event vanished, reminders
        // cleared, fell out of window).
        for ((key, oldLeads) in lastScheduled) {
            val nowLeads = current[key].orEmpty()
            val toCancel = oldLeads - nowLeads
            if (toCancel.isNotEmpty()) {
                scheduler.cancelFor(key.repoId, key.eventId, toCancel.toList())
            }
        }
        // Also cancel keys that disappeared entirely.
        for (key in lastScheduled.keys - current.keys) {
            scheduler.cancelFor(key.repoId, key.eventId, lastScheduled.getValue(key).toList())
        }

        val armed = scheduler.scheduleAll(inputs)
        lastScheduled.clear()
        lastScheduled.putAll(current)
        return Refresh(armedAlarmIds = armed, scheduledEventCount = current.size)
    }

    /** Cancel every alarm we currently know about. Used on teardown. */
    fun cancelAll() {
        for ((key, leads) in lastScheduled) {
            scheduler.cancelFor(key.repoId, key.eventId, leads.toList())
        }
        lastScheduled.clear()
    }

    /** Test seam — exposes the current tag set. */
    internal fun currentTags(): Map<EventKey, Set<String>> = lastScheduled.toMap()

    data class Refresh(
        val armedAlarmIds: List<String>,
        val scheduledEventCount: Int,
    )

    data class EventKey(val repoId: String, val eventId: String)
}
