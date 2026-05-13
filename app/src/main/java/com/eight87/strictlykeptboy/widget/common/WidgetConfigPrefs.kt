package com.eight87.strictlykeptboy.widget.common

import android.content.Context
import android.content.SharedPreferences

/**
 * Phase VV.6 / EEE.11 — per-`appWidgetId` configuration store.
 *
 * One key namespace per widget instance so multiple widgets on the
 * homescreen each carry independent picker + override state.
 */
class WidgetConfigPrefs(private val prefs: SharedPreferences) {

    fun setCountdownPickerMode(widgetId: Int, mode: PickerMode) {
        prefs.edit().putString(keyMode(widgetId), mode.raw).apply()
    }
    fun countdownPickerMode(widgetId: Int): PickerMode =
        PickerMode.fromRaw(prefs.getString(keyMode(widgetId), null))

    fun setCountdownTarget(widgetId: Int, eventId: String?) {
        prefs.edit().putString(keyEvent(widgetId), eventId).apply()
    }
    fun countdownTarget(widgetId: Int): String? = prefs.getString(keyEvent(widgetId), null)

    fun setCountdownCalendar(widgetId: Int, calendarRef: String?) {
        prefs.edit().putString(keyCalendar(widgetId), calendarRef).apply()
    }
    fun countdownCalendar(widgetId: Int): String? = prefs.getString(keyCalendar(widgetId), null)

    fun setStickerPackOverride(widgetId: Int, species: String, packId: String?) {
        prefs.edit().putString(keyPack(widgetId, species), packId).apply()
    }
    fun stickerPackOverride(widgetId: Int, species: String): String? =
        prefs.getString(keyPack(widgetId, species), null)

    fun setNowExactMinutes(widgetId: Int, exact: Boolean) {
        prefs.edit().putBoolean(keyExact(widgetId), exact).apply()
    }
    fun nowExactMinutes(widgetId: Int): Boolean = prefs.getBoolean(keyExact(widgetId), true)

    fun setNowCalendarFilter(widgetId: Int, calendarRefs: Set<String>) {
        prefs.edit().putStringSet(keyCalFilter(widgetId), calendarRefs).apply()
    }
    fun nowCalendarFilter(widgetId: Int): Set<String> =
        prefs.getStringSet(keyCalFilter(widgetId), emptySet()) ?: emptySet()

    fun clear(widgetId: Int) {
        val e = prefs.edit()
        prefs.all.keys.filter { it.endsWith(":$widgetId") || it.startsWith("$widgetId:") }
            .forEach { e.remove(it) }
        e.apply()
    }

    enum class PickerMode(val raw: String) {
        SpecificEvent("event"),
        NextInCalendar("next");

        companion object {
            fun fromRaw(s: String?): PickerMode =
                values().firstOrNull { it.raw == s } ?: SpecificEvent
        }
    }

    private fun keyMode(id: Int) = "vv.mode:$id"
    private fun keyEvent(id: Int) = "vv.event:$id"
    private fun keyCalendar(id: Int) = "vv.calendar:$id"
    private fun keyPack(id: Int, species: String) = "$id:pack:$species"
    private fun keyExact(id: Int) = "eee.exact:$id"
    private fun keyCalFilter(id: Int) = "eee.calfilter:$id"

    companion object {
        const val FILE_NAME = "widget_config"

        fun open(context: Context): WidgetConfigPrefs {
            val sp = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            return WidgetConfigPrefs(sp)
        }
    }
}
