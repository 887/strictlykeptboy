package com.eight87.strictlykeptboy.widget.countdown

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.avatar.StickerResolver
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.widget.WidgetGraph
import com.eight87.strictlykeptboy.widget.common.Countdown
import com.eight87.strictlykeptboy.widget.common.PinnedEvent
import com.eight87.strictlykeptboy.widget.common.PinnedEventSource
import com.eight87.strictlykeptboy.widget.common.WidgetAlarmScheduler
import com.eight87.strictlykeptboy.widget.common.WidgetConfigPrefs
import com.eight87.strictlykeptboy.widget.common.WidgetLayoutSize
import com.eight87.strictlykeptboy.widget.common.WidgetPrivacy
import com.eight87.strictlykeptboy.widget.common.WidgetStickerRenderer
import com.eight87.strictlykeptboy.widget.common.WidgetTimeFormat
import java.time.Instant

/**
 * Phase VV.1 — homescreen countdown widget.
 *
 * Pins a future event; re-resolves on each minute-tick alarm + each
 * `onUpdate`. Three layouts: 2x1 (sticker + dd:hh:mm), 4x2 (+ title),
 * 4x4 (+ progress bar + subtitle). Privacy (K-2 / D.57) enforced via
 * [WidgetPrivacy] — redacted events render bat + "Scheduled event".
 *
 * Construction is dependency-inverted via [WidgetGraph] (composition
 * root tunable for tests).
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        WidgetAlarmScheduler(context).schedule(CountdownWidgetProvider::class.java)
    }

    override fun onDisabled(context: Context) {
        WidgetAlarmScheduler(context).cancel(CountdownWidgetProvider::class.java)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = WidgetConfigPrefs.open(context)
        appWidgetIds.forEach { prefs.clear(it) }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val graph = WidgetGraph.get(context)
        appWidgetIds.forEach { id ->
            val views = renderFor(context, appWidgetManager, id, graph.pinnedEventSource, graph.stickerRenderer)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        val graph = WidgetGraph.get(context)
        val views = renderFor(context, appWidgetManager, appWidgetId, graph.pinnedEventSource, graph.stickerRenderer)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun renderFor(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        source: PinnedEventSource,
        stickers: WidgetStickerRenderer,
    ): RemoteViews {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
        val onLockscreen = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, HOST_HOME,
        ) == HOST_KEYGUARD
        val size = WidgetLayoutSize.pick(minW, minH)

        val pinned = source.pinnedFor(appWidgetId, Instant.now())
        val layout = when (size) {
            WidgetLayoutSize.Size2x1 -> R.layout.widget_countdown_2x1
            WidgetLayoutSize.Size4x2 -> R.layout.widget_countdown_4x2
            WidgetLayoutSize.Size4x4 -> R.layout.widget_countdown_4x4
        }
        val views = RemoteViews(context.packageName, layout)
        when (pinned) {
            is PinnedEvent.Present -> bindPresent(context, views, size, pinned, stickers, onLockscreen)
            PinnedEvent.Missing -> bindMissing(context, views, size, stickers)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, pinned))
        return views
    }

    private fun bindPresent(
        context: Context,
        views: RemoteViews,
        size: WidgetLayoutSize,
        pinned: PinnedEvent.Present,
        stickers: WidgetStickerRenderer,
        onLockscreen: Boolean,
    ) {
        val instance = pinned.instance
        val graph = WidgetGraph.get(context)
        val surface = if (onLockscreen) WidgetPrivacy.Surface.Lockscreen else WidgetPrivacy.Surface.Home
        val redact = WidgetPrivacy.shouldRedact(instance, surface) { false }
        val title = if (redact) context.getString(R.string.widget_redacted_title) else instance.title
        val sticker = if (redact) stickers.renderBatFallback() else stickers.render(
            StickerResolver.Request(
                species = graph.activeSpecies(),
                activityId = instance.tags.firstOrNull(),
                neutralMode = graph.neutralMode(),
            ),
        )
        if (sticker != null) views.setImageViewBitmap(R.id.widget_sticker, sticker)
        views.setTextViewText(R.id.widget_countdown, WidgetTimeFormat.ddhhmm(pinned.countdown))
        if (size != WidgetLayoutSize.Size2x1) {
            views.setTextViewText(R.id.widget_title, title)
        }
        if (size == WidgetLayoutSize.Size4x4) {
            views.setTextViewText(R.id.widget_subtitle, subtitleFor(context, pinned.countdown))
            views.setProgressBar(R.id.widget_progress, 100, progressOf(pinned.countdown), false)
        }
    }

    private fun bindMissing(
        context: Context,
        views: RemoteViews,
        size: WidgetLayoutSize,
        stickers: WidgetStickerRenderer,
    ) {
        stickers.renderBatFallback()?.let { views.setImageViewBitmap(R.id.widget_sticker, it) }
        views.setTextViewText(R.id.widget_countdown, "--:--:--")
        if (size != WidgetLayoutSize.Size2x1) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_pinned_event))
        }
    }

    private fun subtitleFor(context: Context, c: Countdown): String = when (WidgetTimeFormat.bucket(c)) {
        WidgetTimeFormat.Bucket.Today -> context.getString(R.string.widget_countdown_today)
        WidgetTimeFormat.Bucket.Tomorrow -> context.getString(R.string.widget_countdown_tomorrow)
        WidgetTimeFormat.Bucket.InDays -> context.resources.getQuantityString(
            R.plurals.widget_countdown_in_days,
            c.days.toInt(),
            c.days.toInt(),
        )
        WidgetTimeFormat.Bucket.DaysAgo -> context.resources.getQuantityString(
            R.plurals.widget_countdown_past_due,
            c.days.toInt(),
            c.days.toInt(),
        )
    }

    private fun progressOf(c: Countdown): Int {
        if (c.isPastDue) return 0
        val pct = (c.absMinutes.coerceAtMost(30L * 1440L) * 100L / (30L * 1440L)).toInt()
        return 100 - pct
    }

    private fun openIntent(context: Context, pinned: PinnedEvent): PendingIntent {
        val intent = when (pinned) {
            is PinnedEvent.Present -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("strictlykeptboy://event/${eventGlobalId(pinned.instance)}"),
                context, MainActivity::class.java,
            )
            PinnedEvent.Missing -> Intent(context, MainActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            (intent.dataString ?: "vv-empty").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun eventGlobalId(i: MaterializedInstance): String =
        "${i.repo.id}/${i.calendar.id}/${i.instanceId}"

    companion object {
        const val HOST_HOME = 1
        const val HOST_KEYGUARD = 2

        fun forceRefresh(context: Context) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(context, CountdownWidgetProvider::class.java)
            }
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
