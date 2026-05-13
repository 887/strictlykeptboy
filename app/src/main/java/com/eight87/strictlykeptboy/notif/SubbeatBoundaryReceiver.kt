package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.eight87.strictlykeptboy.R

/**
 * Phase XX.9 / AT-I — receiver for sub-beat boundary alarms.
 *
 * Fires once per sub-beat boundary scheduled by
 * [SubbeatBoundaryScheduler]. Posts a low-priority single-vibration
 * notification on the [NotificationChannels.EVENTS_ATOMIC] channel
 * with title = sub-beat label, body = `<i>/<N>`, and a single
 * `Skip ahead` action which writes a `partial` deviation with
 * `subbeats_completed` = the labels of the sub-beats that already
 * fired before the user tapped skip.
 *
 * SOLID-S: post + dispatch action only. Deviation writes happen on
 * [DeviationActionWriter]'s IO dispatcher; this receiver never
 * touches files / databases on the broadcast thread.
 */
class SubbeatBoundaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postBoundary(context, intent)
            ACTION_SKIP_AHEAD -> handleSkipAhead(context, intent)
        }
    }

    private fun postBoundary(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val index = intent.getIntExtra(EXTRA_SUBBEAT_INDEX, 0)
        val total = intent.getIntExtra(EXTRA_SUBBEAT_TOTAL, 1)
        val label = intent.getStringExtra(EXTRA_SUBBEAT_LABEL) ?: ""
        val priv = intent.getBooleanExtra(EXTRA_PRIVATE, false)

        val prefs = NotificationPrefs.open(context)
        if (!prefs.isChannelEnabled(NotificationChannels.EVENTS_ATOMIC)) return

        // AT-I.2 LOCKED: title = sub-beat label, body = `i/N`. Private
        // events redact title to the K-2 placeholder.
        val displayTitle = if (priv) {
            context.getString(R.string.notif_event_private_title)
        } else {
            label
        }
        val body = context.getString(R.string.notif_subbeat_index_body, index + 1, total)

        val skipPi = PendingIntent.getBroadcast(
            context,
            (eventId + "subbeat-skip").hashCode(),
            Intent(context, SubbeatBoundaryReceiver::class.java).apply {
                action = ACTION_SKIP_AHEAD
                putExtra(EXTRA_REPO_ID, intent.getStringExtra(EXTRA_REPO_ID))
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_PRIOR_LABELS, intent.getStringArrayExtra(EXTRA_PRIOR_LABELS))
                // Mark this prior boundary as having fired by including
                // the current label in the partial deviation.
                putExtra(EXTRA_SUBBEAT_LABEL, label)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, NotificationChannels.EVENTS_ATOMIC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(body)
            // AT-I.2 LOCKED: low-priority, single vibration, no
            // full-screen banner. Channel is HIGH; per-notification
            // priority MIN keeps this from yanking the user out of
            // whatever they're doing while still buzzing once.
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_skip_ahead), skipPi)

        val nm = context.getSystemService<NotificationManager>() ?: return
        // Per-(event,index) id so each boundary stacks but cancelling
        // a prior boundary doesn't take down the current one.
        nm.notify((eventId + ":subbeat:" + index).hashCode(), builder.build())
    }

    private fun handleSkipAhead(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val prior = intent.getStringArrayExtra(EXTRA_PRIOR_LABELS)?.toMutableList() ?: mutableListOf()
        intent.getStringExtra(EXTRA_SUBBEAT_LABEL)?.let { prior.add(it) }
        val pending = goAsync()
        DeviationActionWriter.writeAsync(
            context = context,
            repoId = repoId,
            eventId = eventId,
            kind = ReminderBroadcastReceiver.DEVIATION_PARTIAL,
            subbeatsCompleted = prior,
        ) {
            // Cancel any pending sub-beat boundary notifications for
            // this event. (Best-effort: only addresses already-posted
            // boundaries; remaining alarms must be cancelled at the
            // call site that knows the full sub-beat list.)
            context.getSystemService<NotificationManager>()?.let { nm ->
                for (i in 0 until SubbeatBoundaryScheduler.MAX_SUBBEATS_PER_EVENT) {
                    nm.cancel((eventId + ":subbeat:" + i).hashCode())
                }
            }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_FIRE = "com.eight87.strictlykeptboy.notif.subbeat.FIRE"
        const val ACTION_SKIP_AHEAD = "com.eight87.strictlykeptboy.notif.subbeat.SKIP"

        const val EXTRA_REPO_ID = "repoId"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_CALENDAR_ID = "calendarId"
        const val EXTRA_EVENT_TITLE = "eventTitle"
        const val EXTRA_SUBBEAT_INDEX = "subbeatIndex"
        const val EXTRA_SUBBEAT_TOTAL = "subbeatTotal"
        const val EXTRA_SUBBEAT_LABEL = "subbeatLabel"
        const val EXTRA_PRIOR_LABELS = "priorLabels"
        const val EXTRA_PRIVATE = "private"
    }
}
