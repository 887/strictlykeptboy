package com.eight87.strictlykeptboy.system

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Round 2.18.A.14 — per-(accountType, accountName, calendarId) overrides
 * for system (CalendarContract-backed) calendars.
 *
 * What the user can override (mirrors the file-backed `calendar.toml`
 * overlay vocabulary):
 *  - [SystemCalendarOverride.activeToggle] — turn this calendar off
 *    without touching the OS-level visibility flag.
 *  - [SystemCalendarOverride.priority] — same 1..1000 priority space
 *    as `CalendarMeta.priority`.
 *  - [SystemCalendarOverride.supersedes] — calendar refs this calendar
 *    suppresses while active. Empty = no supersedence.
 *
 * Storage: JSON-serialized map under a single SharedPreferences key.
 * EncryptedSharedPreferences for parity with other skb stores; the
 * payload itself isn't secret, but the file lives alongside the rest
 * of skb's prefs (and the encryption cost is negligible).
 *
 * Not in this phase:
 *  - User-facing UI (Phase B + C).
 *  - Active-windows / active-hours overrides (deferred; not exposed by
 *    CalendarContract per-calendar so they'd be skb-only state).
 */
class SystemCalendarPrefsStore internal constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<String, SystemCalendarOverride>> = _state.asStateFlow()

    /**
     * Round 2.18.B.2 — global feature flags. Top-level "Show system
     * calendars" toggle (off by default), the secondary "Allow editing
     * system calendars" toggle (gated on the top-level), and the
     * one-shot first-run nudge tracker for B.6.
     *
     * `visibleByDefault = true` means new system calendars are visible
     * unless the per-calendar `visible` override (B.5) says otherwise.
     */
    private val _global = MutableStateFlow(loadGlobal())
    val globalState: StateFlow<SystemCalendarGlobalPrefs> = _global.asStateFlow()

    /** Look up the override for `(accountType, accountName, calendarId)`, if any. */
    fun get(accountType: String, accountName: String, calendarId: Long): SystemCalendarOverride? =
        _state.value[keyFor(accountType, accountName, calendarId)]

    fun set(
        accountType: String,
        accountName: String,
        calendarId: Long,
        override: SystemCalendarOverride,
    ) {
        val k = keyFor(accountType, accountName, calendarId)
        val next = _state.value.toMutableMap().apply { put(k, override) }.toMap()
        persist(next)
    }

    fun clear(accountType: String, accountName: String, calendarId: Long) {
        val k = keyFor(accountType, accountName, calendarId)
        if (k !in _state.value) return
        val next = _state.value.toMutableMap().apply { remove(k) }.toMap()
        persist(next)
    }

    fun clearAll() {
        if (_state.value.isEmpty()) return
        persist(emptyMap())
    }

    /** Round 2.18.B.5 — convenience to flip just the visibility flag. */
    fun setVisible(accountType: String, accountName: String, calendarId: Long, visible: Boolean) {
        val k = keyFor(accountType, accountName, calendarId)
        val prev = _state.value[k] ?: SystemCalendarOverride()
        val next = _state.value.toMutableMap().apply { put(k, prev.copy(visible = visible)) }.toMap()
        persist(next)
    }

    /** Round 2.18.B.2 — top-level toggle. */
    fun setShowSystemCalendars(value: Boolean) = mutateGlobal { it.copy(showSystemCalendars = value) }

    /** Round 2.18.B.2 — gates WRITE_CALENDAR request + future write path. */
    fun setAllowEditing(value: Boolean) = mutateGlobal { it.copy(allowEditing = value) }

    /** Round 2.18.B.6 — one-shot "new accounts" snackbar tracker. */
    fun markAccountChangeNudgeShown() = mutateGlobal { it.copy(accountChangeNudgeShown = true) }

    /**
     * Round 2.18.G.6 — top-level "Make skb visible to other Android apps"
     * toggle. When `true`, [com.eight87.strictlykeptboy.system.SkbAccountManager]
     * creates one local AccountManager account per skb repo and the sync
     * adapter publishes events into `CalendarContract`.
     */
    fun setPublishToOs(value: Boolean) = mutateGlobal { it.copy(publishToOs = value) }

    /**
     * Round 2.18 Phase I — one-shot tracker for the wizard's
     * "Make skb your default calendar app?" card. Once set the wizard
     * never shows the card again, regardless of whether the user
     * actually picked skb in the chooser.
     */
    fun markDefaultCalendarOnboardingShown() =
        mutateGlobal { it.copy(defaultCalendarOnboardingShown = true) }

    // ----------------------------------------------------------------
    // Round 2.18.G.8 — Calendar-row idempotency map.
    //
    // Keyed by `<repoId>:<skbCalendarId>` → `CalendarContract.Calendars._ID`.
    // The sync adapter consults this before inserting a fresh Calendars
    // row; on first sync it inserts then writes the resulting ID here,
    // subsequent syncs short-circuit on lookup.
    // ----------------------------------------------------------------

    fun calendarRowId(repoId: String, skbCalendarId: String): Long? {
        val raw = prefs.getString(KEY_CAL_ROW_IDS, null) ?: return null
        val map = runCatching { JSON.decodeFromString(longMapSerializer, raw) }.getOrDefault(emptyMap())
        return map["$repoId:$skbCalendarId"]
    }

    fun setCalendarRowId(repoId: String, skbCalendarId: String, rowId: Long) {
        val raw = prefs.getString(KEY_CAL_ROW_IDS, null)
        val current = raw?.let {
            runCatching { JSON.decodeFromString(longMapSerializer, it) }.getOrDefault(emptyMap())
        } ?: emptyMap()
        val next = current.toMutableMap().apply { put("$repoId:$skbCalendarId", rowId) }
        prefs.edit().putString(KEY_CAL_ROW_IDS, JSON.encodeToString(longMapSerializer, next)).apply()
    }

    fun clearCalendarRowIdsFor(repoId: String) {
        val raw = prefs.getString(KEY_CAL_ROW_IDS, null) ?: return
        val current = runCatching { JSON.decodeFromString(longMapSerializer, raw) }.getOrDefault(emptyMap())
        val next = current.filterKeys { !it.startsWith("$repoId:") }
        prefs.edit().putString(KEY_CAL_ROW_IDS, JSON.encodeToString(longMapSerializer, next)).apply()
    }

    private fun mutateGlobal(transform: (SystemCalendarGlobalPrefs) -> SystemCalendarGlobalPrefs) {
        val next = transform(_global.value)
        prefs.edit().putString(KEY_GLOBAL, JSON.encodeToString(globalSerializer, next)).apply()
        _global.value = next
    }

    private fun persist(next: Map<String, SystemCalendarOverride>) {
        prefs.edit().putString(KEY_PAYLOAD, JSON.encodeToString(serializer, next)).apply()
        _state.value = next
    }

    private fun load(): Map<String, SystemCalendarOverride> {
        val raw = prefs.getString(KEY_PAYLOAD, null) ?: return emptyMap()
        return runCatching { JSON.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private fun loadGlobal(): SystemCalendarGlobalPrefs {
        val raw = prefs.getString(KEY_GLOBAL, null) ?: return SystemCalendarGlobalPrefs()
        return runCatching { JSON.decodeFromString(globalSerializer, raw) }
            .getOrDefault(SystemCalendarGlobalPrefs())
    }

    companion object {
        private const val PREFS_FILE = "system_calendar_prefs_v1"
        private const val KEY_PAYLOAD = "overrides.json"
        // Round 2.18.B.2 — global flags blob lives alongside the
        // per-calendar overrides map. Single JSON string keeps schema
        // migrations simple.
        private const val KEY_GLOBAL = "global.json"
        // Round 2.18.G.8 — calendar-row idempotency map (separate key so
        // its serializer doesn't bloat the override decoder).
        private const val KEY_CAL_ROW_IDS = "calendar_row_ids.json"

        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        private val serializer = MapSerializer(String.serializer(), SystemCalendarOverride.serializer())
        private val globalSerializer = SystemCalendarGlobalPrefs.serializer()
        private val longMapSerializer = MapSerializer(String.serializer(), Long.serializer())

        fun keyFor(accountType: String, accountName: String, calendarId: Long): String =
            "$accountType $accountName $calendarId"

        fun open(context: Context): SystemCalendarPrefsStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SystemCalendarPrefsStore(prefs)
        }

        /** Test injection — pass any SharedPreferences (e.g. Robolectric default). */
        fun openForTest(prefs: SharedPreferences): SystemCalendarPrefsStore =
            SystemCalendarPrefsStore(prefs)
    }
}

/**
 * Round 2.18.A.14 — per-system-calendar user override.
 *
 * Defaults make this a no-op: `activeToggle = true`, `priority = null`
 * (fall through to whatever the bridge synthesized), `supersedes = []`.
 */
@Serializable
data class SystemCalendarOverride(
    val activeToggle: Boolean = true,
    val priority: Int? = null,
    val supersedes: List<String> = emptyList(),
    /**
     * Round 2.18.B.5 — per-calendar visibility toggle. Default `true`
     * (new calendars are visible once the global toggle is on). `false`
     * filters this calendar out of `CalendarMeta` emission, hiding it
     * from chip strip / agenda / detail surfaces. Hidden ≠ unsubscribed:
     * the calendar still exists in the underlying `SystemCalendar`
     * flow, the Settings list still shows it, the OS still syncs it.
     */
    val visible: Boolean = true,
)

/**
 * Round 2.18.B.2 — global (not per-calendar) feature flags. Stored as
 * a single JSON blob alongside the override map.
 */
@Serializable
data class SystemCalendarGlobalPrefs(
    /** Top-level "Show system calendars in strictlykeptboy" toggle (off by default). */
    val showSystemCalendars: Boolean = false,
    /**
     * Secondary toggle, gated on [showSystemCalendars]. Gates the
     * WRITE_CALENDAR runtime permission request + the future Phase D
     * CalendarContract write path.
     */
    val allowEditing: Boolean = false,
    /** Round 2.18.B.6 — first-run "new accounts detected" snackbar tracker. */
    val accountChangeNudgeShown: Boolean = false,
    /**
     * Round 2.18.G.6 — publish skb repos as Android accounts + run the
     * sync adapter that mirrors skb events into `CalendarContract`.
     * Default `false` so a fresh install doesn't pollute the OS account
     * list before the user opts in.
     */
    val publishToOs: Boolean = false,
    /**
     * Round 2.18 Phase I — one-shot tracker for the intro-wizard
     * "Make skb your default calendar app?" card. Default `false` so a
     * fresh install shows the card once; flipped `true` on first arrival
     * or skip. Never reset.
     */
    val defaultCalendarOnboardingShown: Boolean = false,
)
