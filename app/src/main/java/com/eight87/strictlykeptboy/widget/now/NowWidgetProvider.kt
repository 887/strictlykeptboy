package com.eight87.strictlykeptboy.widget.now

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
import com.eight87.strictlykeptboy.widget.common.ActiveEvent
import com.eight87.strictlykeptboy.widget.common.ActiveEventSource
import com.eight87.strictlykeptboy.widget.common.UpcomingItem
import com.eight87.strictlykeptboy.widget.common.WidgetAlarmScheduler
import com.eight87.strictlykeptboy.widget.common.WidgetConfigPrefs
import com.eight87.strictlykeptboy.widget.common.WidgetLayoutSize
import com.eight87.strictlykeptboy.widget.common.WidgetPrivacy
import com.eight87.strictlykeptboy.widget.common.WidgetStickerRenderer
import com.eight87.strictlykeptboy.widget.common.WidgetTimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase EEE.1 — homescreen + lockscreen now-widget.
 *
 * Sibling to [com.eight87.strictlykeptboy.widget.countdown.CountdownWidgetProvider];
 * shares alarm scheduler, sticker renderer, config prefs, and privacy
 * contract. Queries an [ActiveEventSource] for `Instant.now()` rather
 * than a pinned future event.
 *
 * Layouts (EEE.3): 2x1 → sticker + title; 4x2 → + remaining + sub-beat;
 * 4x4 → + 3-up upcoming. 4x4 home-only (EEE.7); on lockscreen, 4x4
 * falls back to 4x2.
 */
class NowWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        WidgetAlarmScheduler(context).schedule(NowWidgetProvider::class.java)
    }

    override fun onDisabled(context: Context) {
        WidgetAlarmScheduler(context).cancel(NowWidgetProvider::class.java)
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
            val views = renderFor(context, appWidgetManager, id, graph.activeEventSource, graph.stickerRenderer)
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
        val views = renderFor(context, appWidgetManager, appWidgetId, graph.activeEventSource, graph.stickerRenderer)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun renderFor(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        source: ActiveEventSource,
        stickers: WidgetStickerRenderer,
    ): RemoteViews {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
        val onLockscreen = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, HOST_HOME,
        ) == HOST_KEYGUARD
        var size = WidgetLayoutSize.pick(minW, minH)
        if (onLockscreen && size == WidgetLayoutSize.Size4x4) size = WidgetLayoutSize.Size4x2

        val active = source.activeAt(Instant.now())
        val layout = when (size) {
            WidgetLayoutSize.Size2x1 -> R.layout.widget_now_2x1
            WidgetLayoutSize.Size4x2 -> R.layout.widget_now_4x2
            WidgetLayoutSize.Size4x4 -> R.layout.widget_now_4x4
        }
        val views = RemoteViews(context.packageName, layout)
        when (active) {
            is ActiveEvent.Present -> bindPresent(context, views, size, active, stickers, onLockscreen)
            ActiveEvent.Absent -> bindAbsent(context, views, size, stickers)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, active))
        return views
    }

    private fun bindPresent(
        context: Context,
        views: RemoteViews,
        size: WidgetLayoutSize,
        active: ActiveEvent.Present,
        stickers: WidgetStickerRenderer,
        onLockscreen: Boolean,
    ) {
        val instance = active.instance
        val graph = WidgetGraph.get(context)
        val surface = if (onLockscreen) WidgetPrivacy.Surface.Lockscreen else WidgetPrivacy.Surface.Home
        val redact = WidgetPrivacy.shouldRedact(instance, surface) { false }
        val title = if (redact) context.getString(R.string.widget_redacted_title) else instance.title
        val sticker = if (redact) stickers.renderBatFallback() else stickers.render(
            StickerResolver.Request(
                species = graph.activeSpecies(),
                activityId = instance.tags.firstOrNull(),
                subbeatIndex = active.subbeatIndex,
                subbeatStickerId = active.subbeatStickerId,
                neutralMode = graph.neutralMode(),
            ),
        )
        if (sticker != null) views.setImageViewBitmap(R.id.widget_sticker, sticker)
        views.setTextViewText(R.id.widget_title, title)

        if (size != WidgetLayoutSize.Size2x1) {
            views.setTextViewText(R.id.widget_remaining, WidgetTimeFormat.exactRemaining(active.remainingMinutes))
            val subbeat = if (redact) null else active.subbeatLabel
            views.setTextViewText(R.id.widget_subbeat, subbeat ?: "")
        }
        if (size == WidgetLayoutSize.Size4x4) {
            bindUpcoming(context, views, active.upcoming, redact)
        }
    }

    private fun bindUpcoming(
        context: Context,
        views: RemoteViews,
        upcoming: List<UpcomingItem>,
        parentRedacted: Boolean,
    ) {
        val ids = intArrayOf(R.id.widget_upcoming_1, R.id.widget_upcoming_2, R.id.widget_upcoming_3)
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        for (i in ids.indices) {
            val item = upcoming.getOrNull(i)
            if (item == null) { views.setTextViewText(ids[i], ""); continue }
            val title = if (item.isPrivate || parentRedacted) context.getString(R.string.widget_redacted_title) else item.title
            val time = fmt.format(Instant.ofEpochMilli(item.startEpochMillis))
            views.setTextViewText(ids[i], "$time   $title")
        }
    }

    private fun bindAbsent(
        context: Context,
        views: RemoteViews,
        size: WidgetLayoutSize,
        stickers: WidgetStickerRenderer,
    ) {
        stickers.renderBatFallback()?.let { views.setImageViewBitmap(R.id.widget_sticker, it) }
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_now_empty_good_boy))
        if (size != WidgetLayoutSize.Size2x1) {
            views.setTextViewText(R.id.widget_remaining, "")
            views.setTextViewText(R.id.widget_subbeat, "")
        }
        if (size == WidgetLayoutSize.Size4x4) {
            views.setTextViewText(R.id.widget_upcoming_1, "")
            views.setTextViewText(R.id.widget_upcoming_2, "")
            views.setTextViewText(R.id.widget_upcoming_3, "")
        }
    }

    private fun openIntent(context: Context, active: ActiveEvent): PendingIntent {
        val intent = when (active) {
            is ActiveEvent.Present -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("strictlykeptboy://event/${eventGlobalId(active.instance)}"),
                context, MainActivity::class.java,
            )
            ActiveEvent.Absent -> Intent(context, MainActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            (intent.dataString ?: "now-empty").hashCode(),
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
                component = ComponentName(context, NowWidgetProvider::class.java)
            }
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, NowWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
