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
 * Phase S.8b — Identity (HV-R.3 / DDD.9).
 *
 * Mirrors the per-repo `identity.toml` schema in a settings-cached form
 * so the surface can render + edit + live-preview without touching the
 * Git layer on every keystroke. In `strictly-kept` mode, saving emits a
 * `reviews/<sha>/...` entry — that wiring is on the writer side and not
 * embedded here.
 */
enum class ToneRegister { Soft, Neutral, Formal, Stern, Playful }
enum class EmojiDensity { None, Sparse, Standard, Lush }

@Serializable
data class IdentityState(
    val praise: String = "good boy",
    val altTerms: List<String> = emptyList(),
    val pronouns: String = "he/him",
    val pronounsExtra: List<String> = emptyList(),
    val honorific: String = "",
    val tone: ToneRegister = ToneRegister.Neutral,
    val emoji: EmojiDensity = EmojiDensity.Standard,
)

class IdentityPrefs internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json = DefaultJson,
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<IdentityState> = _state.asStateFlow()

    fun update(transform: (IdentityState) -> IdentityState) {
        val next = transform(_state.value)
        prefs.edit().putString(KEY_STATE, json.encodeToString(next)).apply()
        _state.value = next
    }

    fun resetToDefaults() {
        prefs.edit().remove(KEY_STATE).apply()
        _state.value = IdentityState()
    }

    private fun load(): IdentityState {
        val raw = prefs.getString(KEY_STATE, null) ?: return IdentityState()
        return runCatching { json.decodeFromString<IdentityState>(raw) }.getOrDefault(IdentityState())
    }

    companion object {
        private const val PREFS_FILE = "identity_v1"
        private const val KEY_STATE = "state"
        private val DefaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun open(context: Context) = IdentityPrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        internal fun openForTest(prefs: SharedPreferences) = IdentityPrefs(prefs)
    }
}
