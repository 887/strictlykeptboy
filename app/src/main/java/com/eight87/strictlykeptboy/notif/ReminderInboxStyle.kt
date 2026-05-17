package com.eight87.strictlykeptboy.notif

import android.content.Context
import androidx.core.app.NotificationCompat
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.ReminderCollapsing
import java.time.Instant

/**
 * Phase 2.1.F.9 — wrap [ReminderCollapsing] output as an Android
 * `InboxStyle` notification body. Collapsed-preview privacy rule
 * (per the audit): if any constituent reminder is marked `private`,
 * the inbox line set is suppressed in favour of a generic "N reminders"
 * count to avoid leaking a praise term + title onto the lockscreen.
 *
 * SOLID-S: pure helper. The actual posting site lives in
 * [ReminderBroadcastReceiver] (single-event) or future stacking flows.
 */
object ReminderInboxStyle {

    /**
     * Per-event line: the title + a hint. When [anyPrivate] is true, the
     * caller should pass null lines and rely on the count summary
     * (see [applyToBuilder]).
     */
    data class Line(val title: String, val isPrivate: Boolean)

    fun applyToBuilder(
        context: Context,
        builder: NotificationCompat.Builder,
        lines: List<Line>,
    ): NotificationCompat.Builder {
        if (lines.isEmpty()) return builder
        val anyPrivate = lines.any { it.isPrivate }
        val style = NotificationCompat.InboxStyle()
        if (anyPrivate) {
            // Privacy: collapse all lines into a single count summary so
            // no title leaks alongside the praise term.
            style.addLine(context.resources.getQuantityString(R.plurals.notif_event_collapsed_count, lines.size, lines.size))
        } else {
            lines.forEach { style.addLine(it.title) }
        }
        style.setSummaryText(
            context.resources.getQuantityString(R.plurals.notif_event_collapsed_count, lines.size, lines.size),
        )
        return builder.setStyle(style)
    }

    /**
     * Helper for tests + the post-time collapsing decision: collapse a
     * list of pending fire-instants into 60s buckets and return the
     * bucket whose anchor is closest to [anchor]. Returns `null` when
     * [pending] is empty.
     */
    fun bucketFor(
        pending: List<Instant>,
        anchor: Instant,
    ): ReminderCollapsing.Bucket? {
        val buckets = ReminderCollapsing.collapse(pending)
        if (buckets.isEmpty()) return null
        return buckets.minByOrNull { kotlin.math.abs(it.anchor.toEpochMilli() - anchor.toEpochMilli()) }
    }
}
