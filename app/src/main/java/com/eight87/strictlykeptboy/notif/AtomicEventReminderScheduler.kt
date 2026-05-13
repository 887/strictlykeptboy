package com.eight87.strictlykeptboy.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Phase XX.3 / AT-C.2 + AT-C.3 — schedule start + end alarms for an
 * atomic-activity event.
 *
 * Parallel to [EventReminderScheduler] (which schedules lead-time
 * reminders for ordinary events). The atomic scheduler differs in two
 * material ways:
 *
 *  1. **Two alarms per event** — one at `start`, one at `end`. The
 *     end-alarm is the inversion's safety net: it flips the Room cache
 *     state `in-progress → completed-by-schedule` without requiring the
 *     app to be in foreground (AT-C.3).
 *  2. **Atomic channel** — fires on [NotificationChannels.EVENTS_ATOMIC],
 *     not the generic events channel.
 *
 * SOLID-I: takes a narrow [AtomicReminder] input shape; never reads
 * Event / Room rows directly. Callers (XX.8 routine materializer, XX.9
 * sub-beat scheduler, indexer) build the input.
 *
 * SOLID-S: this class only schedules; the receiver
 * ([AtomicEventReceiver]) only posts + handles actions; the writer
 * ([DeviationActionWriter]) only writes deviation files; the
 * end-alarm Room flip lives in
 * [AtomicEventReceiver.handleEndAlarm].
 */
class AtomicEventReminderScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager? = context.getSystemService(),
) {

    /**
     * Compact view of an atomic event for the scheduler. Title is the
     * user-authored verbatim string per AT-C.2 ("no editorial wrapping").
     */
    data class AtomicReminder(
        val repoId: String,
        val eventId: String,
        val calendarId: String,
        val title: String,
        /** Event start, ISO-8601 offset date-time. */
        val startIso: String,
        /** Event end, ISO-8601 offset date-time. */
        val endIso: String,
        /** K-2 — private events show generic copy on lockscreen. */
        val privateEvent: Boolean = false,
    )

    /**
     * Schedule the start + end alarms for [reminder]. Idempotent on
     * `(repoId, eventId)` — re-scheduling replaces both alarms.
     *
     * @return scheduled alarm ids (start + end, in that order) or empty
     *   if the alarm-manager is unavailable / times unparseable / both
     *   alarms in the past.
     */
    fun schedule(reminder: AtomicReminder, now: Instant = Instant.now()): List<String> {
        val am = alarmManager ?: return emptyList()
        val startInstant = parseIso(reminder.startIso) ?: return emptyList()
        val endInstant = parseIso(reminder.endIso) ?: return emptyList()
        val scheduled = mutableListOf<String>()

        if (!startInstant.isBefore(now)) {
            val startId = atomicAlarmId(reminder.repoId, reminder.eventId, KIND_START)
            val pi = pendingIntent(startId, reminder, KIND_START)
            setExact(am, startInstant, pi)
            scheduled += startId
        }
        if (!endInstant.isBefore(now)) {
            val endId = atomicAlarmId(reminder.repoId, reminder.eventId, KIND_END)
            val pi = pendingIntent(endId, reminder, KIND_END)
            setExact(am, endInstant, pi)
            scheduled += endId
        }
        return scheduled
    }

    /** Cancel both alarms for the given event. */
    fun cancel(repoId: String, eventId: String) {
        val am = alarmManager ?: return
        for (kind in listOf(KIND_START, KIND_END)) {
            val id = atomicAlarmId(repoId, eventId, kind)
            am.cancel(
                pendingIntent(
                    id,
                    AtomicReminder(repoId, eventId, "", "", "", "", false),
                    kind,
                ),
            )
        }
    }

    /** Schedule a snooze (re-arm of the start alarm at [fireAtMs]). */
    fun snoozeAt(reminder: AtomicReminder, fireAtMs: Long) {
        val am = alarmManager ?: return
        val id = atomicAlarmId(reminder.repoId, reminder.eventId, "snooze-$fireAtMs")
        val pi = pendingIntent(id, reminder, "snooze-$fireAtMs")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
        }
    }

    private fun setExact(am: AlarmManager, at: Instant, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
    }

    private fun pendingIntent(alarmId: String, r: AtomicReminder, kind: String): PendingIntent {
        val intent = Intent(context, AtomicEventReceiver::class.java).apply {
            action = AtomicEventReceiver.ACTION_FIRE
            data = Uri.parse("skb://atomic/$alarmId")
            putExtra(AtomicEventReceiver.EXTRA_REPO_ID, r.repoId)
            putExtra(AtomicEventReceiver.EXTRA_EVENT_ID, r.eventId)
            putExtra(AtomicEventReceiver.EXTRA_CALENDAR_ID, r.calendarId)
            putExtra(AtomicEventReceiver.EXTRA_TITLE, r.title)
            putExtra(AtomicEventReceiver.EXTRA_START_ISO, r.startIso)
            putExtra(AtomicEventReceiver.EXTRA_END_ISO, r.endIso)
            putExtra(AtomicEventReceiver.EXTRA_PRIVATE, r.privateEvent)
            putExtra(AtomicEventReceiver.EXTRA_ALARM_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun parseIso(iso: String): Instant? = try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    companion object {
        const val KIND_START = "start"
        const val KIND_END = "end"

        /** Stable alarm-id format: `<repoId>:<eventId>:atomic:<kind>`. */
        fun atomicAlarmId(repoId: String, eventId: String, kind: String): String =
            "$repoId:$eventId:atomic:$kind"
    }
}
