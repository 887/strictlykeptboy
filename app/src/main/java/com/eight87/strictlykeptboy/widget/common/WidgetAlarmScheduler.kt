package com.eight87.strictlykeptboy.widget.common

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Phase VV.5 / EEE.5 — minute-tick alarm scheduler.
 *
 * Inexact repeating alarm wired to the provider's
 * `ACTION_APPWIDGET_UPDATE` broadcast. Precise event-boundary refreshes
 * are driven separately by EventReminderScheduler (Phase M / NS-C).
 */
class WidgetAlarmScheduler(private val context: Context) {

    fun schedule(provider: Class<*>) {
        val am = alarmManager() ?: return
        val pi = pendingIntent(provider) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            INTERVAL_MS,
            pi,
        )
    }

    fun cancel(provider: Class<*>) {
        val am = alarmManager() ?: return
        val pi = pendingIntent(provider) ?: return
        am.cancel(pi)
    }

    private fun alarmManager(): AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun pendingIntent(provider: Class<*>): PendingIntent? {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = ComponentName(context, provider)
        }
        return PendingIntent.getBroadcast(
            context,
            provider.name.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val INTERVAL_MS: Long = 60_000L
    }
}
