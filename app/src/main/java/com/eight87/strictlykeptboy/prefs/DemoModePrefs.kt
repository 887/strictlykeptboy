package com.eight87.strictlykeptboy.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.eight87.strictlykeptboy.ui.wizard.LifestyleCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 2.15 — demo-mode prefs.
 *
 * Drives the demo-first onboarding flow:
 *   - `isDemoMode == true`  → app is showing a seeded demo repo
 *   - `selectedPerspective` → which [LifestyleCard] the seed was generated from
 *
 * First-launch routing checks `selectedPerspective == null` to know whether
 * to show the IntroWizardHost. Toggling demo off via Repositories sets
 * `isDemoMode = false` but keeps the perspective so re-enabling restores it.
 */
class DemoModePrefs internal constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<DemoState> = _state.asStateFlow()

    val current: DemoState get() = _state.value

    fun setPerspective(card: LifestyleCard) {
        prefs.edit().putString(KEY_PERSPECTIVE, card.name).putBoolean(KEY_ACTIVE, true).apply()
        _state.value = DemoState(isActive = true, perspective = card)
    }

    fun setActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_ACTIVE, active).apply()
        _state.value = _state.value.copy(isActive = active)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = DemoState()
    }

    private fun load(): DemoState {
        val name = prefs.getString(KEY_PERSPECTIVE, null)
        val card = name?.let { runCatching { LifestyleCard.valueOf(it) }.getOrNull() }
        return DemoState(
            isActive = prefs.getBoolean(KEY_ACTIVE, false),
            perspective = card,
        )
    }

    data class DemoState(
        val isActive: Boolean = false,
        val perspective: LifestyleCard? = null,
    )

    companion object {
        private const val PREFS_FILE = "demo_mode_v1"
        private const val KEY_ACTIVE = "demo.active"
        private const val KEY_PERSPECTIVE = "demo.perspective"

        fun open(context: Context): DemoModePrefs {
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
            return DemoModePrefs(prefs)
        }

        internal fun openForTest(prefs: SharedPreferences): DemoModePrefs =
            DemoModePrefs(prefs)
    }
}
