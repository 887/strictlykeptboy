package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase S.3 — global sync preferences (default interval, wifi-only,
 * push policy, conflict resolution).
 *
 * Display-state only (not secrets) → plain SharedPreferences.
 */
enum class PushPolicy { Push, PushLazy, Manual }
enum class ConflictPolicy { AutoRebase, ManualOnly }

data class SyncSettingsState(
    val defaultIntervalMinutes: Int = 60,
    val wifiOnly: Boolean = true,
    val pushPolicy: PushPolicy = PushPolicy.Push,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.AutoRebase,
)

class SyncSettingsPrefs internal constructor(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SyncSettingsState> = _state.asStateFlow()

    fun setInterval(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL, minutes).apply()
        _state.value = _state.value.copy(defaultIntervalMinutes = minutes)
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _state.value = _state.value.copy(wifiOnly = enabled)
    }

    fun setPushPolicy(policy: PushPolicy) {
        prefs.edit().putString(KEY_PUSH, policy.name).apply()
        _state.value = _state.value.copy(pushPolicy = policy)
    }

    fun setConflictPolicy(policy: ConflictPolicy) {
        prefs.edit().putString(KEY_CONFLICT, policy.name).apply()
        _state.value = _state.value.copy(conflictPolicy = policy)
    }

    private fun load() = SyncSettingsState(
        defaultIntervalMinutes = prefs.getInt(KEY_INTERVAL, 60),
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
        pushPolicy = prefs.getString(KEY_PUSH, null)?.let { runCatching { PushPolicy.valueOf(it) }.getOrNull() }
            ?: PushPolicy.Push,
        conflictPolicy = prefs.getString(KEY_CONFLICT, null)?.let { runCatching { ConflictPolicy.valueOf(it) }.getOrNull() }
            ?: ConflictPolicy.AutoRebase,
    )

    companion object {
        private const val PREFS_FILE = "sync_settings_v1"
        private const val KEY_INTERVAL = "default_interval_min"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_PUSH = "push_policy"
        private const val KEY_CONFLICT = "conflict_policy"

        fun open(context: Context) = SyncSettingsPrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        internal fun openForTest(prefs: SharedPreferences) = SyncSettingsPrefs(prefs)
    }
}
