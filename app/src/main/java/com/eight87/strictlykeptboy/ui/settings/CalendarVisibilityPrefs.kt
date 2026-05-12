package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase S.5 / S.6 — visibility + priority editor for calendars + todolists.
 * Persisted as JSON because the keying is `<repoId>/<calendarId>` and the
 * priority is an ordered list rather than a single value.
 *
 * Same shape reused for calendars (S.5) and todolists (S.6) — the
 * [kind] discriminator picks which subkey the prefs file uses so a
 * single SharedPreferences file backs both without leakage.
 */
enum class ListKind { Calendars, Todolists }

@Serializable
data class VisibilityEntry(
    val id: String,
    val label: String,
    val visible: Boolean = true,
    val activeFromIso: String? = null,
    val activeUntilIso: String? = null,
)

@Serializable
data class VisibilityState(
    val ordered: List<VisibilityEntry> = emptyList(),
)

class CalendarVisibilityPrefs internal constructor(
    private val prefs: SharedPreferences,
    private val kind: ListKind,
    private val json: Json = DefaultJson,
) {
    private val key = "list.${kind.name}"
    private val _state = MutableStateFlow(load())
    val state: StateFlow<VisibilityState> = _state.asStateFlow()

    fun setEntries(entries: List<VisibilityEntry>) {
        val v = VisibilityState(entries)
        prefs.edit().putString(key, json.encodeToString(v)).apply()
        _state.value = v
    }

    fun setVisible(id: String, visible: Boolean) {
        val cur = _state.value.ordered.toMutableList()
        val idx = cur.indexOfFirst { it.id == id }
        if (idx < 0) {
            cur.add(VisibilityEntry(id = id, label = id, visible = visible))
        } else {
            cur[idx] = cur[idx].copy(visible = visible)
        }
        setEntries(cur)
    }

    fun moveUp(id: String) {
        val cur = _state.value.ordered.toMutableList()
        val idx = cur.indexOfFirst { it.id == id }
        if (idx > 0) {
            val tmp = cur[idx - 1]
            cur[idx - 1] = cur[idx]
            cur[idx] = tmp
            setEntries(cur)
        }
    }

    fun moveDown(id: String) {
        val cur = _state.value.ordered.toMutableList()
        val idx = cur.indexOfFirst { it.id == id }
        if (idx in 0 until cur.lastIndex) {
            val tmp = cur[idx + 1]
            cur[idx + 1] = cur[idx]
            cur[idx] = tmp
            setEntries(cur)
        }
    }

    private fun load(): VisibilityState {
        val raw = prefs.getString(key, null) ?: return VisibilityState()
        return runCatching { json.decodeFromString<VisibilityState>(raw) }.getOrDefault(VisibilityState())
    }

    companion object {
        private const val PREFS_FILE = "list_visibility_v1"
        private val DefaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun open(context: Context, kind: ListKind) = CalendarVisibilityPrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
            kind,
        )

        internal fun openForTest(prefs: SharedPreferences, kind: ListKind) =
            CalendarVisibilityPrefs(prefs, kind)
    }
}
