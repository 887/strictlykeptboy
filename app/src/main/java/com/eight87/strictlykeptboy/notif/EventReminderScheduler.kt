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
 * Phase M.2 — schedule AlarmManager alarms for the per-event
 * `notifications` array.
 *
 * R.X.1: narrow data interface — the scheduler takes a [ReminderInput]
 * list, never a full Event or CacheDatabase handle. Callers (the index
 * watcher, app start, RepoStore changes) map their concrete data to
 * [ReminderInput] before handing it over.
 */
class EventReminderScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager? = context.getSystemService(),
) {

    /**
     * Compact view of the data the scheduler actually consumes. Holds
     * a snapshot of the title/calendar/lead-times — the receiver fires
     * with these values directly per NS-C.3 ("snapshots at schedule time
     * so the receiver doesn't have to re-read the file store").
     */
    data class ReminderInput(
        val repoId: String,
        val eventId: String,
        val calendarId: String,
        val title: String,
        /** Event start time, ISO-8601 offset date-time. */
        val startIso: String,
        /** Lead-time strings ("15m", "1h", "1d") — parsed via [LeadTime]. */
        val leadTimes: List<String>,
        /** K-2 — private events show generic copy on lockscreen. */
        val privateEvent: Boolean = false,
    )

    /**
     * Schedule alarms for [reminders]. Idempotent: same `(repoId,
     * eventId, leadTime)` triple replaces its previous PendingIntent.
     *
     * @return list of scheduled alarm ids (one per lead-time per event).
     */
    fun scheduleAll(reminders: List<ReminderInput>, now: Instant = Instant.now()): List<String> {
        val am = alarmManager ?: return emptyList()
        val scheduled = mutableListOf<String>()
        for (r in reminders) {
            val startInstant = parseStart(r.startIso) ?: continue
            for (lt in r.leadTimes) {
                val duration = LeadTime.parse(lt) ?: continue
                val fireAt = startInstant.minusMillis(duration.inWholeMilliseconds)
                if (fireAt.isBefore(now)) continue // skip past-due
                val alarmId = ReminderAlarmId.format(r.repoId, r.eventId, lt)
                val pi = pendingIntentFor(alarmId, r, lt)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pi)
                }
                scheduled.add(alarmId)
            }
        }
        return scheduled
    }

    /** Cancel every alarm for a given `(repoId, eventId)`. */
    fun cancelFor(repoId: String, eventId: String, leadTimes: List<String>) {
        val am = alarmManager ?: return
        for (lt in leadTimes) {
            val alarmId = ReminderAlarmId.format(repoId, eventId, lt)
            am.cancel(pendingIntentFor(alarmId, repoId, eventId, lt))
        }
    }

    /** Re-arm a single alarm at [fireAtMs]. Used by snooze actions. */
    fun snoozeAt(repoId: String, eventId: String, title: String, fireAtMs: Long) {
        val am = alarmManager ?: return
        val lt = "snooze-${fireAtMs}"
        val alarmId = ReminderAlarmId.format(repoId, eventId, lt)
        val pi = pendingIntentFor(
            alarmId,
            ReminderInput(repoId, eventId, calendarId = "", title = title, startIso = "", leadTimes = emptyList()),
            lt,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
        }
    }

    private fun pendingIntentFor(
        alarmId: String,
        r: ReminderInput,
        leadTime: String,
    ): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            data = Uri.parse("skb://reminder/$alarmId")
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, r.repoId)
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, r.eventId)
            putExtra(ReminderBroadcastReceiver.EXTRA_CALENDAR_ID, r.calendarId)
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, r.title)
            putExtra(ReminderBroadcastReceiver.EXTRA_LEAD_TIME, leadTime)
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, r.privateEvent)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pendingIntentFor(
        alarmId: String,
        repoId: String,
        eventId: String,
        leadTime: String,
    ): PendingIntent = pendingIntentFor(
        alarmId,
        ReminderInput(repoId, eventId, calendarId = "", title = "", startIso = "", leadTimes = emptyList()),
        leadTime,
    )

    private fun parseStart(iso: String): Instant? = try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Stable id formula for an alarm — `<repoId>:<entityId>:<leadTime>`.
 * Used both as the Intent data Uri and (via hashCode) as the
 * PendingIntent request code, so re-scheduling replaces in place.
 */
object ReminderAlarmId {
    fun format(repoId: String, entityId: String, leadTime: String): String =
        "$repoId:$entityId:$leadTime"
}
