package com.eight87.strictlykeptboy.widget.common

import android.widget.RemoteViews
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.NowNextResolver
import com.eight87.strictlykeptboy.resolver.NowNextSnapshot
import java.time.Duration

/**
 * Round 2.25 Phase D — shared Now/Next binder used by both
 * [com.eight87.strictlykeptboy.widget.now.NowWidgetProvider] and
 * [com.eight87.strictlykeptboy.widget.countdown.CountdownWidgetProvider]
 * so neither re-implements the formatting (D-2.25.e).
 *
 * The caller decides which `RemoteViews` slots to bind into — this
 * helper only knows how to format the snapshot's `next` band into a
 * `(titleText, relativeText)` pair and apply them to two text-view
 * ids.
 *
 * Returns true iff the snapshot had a `next` band (so the caller can
 * decide whether to leave layout space visible).
 */
object WidgetRenderer {

    data class NextLine(val title: String, val relative: String)

    /** Pure: format the snapshot's `next` band into a two-line pair. */
    fun nextLine(snapshot: NowNextSnapshot): NextLine? {
        val nxt = snapshot.next ?: return null
        val emoji = if (!nxt.emoji.isNullOrBlank()) "${nxt.emoji} " else ""
        val title = "Next: ${emoji}${nxt.title}"
        val rel = NowNextResolver.formatRelative(
            Duration.between(snapshot.now_at, nxt.start.toInstant()),
        )
        return NextLine(title = title, relative = rel)
    }

    /**
     * Bind the snapshot's `next` block into [titleViewId] +
     * [relativeViewId]. When the snapshot has no `next`, both views
     * are cleared to the empty string.
     */
    fun bindNowNext(
        views: RemoteViews,
        snapshot: NowNextSnapshot,
        titleViewId: Int = R.id.widget_subbeat,
        relativeViewId: Int = R.id.widget_remaining,
    ): Boolean {
        val line = nextLine(snapshot)
        if (line == null) {
            views.setTextViewText(titleViewId, "")
            views.setTextViewText(relativeViewId, "")
            return false
        }
        views.setTextViewText(titleViewId, line.title)
        views.setTextViewText(relativeViewId, line.relative)
        return true
    }
}
