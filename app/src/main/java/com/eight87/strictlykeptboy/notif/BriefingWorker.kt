package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Phase 2.1.F.6 — daily briefing worker. Posts the morning summary at
 * 07:00 and evening summary at 21:00. Body construction is delegated to
 * [BriefingComposer]; this class owns Android wiring only.
 *
 * The worker takes a `slot` input (`morning` or `evening`); per-slot
 * unique periodic work is registered via [scheduleAll]. The master
 * `NotificationPrefs.isBriefingsEnabled()` toggle short-circuits the
 * post — flipping it off in Settings now actually mutes the briefing.
 *
 * SOLID-S: Android wiring + master-toggle check. Composition is
 * elsewhere; instance materialization is the caller's concern (today
 * the worker posts an "empty-state" friendly placeholder when no
 * materializer is wired — the cal-briefings seed F.7 supplies a
 * recurring template event so the body still says something useful).
 */
class BriefingWorker(
    ctx: Context,
    params: WorkerParameters,
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val slot = when (inputData.getString(EXTRA_SLOT)) {
            SLOT_EVENING -> BriefingComposer.Slot.Evening
            else -> BriefingComposer.Slot.Morning
        }
        val ctx = applicationContext
        val prefs = NotificationPrefs.open(ctx)
        if (!prefs.isBriefingsEnabled()) return Result.success()
        if (!prefs.isChannelEnabled(NotificationChannels.BRIEFINGS)) return Result.success()

        // For v1 the worker posts a salutation-driven briefing without an
        // instance list. The composer + WorkManager + master-toggle path
        // is the deliverable here; materialization handoff is wired in a
        // follow-up once the AppGraph snapshot exposes a worker-safe API.
        val briefing = BriefingComposer.compose(
            slot = slot,
            instances = emptyList(),
            identity = null,
        )

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(briefing.title)
            .setSummaryText(briefing.salutation)
        briefing.lines.forEach { style.addLine(it) }

        val notif = NotificationCompat.Builder(ctx, NotificationChannels.BRIEFINGS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(briefing.title)
            .setContentText(briefing.salutation)
            .setStyle(style)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService<NotificationManager>()?.notify(
            briefing.title.hashCode(),
            notif,
        )
        return Result.success()
    }

    companion object {
        const val EXTRA_SLOT = "slot"
        const val SLOT_MORNING = "morning"
        const val SLOT_EVENING = "evening"

        const val UNIQUE_MORNING = "skb-briefing-morning"
        const val UNIQUE_EVENING = "skb-briefing-evening"

        /**
         * Idempotently register the two periodic workers. The work runs
         * once a day; AlarmManager-style 07:00 / 21:00 targeting is
         * approximated via initialDelay (WorkManager doesn't pin to the
         * minute, but the briefings channel is LOW so a few-minute drift
         * is fine).
         */
        fun scheduleAll(context: Context) {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniquePeriodicWork(
                UNIQUE_MORNING,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BriefingWorker>(24L, TimeUnit.HOURS)
                    .setInputData(androidx.work.Data.Builder().putString(EXTRA_SLOT, SLOT_MORNING).build())
                    .setInitialDelay(initialDelayMsForHour(7), TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().build())
                    .build(),
            )
            wm.enqueueUniquePeriodicWork(
                UNIQUE_EVENING,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BriefingWorker>(24L, TimeUnit.HOURS)
                    .setInputData(androidx.work.Data.Builder().putString(EXTRA_SLOT, SLOT_EVENING).build())
                    .setInitialDelay(initialDelayMsForHour(21), TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().build())
                    .build(),
            )
        }

        internal fun initialDelayMsForHour(hour: Int, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Long {
            var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            if (!target.isAfter(now)) target = target.plusDays(1L)
            return java.time.Duration.between(now, target).toMillis().coerceAtLeast(0L)
        }
    }
}
