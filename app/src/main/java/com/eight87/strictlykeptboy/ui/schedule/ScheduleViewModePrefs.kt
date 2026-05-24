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
enum class SupersededDisplayMode { Hidden, Strikethrough }

class ScheduleViewModePrefs internal constructor(private val prefs: SharedPreferences) {
    private val _selected = MutableStateFlow(load())
    val selected: StateFlow<ScheduleViewTab> = _selected.asStateFlow()

    /**
     * Round 2.2.C.6 — when true, Month/Year overlays re-color bands by
     * `band.instance.repo.id.hashCode()` instead of `band.accentColorSeed`.
     * Persisted per-device.
     */
    private val _groupByRepo = MutableStateFlow(loadGroupByRepo())
    val groupByRepo: StateFlow<Boolean> = _groupByRepo.asStateFlow()

    /**
     * Round 2026-05-24 — visual treatment for bands tagged
     * `supersededByCalendar`. `Hidden` (default) drops them from the
     * rendered output entirely so the user only sees the active
     * special-base band; `Strikethrough` keeps them visible at 0.35
     * alpha + LineThrough so the user can see what was suppressed.
     * Toggled by the new floating button on the schedule cluster.
     */
    private val _supersededDisplayMode = MutableStateFlow(loadSupersededMode())
    val supersededDisplayMode: StateFlow<SupersededDisplayMode> = _supersededDisplayMode.asStateFlow()

    fun set(tab: ScheduleViewTab) {
        prefs.edit().putString(KEY_TAB, tab.name).apply()
        _selected.value = tab
    }

    fun setGroupByRepo(value: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_BY_REPO, value).apply()
        _groupByRepo.value = value
    }

    fun setSupersededDisplayMode(mode: SupersededDisplayMode) {
        prefs.edit().putString(KEY_SUPERSEDED_MODE, mode.name).apply()
        _supersededDisplayMode.value = mode
    }

    private fun loadSupersededMode(): SupersededDisplayMode =
        prefs.getString(KEY_SUPERSEDED_MODE, null)
            ?.let { runCatching { SupersededDisplayMode.valueOf(it) }.getOrNull() }
            ?: SupersededDisplayMode.Hidden

    private fun load(): ScheduleViewTab =
        prefs.getString(KEY_TAB, null)
            ?.let { raw ->
                // Migrate retired tab names: old `Schedule` (agenda list)
                // and old `Agenda` (timebox) both collapse onto `Now`.
                val mapped = when (raw) {
                    "Schedule", "Agenda" -> "Now"
                    else -> raw
                }
                runCatching { ScheduleViewTab.valueOf(mapped) }.getOrNull()
            }
            ?: ScheduleViewTab.Now

    private fun loadGroupByRepo(): Boolean =
        prefs.getBoolean(KEY_GROUP_BY_REPO, false)

    companion object {
        const val PREFS_FILE = "schedule_view_mode_v1"
        private const val KEY_TAB = "selectedTab"
        private const val KEY_GROUP_BY_REPO = "groupByRepo"
        private const val KEY_SUPERSEDED_MODE = "supersededDisplayMode"

        fun open(context: Context): ScheduleViewModePrefs =
            ScheduleViewModePrefs(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))

        internal fun openForTest(prefs: SharedPreferences) = ScheduleViewModePrefs(prefs)
    }
}
