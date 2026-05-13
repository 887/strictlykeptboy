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
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Phase XX.3 / AT-C — receiver for atomic-event start/end alarms.
 *
 * Two alarm kinds fire here:
 *
 *  - [AtomicEventReminderScheduler.KIND_START] — posts the
 *    user-authored-title notification (title verbatim, body empty per
 *    AT-C.2) with actions `I did it` (no-op) / `I didn't` (skipped) /
 *    `Partial` / `Remind in 10/30/60 min`.
 *  - [AtomicEventReminderScheduler.KIND_END] — silently flips the Room
 *    cache `in-progress → completed-by-schedule` per AT-C.3 + AT-B.4(c).
 *    Does NOT post any notification (the inversion's whole point is
 *    that the schedule is the receipt).
 *
 * SOLID-S: this class posts + dispatches actions only. The Room flip is
 * delegated to [CompletionStateCacheWriter]; deviation writes to
 * [DeviationActionWriter]. The receiver itself never touches files or
 * databases on the main thread.
 */
class AtomicEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val kind = intent.getStringExtra(EXTRA_ALARM_KIND)
                    ?: AtomicEventReminderScheduler.KIND_START
                if (kind == AtomicEventReminderScheduler.KIND_END) {
                    handleEndAlarm(context, intent)
                } else {
                    postStartNotification(context, intent)
                }
            }
            ACTION_SNOOZE -> handleSnooze(context, intent)
            ACTION_DEVIATION -> handleDeviation(context, intent)
            ACTION_DID_IT -> handleDidIt(context, intent)
        }
    }

    private fun postStartNotification(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val priv = intent.getBooleanExtra(EXTRA_PRIVATE, false)

        // Respect channel-level enable toggle so the user can mute
        // atomic nudges without losing other events.
        val prefs = NotificationPrefs.open(context)
        if (!prefs.isChannelEnabled(NotificationChannels.EVENTS_ATOMIC)) return

        // AT-C.2 LOCKED: title is the user-authored event title verbatim
        // (or the private-event placeholder for `private = true`). Body
        // is empty by default — nothing the app generates can surface
        // non-SFW phrasing on a lockscreen preview (K-2).
        val displayTitle = if (priv) {
            context.getString(R.string.notif_event_private_title)
        } else {
            title
        }
        val publicVersion = NotificationCompat.Builder(context, NotificationChannels.EVENTS_ATOMIC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_event_private_title))
            .build()

        val openPi = PendingIntent.getActivity(
            context, eventId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val didItPi = actionPi(context, intent, ACTION_DID_IT, "did-it")
        val didntPi = deviationPi(context, intent, ReminderBroadcastReceiver.DEVIATION_SKIPPED)
        val partialPi = deviationPi(context, intent, ReminderBroadcastReceiver.DEVIATION_PARTIAL)
        val snoozePi: (Int) -> PendingIntent = { minutes ->
            PendingIntent.getBroadcast(
                context, (eventId + "snooze" + minutes).hashCode(),
                Intent(context, AtomicEventReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    copyExtrasFrom(intent)
                    putExtra(EXTRA_SNOOZE_MINUTES, minutes)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.EVENTS_ATOMIC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            // Body intentionally empty (AT-C.2 LOCKED — no editorial copy).
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .apply { if (priv) setPublicVersion(publicVersion) }
            .addAction(0, context.getString(R.string.notif_action_i_did_it), didItPi)
            .addAction(0, context.getString(R.string.notif_action_i_didnt), didntPi)
            .addAction(0, context.getString(R.string.notif_action_partial), partialPi)
            .addAction(0, context.getString(R.string.notif_action_snooze_10), snoozePi(10))
            .addAction(0, context.getString(R.string.notif_action_snooze_30), snoozePi(30))
            .addAction(0, context.getString(R.string.notif_action_snooze_60), snoozePi(60))

        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(eventId.hashCode(), builder.build())
    }

    /**
     * AT-C.3 — end-alarm. Silently flips the Room cache. No notification
     * posted; the schedule itself is the receipt.
     */
    private fun handleEndAlarm(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val startIso = intent.getStringExtra(EXTRA_START_ISO)
        val occurrenceDate = occurrenceDateFromStart(startIso)
        CompletionStateCacheWriter.flipToCompletedBySchedule(
            context,
            repoId = repoId,
            targetId = eventId,
            occurrenceDate = occurrenceDate,
        )
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val startIso = intent.getStringExtra(EXTRA_START_ISO) ?: ""
        val endIso = intent.getStringExtra(EXTRA_END_ISO) ?: ""
        val priv = intent.getBooleanExtra(EXTRA_PRIVATE, false)
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        val fireAt = System.currentTimeMillis() + minutes * 60_000L
        AtomicEventReminderScheduler(context).snoozeAt(
            AtomicEventReminderScheduler.AtomicReminder(
                repoId = repoId,
                eventId = eventId,
                calendarId = intent.getStringExtra(EXTRA_CALENDAR_ID) ?: "",
                title = title,
                startIso = startIso,
                endIso = endIso,
                privateEvent = priv,
            ),
            fireAt,
        )
        context.getSystemService<NotificationManager>()?.cancel(eventId.hashCode())
    }

    private fun handleDeviation(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_DEVIATION_KIND)
            ?: ReminderBroadcastReceiver.DEVIATION_SKIPPED
        val pending = goAsync()
        DeviationActionWriter.writeAsync(context, repoId, eventId, kind) {
            context.getSystemService<NotificationManager>()?.cancel(eventId.hashCode())
            pending.finish()
        }
    }

    /**
     * AT-C.4 LOCKED: `I did it` is a no-op. Inverted default already
     * wins; we do NOT write a `completed` deviation file (would balloon
     * the repo and contradicts AT-A.3).
     */
    private fun handleDidIt(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        context.getSystemService<NotificationManager>()?.cancel(eventId.hashCode())
    }

    private fun actionPi(
        context: Context,
        sourceIntent: Intent,
        action: String,
        discriminator: String,
    ): PendingIntent {
        val eventId = sourceIntent.getStringExtra(EXTRA_EVENT_ID) ?: ""
        return PendingIntent.getBroadcast(
            context,
            (eventId + discriminator).hashCode(),
            Intent(context, AtomicEventReceiver::class.java).apply {
                this.action = action
                copyExtrasFrom(sourceIntent)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun deviationPi(context: Context, sourceIntent: Intent, kind: String): PendingIntent {
        val eventId = sourceIntent.getStringExtra(EXTRA_EVENT_ID) ?: ""
        return PendingIntent.getBroadcast(
            context,
            (eventId + "dev" + kind).hashCode(),
            Intent(context, AtomicEventReceiver::class.java).apply {
                action = ACTION_DEVIATION
                copyExtrasFrom(sourceIntent)
                putExtra(EXTRA_DEVIATION_KIND, kind)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun Intent.copyExtrasFrom(src: Intent) {
        putExtra(EXTRA_REPO_ID, src.getStringExtra(EXTRA_REPO_ID))
        putExtra(EXTRA_EVENT_ID, src.getStringExtra(EXTRA_EVENT_ID))
        putExtra(EXTRA_CALENDAR_ID, src.getStringExtra(EXTRA_CALENDAR_ID))
        putExtra(EXTRA_TITLE, src.getStringExtra(EXTRA_TITLE))
        putExtra(EXTRA_START_ISO, src.getStringExtra(EXTRA_START_ISO))
        putExtra(EXTRA_END_ISO, src.getStringExtra(EXTRA_END_ISO))
        putExtra(EXTRA_PRIVATE, src.getBooleanExtra(EXTRA_PRIVATE, false))
    }

    private fun occurrenceDateFromStart(startIso: String?): String {
        if (startIso.isNullOrBlank()) return java.time.LocalDate.now().toString()
        return try {
            OffsetDateTime.parse(startIso).toLocalDate().toString()
        } catch (_: DateTimeParseException) {
            java.time.LocalDate.now().toString()
        }
    }

    companion object {
        const val ACTION_FIRE = "com.eight87.strictlykeptboy.notif.atomic.FIRE"
        const val ACTION_SNOOZE = "com.eight87.strictlykeptboy.notif.atomic.SNOOZE"
        const val ACTION_DEVIATION = "com.eight87.strictlykeptboy.notif.atomic.DEVIATION"
        const val ACTION_DID_IT = "com.eight87.strictlykeptboy.notif.atomic.DID_IT"

        const val EXTRA_REPO_ID = "repoId"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_CALENDAR_ID = "calendarId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_ISO = "startIso"
        const val EXTRA_END_ISO = "endIso"
        const val EXTRA_PRIVATE = "private"
        const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
        const val EXTRA_DEVIATION_KIND = "deviationKind"
        const val EXTRA_ALARM_KIND = "alarmKind"
    }
}
