package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R

/**
 * Phase M.4 — surfaces sync outcomes onto the silent `skb.sync` channel
 * (success) or the `skb.errors` channel (auth / conflict failures).
 *
 * Replace-by-notification-id semantics so a repeating error does not
 * spam the shade. Auto-dismiss after 5 s for success-state posts.
 */
object SyncResultNotifier {

    private const val SUCCESS_NOTIF_ID = 9_001
    private const val ERROR_NOTIF_ID_BASE = 9_100
    private const val AUTH_NOTIF_ID_BASE = 9_200

    fun postSuccess(context: Context, reposSynced: Int, durationMs: Long) {
        val prefs = NotificationPrefs.open(context)
        if (!prefs.isChannelEnabled(NotificationChannels.SYNC)) return
        val seconds = "%.1fs".format(durationMs / 1000.0)
        val text = context.resources.getQuantityString(
            R.plurals.notif_sync_success,
            reposSynced,
            reposSynced,
            seconds,
        )
        val tap = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.SYNC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(tap)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(5_000)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        context.getSystemService<NotificationManager>()?.notify(SUCCESS_NOTIF_ID, n)
    }

    fun postConflict(context: Context, repoId: String, conflictCount: Int) {
        if (!NotificationPrefs.open(context).isChannelEnabled(NotificationChannels.ERRORS)) return
        val text = context.resources.getQuantityString(
            R.plurals.notif_sync_conflict,
            conflictCount,
            conflictCount,
        )
        val tap = PendingIntent.getActivity(
            context, repoId.hashCode(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.ERRORS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_sync_conflict_title))
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        context.getSystemService<NotificationManager>()
            ?.notify(ERROR_NOTIF_ID_BASE + repoId.hashCode().mod(100), n)
    }

    fun postAuthExpired(context: Context, repoId: String, repoDisplayName: String) {
        if (!NotificationPrefs.open(context).isChannelEnabled(NotificationChannels.ERRORS)) return
        val tap = PendingIntent.getActivity(
            context, ("auth$repoId").hashCode(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.ERRORS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_sync_auth_title))
            .setContentText(context.getString(R.string.notif_sync_auth_body, repoDisplayName))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        context.getSystemService<NotificationManager>()
            ?.notify(AUTH_NOTIF_ID_BASE + repoId.hashCode().mod(100), n)
    }
}
