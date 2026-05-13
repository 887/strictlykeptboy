package com.eight87.strictlykeptboy.widget.now

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.widget.common.WidgetConfigPrefs

/**
 * Phase EEE.11 — landing activity for now-widget placement.
 *
 * v1 saves the "all calendars, exact-minutes" defaults so the placement
 * completes; the in-app picker (post-MM) does the real work.
 */
class NowWidgetConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }
        setContentView(R.layout.widget_config_activity)
        findViewById<Button>(R.id.config_save).setOnClickListener {
            val prefs = WidgetConfigPrefs.open(this)
            prefs.setNowExactMinutes(appWidgetId, true)
            prefs.setNowCalendarFilter(appWidgetId, emptySet())
            val out = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, out)
            NowWidgetProvider.forceRefresh(this)
            finish()
        }
    }
}
