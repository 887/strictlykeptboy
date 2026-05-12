package com.eight87.strictlykeptboy.ui.schedule

import android.content.Context
import android.content.SharedPreferences
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase G.6 — persists the last-selected schedule view tab per-device.
 *
 * Plain `SharedPreferences` per the brief — not a secret, not synced
 * across devices, just a UI preference. Mirrors the [com.eight87.strictlykeptboy.theme.AppearancePrefs]
 * pattern: a small class that owns its prefs file, exposes a StateFlow
 * for Compose, and writes through on every set.
 */
class ScheduleViewModePrefs internal constructor(private val prefs: SharedPreferences) {
    private val _selected = MutableStateFlow(load())
    val selected: StateFlow<ScheduleViewTab> = _selected.asStateFlow()

    fun set(tab: ScheduleViewTab) {
        prefs.edit().putString(KEY_TAB, tab.name).apply()
        _selected.value = tab
    }

    private fun load(): ScheduleViewTab =
        prefs.getString(KEY_TAB, null)
            ?.let { runCatching { ScheduleViewTab.valueOf(it) }.getOrNull() }
            ?: ScheduleViewTab.Day

    companion object {
        const val PREFS_FILE = "schedule_view_mode_v1"
        private const val KEY_TAB = "selectedTab"

        fun open(context: Context): ScheduleViewModePrefs =
            ScheduleViewModePrefs(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))

        internal fun openForTest(prefs: SharedPreferences) = ScheduleViewModePrefs(prefs)
    }
}
