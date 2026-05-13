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
 * Phase XX.9 / AT-I — schedule one alarm per sub-beat boundary inside
 * an atomic event.
 *
 * Boundary alarms fire at `event.start + sum(prefix-1)` for each
 * sub-beat (i.e. when sub-beat _i_ *starts*). Single vibration,
 * low-priority notification; title = current sub-beat label, body =
 * `<i>/<N>`, single `Skip ahead` action which writes a `partial`
 * deviation with `subbeats_completed` = labels of the sub-beats that
 * already started before the user tapped skip.
 *
 * Cap: 16 sub-beats per event (AT-I.3); excess sub-beats are silently
 * truncated so the scheduler can never blow past Android's per-app
 * alarm-count quota for one event.
 *
 * SOLID-S: this class only *schedules* boundary alarms. Posting +
 * action-handling lives in [SubbeatBoundaryReceiver]; deviation
 * write-out lives in [DeviationActionWriter]. SOLID-I: takes the
 * narrow [SubbeatSeries] tuple — does not depend on Event / Room
 * entities.
 */
class SubbeatBoundaryScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager? = context.getSystemService(),
) {

    /** Compact tuple per AT-I.1 / AT-I.2. */
    data class SubbeatSeries(
        val repoId: String,
        val eventId: String,
        val calendarId: String,
        val eventTitle: String,
        val startIso: String,
        /** Ordered labels + durations of this event's sub-beats. */
        val subbeats: List<Subbeat>,
        val privateEvent: Boolean = false,
    )

    data class Subbeat(val label: String, val durationSeconds: Int)

    /**
     * @return alarm ids actually scheduled (in fire order); empty list
     *   if alarm-manager unavailable, no sub-beats supplied, the start
     *   doesn't parse, or all sub-beats already in the past.
     */
    fun schedule(series: SubbeatSeries, now: Instant = Instant.now()): List<String> {
        val am = alarmManager ?: return emptyList()
        if (series.subbeats.isEmpty()) return emptyList()
        val start = parseIso(series.startIso) ?: return emptyList()
        val capped = series.subbeats.take(MAX_SUBBEATS_PER_EVENT)
        val total = capped.size

        val scheduled = mutableListOf<String>()
        var cursor = start
        capped.forEachIndexed { index, sb ->
            // Sub-beat _index_ STARTS at the running cursor. Schedule
            // the alarm there; advance cursor by this sub-beat's duration.
            if (!cursor.isBefore(now)) {
                val id = boundaryAlarmId(series.repoId, series.eventId, index)
                setExact(am, cursor, pendingIntent(id, series, index, total, sb))
                scheduled += id
            }
            cursor = cursor.plusSeconds(sb.durationSeconds.toLong())
        }
        return scheduled
    }

    /** Cancel every boundary alarm for the event. */
    fun cancel(series: SubbeatSeries) {
        val am = alarmManager ?: return
        series.subbeats.take(MAX_SUBBEATS_PER_EVENT).forEachIndexed { index, sb ->
            val id = boundaryAlarmId(series.repoId, series.eventId, index)
            am.cancel(pendingIntent(id, series, index, series.subbeats.size, sb))
        }
    }

    private fun setExact(am: AlarmManager, at: Instant, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
    }

    private fun pendingIntent(
        alarmId: String,
        series: SubbeatSeries,
        index: Int,
        total: Int,
        sb: Subbeat,
    ): PendingIntent {
        val intent = Intent(context, SubbeatBoundaryReceiver::class.java).apply {
            action = SubbeatBoundaryReceiver.ACTION_FIRE
            data = Uri.parse("skb://subbeat/$alarmId")
            putExtra(SubbeatBoundaryReceiver.EXTRA_REPO_ID, series.repoId)
            putExtra(SubbeatBoundaryReceiver.EXTRA_EVENT_ID, series.eventId)
            putExtra(SubbeatBoundaryReceiver.EXTRA_CALENDAR_ID, series.calendarId)
            putExtra(SubbeatBoundaryReceiver.EXTRA_EVENT_TITLE, series.eventTitle)
            putExtra(SubbeatBoundaryReceiver.EXTRA_SUBBEAT_INDEX, index)
            putExtra(SubbeatBoundaryReceiver.EXTRA_SUBBEAT_TOTAL, total)
            putExtra(SubbeatBoundaryReceiver.EXTRA_SUBBEAT_LABEL, sb.label)
            // Pass the labels of *prior* sub-beats so the `Skip ahead`
            // action knows what subbeats_completed array to write into
            // the partial deviation.
            val priorLabels = series.subbeats.take(index).map { it.label }.toTypedArray()
            putExtra(SubbeatBoundaryReceiver.EXTRA_PRIOR_LABELS, priorLabels)
            putExtra(SubbeatBoundaryReceiver.EXTRA_PRIVATE, series.privateEvent)
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
        /** AT-I.3 — cap per event. */
        const val MAX_SUBBEATS_PER_EVENT = 16

        /** Stable alarm-id format: `<repoId>:<eventId>:subbeat:<index>`. */
        fun boundaryAlarmId(repoId: String, eventId: String, index: Int): String =
            "$repoId:$eventId:subbeat:$index"
    }
}
