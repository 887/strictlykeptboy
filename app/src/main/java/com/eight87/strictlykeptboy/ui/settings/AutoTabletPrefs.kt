package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 2.2.D.7 — Auto & Tablet category preferences.
 *
 * Sibling of [SyncSettingsPrefs]. Plain SharedPreferences — display-state
 * only, no secrets.
 *
 *  - `auto.show.<repoId>` Boolean (default true)
 *  - `auto.maxEvents` Int (default 8, coerced into 1..20)
 *  - `tablet.mode` String (TabletMasterDetailMode.name, default Auto)
 *  - `tablet.openDetail` Boolean (default false)
 */
enum class TabletMasterDetailMode { Auto, On, Off }

data class AutoTabletState(
    val showOnAutoForRepo: Map<String, Boolean> = emptyMap(),
    val maxAutoEventsToday: Int = 8,
    val tabletMasterDetailMode: TabletMasterDetailMode = TabletMasterDetailMode.Auto,
    val tabletOpenDetailByDefault: Boolean = false,
)

class AutoTabletPrefs internal constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AutoTabletState> = _state.asStateFlow()

    fun isShowOnAutoForRepo(repoId: String): Boolean =
        prefs.getBoolean(showKey(repoId), true)

    fun setShowOnAutoForRepo(repoId: String, value: Boolean) {
        prefs.edit().putBoolean(showKey(repoId), value).apply()
        _state.value = load()
    }

    fun setMaxAutoEventsToday(value: Int) {
        val coerced = value.coerceIn(MIN_EVENTS, MAX_EVENTS)
        prefs.edit().putInt(KEY_MAX_EVENTS, coerced).apply()
        _state.value = _state.value.copy(maxAutoEventsToday = coerced)
    }

    fun setTabletMasterDetailMode(mode: TabletMasterDetailMode) {
        prefs.edit().putString(KEY_TABLET_MODE, mode.name).apply()
        _state.value = _state.value.copy(tabletMasterDetailMode = mode)
    }

    fun setTabletOpenDetailByDefault(value: Boolean) {
        prefs.edit().putBoolean(KEY_OPEN_DETAIL, value).apply()
        _state.value = _state.value.copy(tabletOpenDetailByDefault = value)
    }

    private fun load(): AutoTabletState {
        val showMap = prefs.all
            .filter { (k, v) -> k.startsWith(SHOW_PREFIX) && v is Boolean }
            .mapKeys { (k, _) -> k.removePrefix(SHOW_PREFIX) }
            .mapValues { (_, v) -> v as Boolean }
        return AutoTabletState(
            showOnAutoForRepo = showMap,
            maxAutoEventsToday = prefs.getInt(KEY_MAX_EVENTS, 8).coerceIn(MIN_EVENTS, MAX_EVENTS),
            tabletMasterDetailMode = prefs.getString(KEY_TABLET_MODE, null)
                ?.let { runCatching { TabletMasterDetailMode.valueOf(it) }.getOrNull() }
                ?: TabletMasterDetailMode.Auto,
            tabletOpenDetailByDefault = prefs.getBoolean(KEY_OPEN_DETAIL, false),
        )
    }

    private fun showKey(repoId: String) = "$SHOW_PREFIX$repoId"

    companion object {
        const val MIN_EVENTS = 1
        const val MAX_EVENTS = 20
        private const val PREFS_FILE = "auto_tablet_v1"
        private const val SHOW_PREFIX = "auto.show."
        private const val KEY_MAX_EVENTS = "auto.maxEvents"
        private const val KEY_TABLET_MODE = "tablet.mode"
        private const val KEY_OPEN_DETAIL = "tablet.openDetail"

        fun open(context: Context): AutoTabletPrefs = AutoTabletPrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        /** Test-only constructor. */
        internal fun openForTest(prefs: SharedPreferences): AutoTabletPrefs =
            AutoTabletPrefs(prefs)
    }
}
