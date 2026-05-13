package com.eight87.strictlykeptboy.notif

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase M.3 — per-channel + per-calendar notification preferences.
 *
 * Plain SharedPreferences (`notification_prefs_v1.xml`). Display-only
 * settings — no secrets.
 *
 *  - `channel.<id>.enabled` Boolean (default true)
 *  - `channel.<id>.silent`  Boolean (default false)
 *  - `cal.<repoId>.<calId>.enabled` Boolean (default true)
 *  - `cal.<repoId>.<calId>.silent`  Boolean (default false)
 *  - `cal.<repoId>.<calId>.leadtimes` semicolon-joined list overriding D.79 defaults
 *
 * The per-calendar group-level lead-time override list is stored as a
 * single string (semicolon-delimited tokens like `15m;1h;1d`) to keep
 * the SharedPreferences shape flat.
 */
class NotificationPrefs internal constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(loadAll())
    val state: StateFlow<Map<String, Any>> = _state.asStateFlow()

    // --- channel-level --------------------------------------------------

    fun isChannelEnabled(channelId: String): Boolean =
        prefs.getBoolean(channelKey(channelId, "enabled"), true)

    fun isChannelSilent(channelId: String): Boolean =
        prefs.getBoolean(channelKey(channelId, "silent"), false)

    fun setChannelEnabled(channelId: String, value: Boolean) {
        prefs.edit().putBoolean(channelKey(channelId, "enabled"), value).apply()
        _state.value = loadAll()
    }

    fun setChannelSilent(channelId: String, value: Boolean) {
        prefs.edit().putBoolean(channelKey(channelId, "silent"), value).apply()
        _state.value = loadAll()
    }

    // --- per-calendar group ---------------------------------------------

    fun isCalendarEnabled(repoId: String, calendarId: String): Boolean =
        prefs.getBoolean(calKey(repoId, calendarId, "enabled"), true)

    fun isCalendarSilent(repoId: String, calendarId: String): Boolean =
        prefs.getBoolean(calKey(repoId, calendarId, "silent"), false)

    fun calendarLeadTimes(repoId: String, calendarId: String): List<String>? {
        val s = prefs.getString(calKey(repoId, calendarId, "leadtimes"), null) ?: return null
        if (s.isEmpty()) return emptyList()
        return s.split(';').filter { it.isNotBlank() }
    }

    fun setCalendarEnabled(repoId: String, calendarId: String, value: Boolean) {
        prefs.edit().putBoolean(calKey(repoId, calendarId, "enabled"), value).apply()
        _state.value = loadAll()
    }

    fun setCalendarSilent(repoId: String, calendarId: String, value: Boolean) {
        prefs.edit().putBoolean(calKey(repoId, calendarId, "silent"), value).apply()
        _state.value = loadAll()
    }

    fun setCalendarLeadTimes(repoId: String, calendarId: String, values: List<String>?) {
        val editor = prefs.edit()
        if (values == null) editor.remove(calKey(repoId, calendarId, "leadtimes"))
        else editor.putString(calKey(repoId, calendarId, "leadtimes"), values.joinToString(";"))
        editor.apply()
        _state.value = loadAll()
    }

    // --- Phase S.4 — master briefings + per-template-category lead times -----

    fun isBriefingsEnabled(): Boolean = prefs.getBoolean(KEY_BRIEFINGS_MASTER, true)

    fun setBriefingsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BRIEFINGS_MASTER, enabled).apply()
        _state.value = loadAll()
    }

    fun categoryLeadTimes(category: String): String? =
        prefs.getString("category.$category.leadtimes", null)

    fun setCategoryLeadTimes(category: String, value: String?) {
        val editor = prefs.edit()
        val k = "category.$category.leadtimes"
        if (value.isNullOrBlank()) editor.remove(k) else editor.putString(k, value)
        editor.apply()
        _state.value = loadAll()
    }

    // --- Phase 2.1.F.2 — per-event mute -----------------------------------

    /**
     * Per-event mute toggle. When `true`, the receiver short-circuits the
     * notification post for the given `(repoId, eventId)` even though the
     * channel + calendar are still enabled.
     */
    fun isEventMuted(repoId: String, eventId: String): Boolean =
        prefs.getBoolean(eventKey(repoId, eventId, "muted"), false)

    fun setEventMuted(repoId: String, eventId: String, muted: Boolean) {
        val k = eventKey(repoId, eventId, "muted")
        val editor = prefs.edit()
        if (muted) editor.putBoolean(k, true) else editor.remove(k)
        editor.apply()
        _state.value = loadAll()
    }

    // --- Phase 2.1.F.4 — time-bounded group mute --------------------------

    /**
     * Persist a time-bounded mute. `untilEpochMs == null` clears the mute.
     * Scopes are keyed by [LogicalGroup.storageKey] so callers don't have
     * to think about how a group is serialised.
     */
    fun setGroupMute(scope: LogicalGroup, untilEpochMs: Long?) {
        val k = groupMuteKey(scope)
        val editor = prefs.edit()
        if (untilEpochMs == null || untilEpochMs <= 0L) editor.remove(k)
        else editor.putLong(k, untilEpochMs)
        editor.apply()
        _state.value = loadAll()
    }

    /** Return the absolute epoch-ms a group is muted until, or `null`. */
    fun groupMuteUntil(scope: LogicalGroup): Long? {
        val v = prefs.getLong(groupMuteKey(scope), -1L)
        return if (v <= 0L) null else v
    }

    /**
     * True iff [scope] is currently muted at [nowEpochMs]. Expired mutes
     * are treated as not-muted (the receiver may opt to clear them lazily,
     * but the read path doesn't mutate prefs).
     */
    fun isGroupMutedAt(scope: LogicalGroup, nowEpochMs: Long): Boolean {
        val until = groupMuteUntil(scope) ?: return false
        return until > nowEpochMs
    }

    // --- Phase XX.10 / AT-J.4 — global streak-count visibility toggle --------

    /** Default ON per AT-J.4. */
    fun isStreakCountsEnabled(): Boolean = prefs.getBoolean(KEY_STREAK_COUNTS, true)

    fun setStreakCountsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STREAK_COUNTS, enabled).apply()
        _state.value = loadAll()
    }

    private fun channelKey(channelId: String, suffix: String) = "channel.$channelId.$suffix"
    private fun calKey(repoId: String, calendarId: String, suffix: String) =
        "cal.$repoId.$calendarId.$suffix"
    private fun eventKey(repoId: String, eventId: String, suffix: String) =
        "event.$repoId.$eventId.$suffix"
    private fun groupMuteKey(scope: LogicalGroup): String = "mute.${scope.storageKey}"

    private fun loadAll(): Map<String, Any> = prefs.all.filterValues { it != null }
        .mapValues { it.value as Any }

    companion object {
        private const val PREFS_FILE = "notification_prefs_v1"
        private const val KEY_BRIEFINGS_MASTER = "briefings.master.enabled"
        private const val KEY_STREAK_COUNTS = "streak.counts.enabled"

        fun open(context: Context): NotificationPrefs {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return NotificationPrefs(prefs)
        }

        /** Test-only constructor. */
        internal fun openForTest(prefs: SharedPreferences): NotificationPrefs =
            NotificationPrefs(prefs)
    }
}
