package com.eight87.strictlykeptboy.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.NowNextResolver
import com.eight87.strictlykeptboy.resolver.NowNextSnapshot
import java.time.Duration

/**
 * Round 2.25 Phase C — ongoing notification rendering the Now/Next
 * snapshot (D-2.25.d). Pure builder + side-effecting `post`; the
 * channel is created lazily so we don't have to touch the shared
 * [NotificationChannels] list (this channel is process-singular,
 * always low-importance, no badge).
 *
 * SOLID-S: one reason to change — what the persistent now/next
 * notification looks like. The flow → post wiring is in
 * [com.eight87.strictlykeptboy.composition.AppGraph.parkRuntimes].
 *
 * Refresh discipline:
 *  - Indexer pulse → `nowNextFlow` re-emits → we re-post.
 *  - 60s ticker inside [com.eight87.strictlykeptboy.composition.AppGraph]
 *    re-emits the snapshot with a fresh `now_at` → relative copy
 *    advances ("in 2h 15m" → "in 2h 14m") without re-querying disk.
 *  - Exact `AlarmManager` boundary at `next.start` is intentionally
 *    deferred (D-2.25.d "fallback"); the 60s ticker already gives us
 *    sub-minute freshness at the boundary, and exact-alarm permission
 *    on API 31+ is a per-user opt-in we don't want to silently demand.
 */
class NowNextNotificationProvider(private val context: Context) {

    fun build(snapshot: NowNextSnapshot): Notification {
        ensureChannel()
        val titleText = snapshot.now?.let { now ->
            val emoji = if (!now.emoji.isNullOrBlank()) "${now.emoji} " else ""
            "Now: ${emoji}${now.title}"
        } ?: "No active task"
        val bodyText = snapshot.next?.let { nxt ->
            val emoji = if (!nxt.emoji.isNullOrBlank()) "${nxt.emoji} " else ""
            val rel = NowNextResolver.formatRelative(
                Duration.between(snapshot.now_at, nxt.start.toInstant()),
            )
            "Next: ${emoji}${nxt.title} · $rel"
        } ?: ""

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            PI_REQUEST_OPEN,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pending)
            .build()
    }

    /** Build + post under [NOTIF_ID]. Safe to call repeatedly. */
    fun post(snapshot: NowNextSnapshot) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(NOTIF_ID, build(snapshot))
    }

    /** Tear down the ongoing notification (e.g. on demo-mode flip). */
    fun cancel() {
        context.getSystemService<NotificationManager>()?.cancel(NOTIF_ID)
    }

    private fun ensureChannel() {
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Now / Next",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent status: current event + next upcoming."
            group = NotificationChannels.GROUP_MAIN
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        const val CHANNEL_ID: String = "now_next"
        const val NOTIF_ID: Int = 0x2_25_01
        private const val PI_REQUEST_OPEN: Int = 0x2_25_02
    }
}
