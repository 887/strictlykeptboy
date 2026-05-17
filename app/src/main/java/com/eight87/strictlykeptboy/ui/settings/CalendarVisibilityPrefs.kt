package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phase S.5 / S.6 + Round 2.1.B.3 — visibility + priority editor for
 * calendars + todolists.
 *
 * Persisted as JSON because the keying is `<repoId>/<calendarId>` and the
 * priority is an ordered list rather than a single value. Same shape is
 * reused for calendars (S.5) and todolists (S.6) — the [kind] discriminator
 * picks which subkey the prefs file uses so a single SharedPreferences
 * file backs both without leakage.
 *
 * **Round 2.1.B.3 migration (D-2.1.c):**
 *  - Adds `repoId` qualifier to [VisibilityEntry] so two repos can hold a
 *    calendar with the same id (e.g. both auto-generated `cal-briefings`)
 *    without colliding. Legacy entries (`repoId` absent) decode as
 *    `repoId = ""`; the matcher falls back to id-only when `repoId` is
 *    blank, preserving back-compat.
 *  - **Drops `activeFromIso` / `activeUntilIso`** — those move to
 *    `calendar.toml` (via [CalendarActivityConfig]). Prefs now holds
 *    phone-local on/off + priority ordering only. Legacy entries
 *    silently ignore the dropped fields when decoding.
 */
enum class ListKind { Calendars, Todolists }

/** Round 2.21 Phase D.2 — per-overlay zoom range (also re-exposed on the companion). */
internal const val ZOOM_MIN = 1
internal const val ZOOM_MAX = 4
internal const val ZOOM_DEFAULT = 2

@Serializable
data class VisibilityEntry(
    val id: String,
    val label: String,
    val visible: Boolean = true,
    val repoId: String = "",
    /**
     * Round 2.21 Phase D.2 — per-overlay zoom level ∈ {1,2,3,4}.
     * Default 2 ≈ 80dp/h. The effective day-view grid zoom is the
     * max across all visible overlays so the densest overlay's
     * readability wins (D-2.21.g).
     */
    val zoom: Int = ZOOM_DEFAULT,
)

@Serializable
data class VisibilityState(
    val ordered: List<VisibilityEntry> = emptyList(),
    /**
     * Round 2.23 Phase C (D-2.23.a) — global zoom override for the Day /
     * 3-day timeline. `null` ⇒ legacy max-of-visible-overlays behaviour
     * (per D-2.21.g); explicit Int ∈ {1..4} forces that zoom regardless
     * of which overlays are visible. Wired by `ZoomLevelRow`.
     */
    val globalZoomOverride: Int? = null,
    /**
     * Round 2.24 Phase C (D-2.24.c) — display-tz override for the Schedule
     * view. `null` ⇒ follow `ZoneId.systemDefault()`; any non-null value is
     * a `ZoneId.of(...)`-parseable string (e.g. `"America/New_York"`,
     * `"Europe/Berlin"`). Wired by `DisplayTzChip`.
     */
    val displayTzId: String? = null,
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
        val v = _state.value.copy(ordered = entries)
        prefs.edit().putString(key, json.encodeToString(v)).apply()
        _state.value = v
    }

    /**
     * Round 2.23 Phase C (D-2.23.a) — set the global zoom override. Pass
     * `null` to revert to max-of-visible-overlays (auto). Int values clamp
     * to [ZOOM_MIN..ZOOM_MAX].
     */
    fun setGlobalZoomOverride(override: Int?) {
        val clamped = override?.coerceIn(ZOOM_MIN, ZOOM_MAX)
        val v = _state.value.copy(globalZoomOverride = clamped)
        prefs.edit().putString(key, json.encodeToString(v)).apply()
        _state.value = v
    }

    /** Round 2.23 Phase C — current global zoom override, or null. */
    fun globalZoomOverride(): Int? = _state.value.globalZoomOverride

    /**
     * Round 2.24 Phase C (D-2.24.c) — set the display-tz override. Pass
     * `null` to revert to `ZoneId.systemDefault()`. Blank strings are
     * normalised to `null` so an empty `TextField` resets cleanly.
     */
    fun setDisplayTzId(zoneId: String?) {
        val normalized = zoneId?.takeIf { it.isNotBlank() }
        val v = _state.value.copy(displayTzId = normalized)
        prefs.edit().putString(key, json.encodeToString(v)).apply()
        _state.value = v
    }

    /** Round 2.24 Phase C — current display-tz override, or null. */
    fun displayTzId(): String? = _state.value.displayTzId

    /**
     * Toggle visibility for an entry matched by (id, repoId). Legacy
     * call sites that don't know the repoId pass blank — matched on
     * `id` alone (back-compat).
     */
    fun setVisible(id: String, visible: Boolean, repoId: String = "") {
        val cur = _state.value.ordered.toMutableList()
        val idx = matchIndex(cur, id, repoId)
        if (idx < 0) {
            cur.add(VisibilityEntry(id = id, label = id, visible = visible, repoId = repoId))
        } else {
            cur[idx] = cur[idx].copy(visible = visible)
        }
        setEntries(cur)
    }

    /**
     * Round 2.21 Phase D.2 — set per-overlay zoom. Clamps to `1..4` and
     * synthesizes an entry (default visible) if the calendar hasn't been
     * touched before.
     */
    fun setZoom(id: String, zoom: Int, repoId: String = "") {
        val clamped = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
        val cur = _state.value.ordered.toMutableList()
        val idx = matchIndex(cur, id, repoId)
        if (idx < 0) {
            cur.add(
                VisibilityEntry(id = id, label = id, visible = true, repoId = repoId, zoom = clamped),
            )
        } else {
            cur[idx] = cur[idx].copy(zoom = clamped)
        }
        setEntries(cur)
    }

    /** Round 2.21 Phase D.2 — per-overlay zoom lookup; default [ZOOM_DEFAULT]. */
    fun zoomOf(id: String, repoId: String = ""): Int {
        val cur = _state.value.ordered
        val idx = matchIndex(cur, id, repoId)
        return if (idx < 0) ZOOM_DEFAULT else cur[idx].zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
    }

    /** Lookup-only — returns `true` (default visible) when not in the store. */
    fun isVisible(id: String, repoId: String = ""): Boolean {
        val cur = _state.value.ordered
        val idx = matchIndex(cur, id, repoId)
        return if (idx < 0) true else cur[idx].visible
    }

    fun moveUp(id: String, repoId: String = "") {
        val cur = _state.value.ordered.toMutableList()
        val idx = matchIndex(cur, id, repoId)
        if (idx > 0) {
            val tmp = cur[idx - 1]
            cur[idx - 1] = cur[idx]
            cur[idx] = tmp
            setEntries(cur)
        }
    }

    fun moveDown(id: String, repoId: String = "") {
        val cur = _state.value.ordered.toMutableList()
        val idx = matchIndex(cur, id, repoId)
        if (idx in 0 until cur.lastIndex) {
            val tmp = cur[idx + 1]
            cur[idx + 1] = cur[idx]
            cur[idx] = tmp
            setEntries(cur)
        }
    }

    private fun matchIndex(list: List<VisibilityEntry>, id: String, repoId: String): Int {
        // Strict match first: same (repoId, id).
        val strict = list.indexOfFirst { it.id == id && it.repoId == repoId }
        if (strict >= 0) return strict
        // Back-compat fallback: id-only when repoId is blank or entry's repoId is blank.
        if (repoId.isBlank()) return list.indexOfFirst { it.id == id }
        return list.indexOfFirst { it.id == id && it.repoId.isBlank() }
    }

    private fun load(): VisibilityState {
        val raw = prefs.getString(key, null) ?: return VisibilityState()
        // Tolerant migration path: parse to JsonObject, project each entry
        // through the legacy → current shape (drop activeFromIso /
        // activeUntilIso; default repoId = "").
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val ordered = root["ordered"]?.jsonArray.orEmpty().map { el ->
                val obj = el.jsonObject
                val rawZoom = obj["zoom"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: ZOOM_DEFAULT
                VisibilityEntry(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    label = obj["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    visible = obj["visible"]?.jsonPrimitive?.boolean ?: true,
                    repoId = obj["repoId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    zoom = rawZoom.coerceIn(ZOOM_MIN, ZOOM_MAX),
                )
            }
            val overrideRaw = root["globalZoomOverride"]?.jsonPrimitive?.contentOrNull
            val override = overrideRaw?.toIntOrNull()?.coerceIn(ZOOM_MIN, ZOOM_MAX)
            val displayTzRaw = root["displayTzId"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            VisibilityState(ordered, override, displayTzRaw)
        }.getOrDefault(VisibilityState())
    }

    companion object {
        /** Round 2.21 Phase D.2 — zoom range re-exposed for public callers. */
        const val ZOOM_MIN = com.eight87.strictlykeptboy.ui.settings.ZOOM_MIN
        const val ZOOM_MAX = com.eight87.strictlykeptboy.ui.settings.ZOOM_MAX
        const val ZOOM_DEFAULT = com.eight87.strictlykeptboy.ui.settings.ZOOM_DEFAULT
        private const val PREFS_FILE = "list_visibility_v1"
        private val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun open(context: Context, kind: ListKind) = CalendarVisibilityPrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
            kind,
        )

        internal fun openForTest(prefs: SharedPreferences, kind: ListKind) =
            CalendarVisibilityPrefs(prefs, kind)
    }
}

/** Empty list import to satisfy List<JsonObject>?.orEmpty(). */
private fun List<kotlinx.serialization.json.JsonElement>?.orEmpty(): List<kotlinx.serialization.json.JsonElement> =
    this ?: emptyList()
