package com.eight87.strictlykeptboy.widget.countdown

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.widget.common.WidgetConfigPrefs

/**
 * Phase VV.6 — landing activity for countdown-widget placement.
 *
 * v1 persists the "specific event, no target yet" default — the in-app
 * picker fills in the target later. Saving immediately is required by
 * the AppWidget contract: without `RESULT_OK` the launcher cancels the
 * placement.
 */
class CountdownWidgetConfigActivity : Activity() {

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
            prefs.setCountdownPickerMode(appWidgetId, WidgetConfigPrefs.PickerMode.SpecificEvent)
            val out = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, out)
            CountdownWidgetProvider.forceRefresh(this)
            finish()
        }
    }
}
