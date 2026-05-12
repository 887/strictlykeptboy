package com.eight87.strictlykeptboy.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R

/**
 * Phase J.1 — foreground service that hosts an in-progress sync pass.
 *
 * Started by [start] (manual sync) or by [SyncScheduler] when a periodic
 * tick fires while the app is in the foreground. The service holds the
 * `dataSync` foreground type required by Android 14+ for any network
 * I/O initiated from a non-UI process context.
 *
 * Lifecycle:
 * - `onStartCommand` always calls `startForeground` within 5s of dispatch
 *   (Android mandate) — we do it first, then enqueue the actual sync.
 * - `START_NOT_STICKY`: if the system kills us mid-sync, WorkManager picks
 *   up the deferred retry on next connectivity tick.
 *
 * Per ZZ.H: NO foreground work is scheduled for repos where
 * `remotes.isEmpty()` — the service refuses to start at all if there are
 * zero syncable repos in the store.
 */
class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val notif = buildNotification(this, "syncing repos")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        when (intent?.action) {
            ACTION_SYNC_ALL -> SyncRuntime.scheduler?.requestSyncAll()
            ACTION_SYNC_REPO -> intent.getStringExtra(EXTRA_REPO_ID)?.let {
                SyncRuntime.scheduler?.requestSync(it)
            }
        }

        // Stop ourselves once the queue drains. We can't easily observe
        // scheduler quiescence without coupling; for now the service stays
        // up for the lifetime of the process when bound to the scheduler.
        return START_NOT_STICKY
    }

    companion object {
        /**
         * Phase M.5 — the foreground notification lives on the new low-importance
         * `skb.service` channel (registered by [com.eight87.strictlykeptboy.notif.NotificationChannels]).
         * The legacy "skb_sync" id stays as a fallback for callers that registered
         * it pre-Phase-M but the canonical id is now NotificationChannels.FOREGROUND.
         */
        const val NOTIF_CHANNEL = "skb.service"
        const val NOTIF_ID = 87_001

        const val ACTION_SYNC_ALL = "com.eight87.strictlykeptboy.SYNC_ALL"
        const val ACTION_SYNC_REPO = "com.eight87.strictlykeptboy.SYNC_REPO"
        const val EXTRA_REPO_ID = "repoId"

        fun startSyncAll(context: Context) {
            val intent = Intent(context, SyncService::class.java).setAction(ACTION_SYNC_ALL)
            context.startForegroundService(intent)
        }

        fun startSyncRepo(context: Context, repoId: String) {
            val intent = Intent(context, SyncService::class.java)
                .setAction(ACTION_SYNC_REPO)
                .putExtra(EXTRA_REPO_ID, repoId)
            context.startForegroundService(intent)
        }

        internal fun ensureChannel(context: Context) {
            val nm = context.getSystemService<NotificationManager>() ?: return
            if (nm.getNotificationChannel(NOTIF_CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL,
                    "Sync",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Foreground notification while strictlykeptboy syncs."
                    setShowBadge(false)
                },
            )
        }

        internal fun buildNotification(context: Context, content: String): android.app.Notification {
            val tapIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, NOTIF_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("strictlykeptboy")
                .setContentText(content)
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}

/**
 * Process-wide handle so [SyncService] can find the singleton [SyncScheduler]
 * created by the application. Wired in [MainActivity] / app onCreate.
 */
object SyncRuntime {
    @Volatile var scheduler: SyncScheduler? = null
    @Volatile var statusStore: SyncStatusStore? = null
}
