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
 * Phase S.11 — Mode (HV-Q.1 / DDD.1).
 *
 * Tracks app-wide default mode (free vs strictly-kept), the dom-persona
 * pointer, and the dom cadence. Mode transitions to `free` go through a
 * 24h cooling-off CONFIRMATION (D.86) — that flow lives in the
 * composable; this store just exposes the current value + the typed-
 * confirmation gate.
 */
enum class AppMode { Free, StrictlyKept }
enum class DomCadence { Realtime, EndOfDay, Weekly }

/**
 * Six default dom personas per D.85 + a `custom-prompt` slot the user
 * can name themselves.
 */
@Serializable
sealed interface DomPersona {
    val id: String
    val label: String

    @Serializable
    data class Builtin(override val id: String, override val label: String) : DomPersona

    @Serializable
    data class Custom(override val id: String, override val label: String, val prompt: String = "") : DomPersona

    companion object {
        val defaults: List<DomPersona> = listOf(
            Builtin("stern-but-fair", "Stern but fair"),
            Builtin("playful-tease", "Playful tease"),
            Builtin("kinky-affectionate", "Kinky-affectionate"),
            Builtin("daddy-warmth", "Daddy warmth"),
            Builtin("bratty-switch-energy", "Bratty switch energy"),
            Builtin("clinical-protocol", "Clinical protocol"),
        )
    }
}

@Serializable
data class ModeState(
    val mode: AppMode = AppMode.Free,
    val cadence: DomCadence = DomCadence.EndOfDay,
    val personaId: String = "stern-but-fair",
    val customPersonas: List<DomPersona.Custom> = emptyList(),
)

class ModePrefs internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json = DefaultJson,
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ModeState> = _state.asStateFlow()

    fun setMode(mode: AppMode) = update { it.copy(mode = mode) }
    fun setCadence(cadence: DomCadence) = update { it.copy(cadence = cadence) }
    fun setPersonaId(id: String) = update { it.copy(personaId = id) }
    fun addCustomPersona(persona: DomPersona.Custom) =
        update { it.copy(customPersonas = it.customPersonas + persona) }

    private fun update(transform: (ModeState) -> ModeState) {
        val next = transform(_state.value)
        prefs.edit().putString(KEY_STATE, json.encodeToString(next)).apply()
        _state.value = next
    }

    private fun load(): ModeState {
        val raw = prefs.getString(KEY_STATE, null) ?: return ModeState()
        return runCatching { json.decodeFromString<ModeState>(raw) }.getOrDefault(ModeState())
    }

    fun availablePersonas(): List<DomPersona> = DomPersona.defaults + _state.value.customPersonas

    companion object {
        private const val PREFS_FILE = "mode_v1"
        private const val KEY_STATE = "state"

        /** D.86 — boy types this exact phrase to confirm leaving strictly-kept. */
        const val FREE_CONFIRMATION_PHRASE: String = "yes I want to leave"

        private val DefaultJson = Json {
            ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_t"
        }

        fun open(context: Context) = ModePrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        internal fun openForTest(prefs: SharedPreferences) = ModePrefs(prefs)
    }
}
