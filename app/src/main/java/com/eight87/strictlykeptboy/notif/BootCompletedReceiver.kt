package com.eight87.strictlykeptboy.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Phase 2.1.F.8 — re-arm reminders after device reboot / app replace.
 *
 * Android drops every `AlarmManager` alarm on reboot; the permission
 * `RECEIVE_BOOT_COMPLETED` has been declared in the manifest since
 * Phase M but no receiver existed to consume the broadcast. This
 * receiver schedules an immediate one-shot [AlarmHorizonExtenderWorker]
 * to re-arm every reminder firing in the next horizon window, and
 * registers the nightly periodic worker that slides the 7-day horizon
 * forward.
 *
 * SOLID-S: this class only kicks the workers — the actual scanning
 * lives in [AlarmHorizonExtenderWorker] so it can be tested in
 * isolation and re-used by the nightly job.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val test = kickForTest
                if (test != null) test(context) else kickRearm(context)
            }
        }
    }

    companion object {
        /** Test seam — when non-null, overrides the WorkManager dispatch. */
        @Volatile var kickForTest: ((Context) -> Unit)? = null

        /** Public so the nightly worker / test seam can call the same path. */
        fun kickRearm(context: Context) {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniqueWork(
                UNIQUE_BOOT_REARM,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AlarmHorizonExtenderWorker>().build(),
            )
            // Slide the horizon nightly. Idempotent — KEEP policy avoids
            // stomping a pending run.
            wm.enqueueUniquePeriodicWork(
                UNIQUE_HORIZON_SLIDE,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AlarmHorizonExtenderWorker>(
                    24L, TimeUnit.HOURS,
                ).build(),
            )
        }

        const val UNIQUE_BOOT_REARM = "skb-reminder-boot-rearm"
        const val UNIQUE_HORIZON_SLIDE = "skb-reminder-horizon-slide"
    }
}
