package com.eight87.strictlykeptboy.ui.theming

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase T.3 — per-calendar icon + color seed persistence.
 *
 * Keyed by `<repoId>/<calendarId>` strings; persisted as JSON. Separate
 * from [com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs]
 * (visibility / priority) so the two concerns can evolve independently
 * (Single-Responsibility).
 */
@Serializable
data class CalendarThemeEntry(
    val key: String,
    /** ARGB int from `Color.toArgb()`; null means inherit from M3E theme. */
    val colorSeedArgb: Int? = null,
    /** Optional emoji glyph icon. Future: extend with a sealed type when
     *  Photo/AutoInitials make sense for calendars. */
    val iconEmoji: String? = null,
)

@Serializable
data class CalendarThemeState(
    val entries: Map<String, CalendarThemeEntry> = emptyMap(),
)

class CalendarThemePrefs internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<CalendarThemeState> = _state.asStateFlow()

    fun setSeed(key: String, argb: Int?) = mutate(key) { it.copy(colorSeedArgb = argb) }
    fun setIcon(key: String, glyph: String?) = mutate(key) { it.copy(iconEmoji = glyph?.takeIf { g -> g.isNotEmpty() }) }

    fun entry(key: String): CalendarThemeEntry? = _state.value.entries[key]

    private fun mutate(key: String, op: (CalendarThemeEntry) -> CalendarThemeEntry) {
        val cur = _state.value.entries[key] ?: CalendarThemeEntry(key = key)
        val updated = op(cur)
        val next = _state.value.copy(entries = _state.value.entries + (key to updated))
        prefs.edit().putString(STATE_KEY, json.encodeToString(next)).apply()
        _state.value = next
    }

    private fun load(): CalendarThemeState =
        prefs.getString(STATE_KEY, null)?.let {
            runCatching { json.decodeFromString<CalendarThemeState>(it) }.getOrNull()
        } ?: CalendarThemeState()

    companion object {
        private const val PREFS_FILE = "calendar_theme_v1"
        private const val STATE_KEY = "state"
        fun open(context: Context): CalendarThemePrefs =
            CalendarThemePrefs(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))
        internal fun openForTest(prefs: SharedPreferences) = CalendarThemePrefs(prefs)
    }
}
