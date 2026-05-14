package com.eight87.strictlykeptboy.ui.schedule

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase FFF / EC-A.3 — last-used-tab persistence for the event-create
 * sheet's two-tab segmented control (`Free-form` | `From template`).
 *
 * Mirrors the [ScheduleViewModePrefs] / [com.eight87.strictlykeptboy.theme.AppearancePrefs]
 * pattern: small SharedPreferences-backed class, StateFlow for Compose,
 * write-through on every set.
 */
enum class EventCreateTab { FreeForm, Template }

class EventCreatePrefs internal constructor(private val prefs: SharedPreferences) {
    private val _selected = MutableStateFlow(load())
    val selected: StateFlow<EventCreateTab> = _selected.asStateFlow()

    fun set(tab: EventCreateTab) {
        prefs.edit().putString(KEY_TAB, tab.name).apply()
        _selected.value = tab
    }

    /**
     * Round 2.1.B.10 — last-used calendar per repo (transient prefs).
     * Replaces the hard `RepoConfig.defaultCalendarId` authoring binding.
     * EventCreateSheet's calendar picker defaults to whatever was last
     * picked for the active write-target repo.
     */
    fun lastUsedCalendar(repoId: String): String? =
        prefs.getString("$KEY_LAST_CAL_PREFIX$repoId", null)

    fun setLastUsedCalendar(repoId: String, calendarId: String) {
        prefs.edit().putString("$KEY_LAST_CAL_PREFIX$repoId", calendarId).apply()
    }

    private fun load(): EventCreateTab =
        prefs.getString(KEY_TAB, null)
            ?.let { runCatching { EventCreateTab.valueOf(it) }.getOrNull() }
            ?: EventCreateTab.FreeForm

    companion object {
        const val PREFS_FILE = "event_create_v1"
        private const val KEY_TAB = "selectedTab"
        private const val KEY_LAST_CAL_PREFIX = "lastCal."

        fun open(context: Context): EventCreatePrefs =
            EventCreatePrefs(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))

        internal fun openForTest(prefs: SharedPreferences) = EventCreatePrefs(prefs)
    }
}
