package com.eight87.strictlykeptboy.notif

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.eight87.strictlykeptboy.R

/**
 * Phase M.1 — notification channel registration.
 *
 * Six channels under a single channel-group so the system Settings page
 * renders them collapsed under one "strictlykeptboy" heading. Registration
 * is idempotent — the OS dedupes by id — so we re-run on every app start.
 *
 * Importance values are INITIAL. Once a channel exists, the user owns
 * it; the app cannot raise importance afterwards.
 */
object NotificationChannels {

    const val GROUP_MAIN = "skb_main"

    const val EVENTS = "skb.events"
    const val EVENTS_ATOMIC = "skb.events.atomic"
    const val TASKS = "skb.tasks"
    const val BRIEFINGS = "skb.briefings"
    const val SYNC = "skb.sync"
    const val ERRORS = "skb.errors"
    /** Foreground service uses this channel. Kept in sync with [SyncService.NOTIF_CHANNEL]. */
    const val FOREGROUND = "skb.service"

    /** All channel ids in order — used by the registration test. */
    val ALL: List<String> = listOf(EVENTS, EVENTS_ATOMIC, TASKS, BRIEFINGS, SYNC, ERRORS, FOREGROUND)

    /**
     * Register the group + all channels. Safe to call repeatedly.
     */
    fun registerAll(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_MAIN, context.getString(R.string.app_name)),
        )

        val channels = listOf(
            channel(
                context, EVENTS,
                R.string.notif_channel_events_name,
                R.string.notif_channel_events_desc,
                NotificationManager.IMPORTANCE_DEFAULT,
                lockscreen = NotificationCompatVisibility.PUBLIC,
                showBadge = true,
                vibrate = true,
            ),
            // Phase XX.3 / AT-C.1 — separate channel so user can downgrade
            // atomic-activity nudges without losing event reminders.
            // IMPORTANCE_HIGH for lockscreen-visible buzz per AT-C.1.
            channel(
                context, EVENTS_ATOMIC,
                R.string.notif_channel_events_atomic_name,
                R.string.notif_channel_events_atomic_desc,
                NotificationManager.IMPORTANCE_HIGH,
                lockscreen = NotificationCompatVisibility.PUBLIC,
                showBadge = true,
                vibrate = true,
            ),
            channel(
                context, TASKS,
                R.string.notif_channel_tasks_name,
                R.string.notif_channel_tasks_desc,
                NotificationManager.IMPORTANCE_DEFAULT,
                lockscreen = NotificationCompatVisibility.PUBLIC,
                showBadge = true,
                vibrate = true,
            ),
            channel(
                context, BRIEFINGS,
                R.string.notif_channel_briefings_name,
                R.string.notif_channel_briefings_desc,
                NotificationManager.IMPORTANCE_LOW,
                lockscreen = NotificationCompatVisibility.PUBLIC,
                showBadge = false,
                vibrate = false,
            ),
            channel(
                context, SYNC,
                R.string.notif_channel_sync_name,
                R.string.notif_channel_sync_desc,
                NotificationManager.IMPORTANCE_MIN,
                lockscreen = NotificationCompatVisibility.SECRET,
                showBadge = false,
                vibrate = false,
            ),
            channel(
                context, ERRORS,
                R.string.notif_channel_errors_name,
                R.string.notif_channel_errors_desc,
                NotificationManager.IMPORTANCE_HIGH,
                lockscreen = NotificationCompatVisibility.PUBLIC,
                showBadge = true,
                vibrate = true,
            ),
            channel(
                context, FOREGROUND,
                R.string.notif_channel_foreground_name,
                R.string.notif_channel_foreground_desc,
                NotificationManager.IMPORTANCE_LOW,
                lockscreen = NotificationCompatVisibility.SECRET,
                showBadge = false,
                vibrate = false,
            ),
        )
        channels.forEach(nm::createNotificationChannel)
    }

    private enum class NotificationCompatVisibility(val raw: Int) {
        PUBLIC(android.app.Notification.VISIBILITY_PUBLIC),
        SECRET(android.app.Notification.VISIBILITY_SECRET),
    }

    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
        descRes: Int,
        importance: Int,
        lockscreen: NotificationCompatVisibility,
        showBadge: Boolean,
        vibrate: Boolean,
    ): NotificationChannel = NotificationChannel(id, context.getString(nameRes), importance).apply {
        description = context.getString(descRes)
        group = GROUP_MAIN
        setShowBadge(showBadge)
        enableVibration(vibrate)
        lockscreenVisibility = lockscreen.raw
    }
}
