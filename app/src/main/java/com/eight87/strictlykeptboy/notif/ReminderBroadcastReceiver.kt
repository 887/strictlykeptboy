package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R

/**
 * Phase M.2 + M.6 — receives AlarmManager fire intents, posts a
 * notification, handles snooze / deviation / open actions.
 *
 * The receiver is intentionally stateless beyond `goAsync()` — heavy
 * work (deviation file writes) is delegated to [DeviationActionWriter]
 * which runs on Dispatchers.IO.
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postNotification(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
            ACTION_DEVIATION -> handleDeviation(context, intent)
        }
    }

    private fun postNotification(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val priv = intent.getBooleanExtra(EXTRA_PRIVATE, false)

        // Respect channel-level enable toggle.
        val prefs = NotificationPrefs.open(context)
        if (!prefs.isChannelEnabled(NotificationChannels.EVENTS)) return

        val displayTitle = if (priv) context.getString(R.string.notif_event_private_title) else title
        val publicVersion = NotificationCompat.Builder(context, NotificationChannels.EVENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_event_private_title))
            .build()

        val openPi = PendingIntent.getActivity(
            context, eventId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snoozePi = { minutes: Int ->
            PendingIntent.getBroadcast(
                context, (eventId + minutes).hashCode(),
                Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra(EXTRA_REPO_ID, repoId)
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_SNOOZE_MINUTES, minutes)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val deviationPi = { kind: String ->
            PendingIntent.getBroadcast(
                context, (eventId + kind).hashCode(),
                Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    action = ACTION_DEVIATION
                    putExtra(EXTRA_REPO_ID, repoId)
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_DEVIATION_KIND, kind)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notif = NotificationCompat.Builder(context, NotificationChannels.EVENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(context.getString(R.string.notif_event_role_pre))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .apply { if (priv) setPublicVersion(publicVersion) }
            .addAction(0, context.getString(R.string.notif_action_open), openPi)
            .addAction(0, context.getString(R.string.notif_action_snooze_10), snoozePi(10))
            .addAction(0, context.getString(R.string.notif_action_snooze_30), snoozePi(30))
            .addAction(0, context.getString(R.string.notif_action_snooze_60), snoozePi(60))
            .addAction(0, context.getString(R.string.notif_action_i_didnt), deviationPi(DEVIATION_SKIPPED))
            .addAction(0, context.getString(R.string.notif_action_partial), deviationPi(DEVIATION_PARTIAL))
            .build()

        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(eventId.hashCode(), notif)
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        val fireAt = System.currentTimeMillis() + minutes * 60_000L

        EventReminderScheduler(context).snoozeAt(repoId, eventId, title, fireAt)
        // Dismiss the current notification.
        context.getSystemService<NotificationManager>()?.cancel(eventId.hashCode())
    }

    private fun handleDeviation(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_DEVIATION_KIND) ?: DEVIATION_SKIPPED

        val pending = goAsync()
        DeviationActionWriter.writeAsync(context, repoId, eventId, kind) {
            // dismiss + finalize
            context.getSystemService<NotificationManager>()?.cancel(eventId.hashCode())
            pending.finish()
        }
    }

    companion object {
        const val ACTION_FIRE = "com.eight87.strictlykeptboy.notif.FIRE"
        const val ACTION_SNOOZE = "com.eight87.strictlykeptboy.notif.SNOOZE"
        const val ACTION_DEVIATION = "com.eight87.strictlykeptboy.notif.DEVIATION"

        const val EXTRA_REPO_ID = "repoId"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_CALENDAR_ID = "calendarId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LEAD_TIME = "leadTime"
        const val EXTRA_PRIVATE = "private"
        const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
        const val EXTRA_DEVIATION_KIND = "deviationKind"

        const val DEVIATION_SKIPPED = "skipped"
        const val DEVIATION_PARTIAL = "partial"
    }
}
