package com.eight87.strictlykeptboy.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase F.3 — user-tweakable appearance state.
 *
 * `ThemeMode` is a display preference (NOT a secret), so it lives in plain
 * SharedPreferences per the brief. Density toggles spacing tokens via the
 * [LocalDensityScale] CompositionLocal.
 */
enum class ThemeMode { Light, Dark, Auto }

enum class DensityScale(val multiplier: Float) {
    Compact(0.75f),
    Comfortable(1.0f),
    Spacious(1.25f),
}

@Immutable
data class AppearanceState(
    val themeMode: ThemeMode = ThemeMode.Auto,
    val densityScale: DensityScale = DensityScale.Comfortable,
    val dynamicColor: Boolean = true,
)

/**
 * Multiplier carried via CompositionLocal so deep children can scale their
 * own padding/spacing without threading the value through every signature.
 * Use [scaledDp] for the common case.
 */
val LocalDensityScale = compositionLocalOf { DensityScale.Comfortable }

/** Apply the ambient density scale to a base dp spacing token. */
fun Dp.scaled(scale: DensityScale): Dp = (this.value * scale.multiplier).dp

/** Convenience for composables that want the scaled value at the current scale. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun scaledDp(base: Dp): Dp = base.scaled(LocalDensityScale.current)

/**
 * SharedPreferences-backed appearance store. Not encrypted — these are
 * display preferences, not secrets (per F.3).
 */
class AppearancePrefs internal constructor(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppearanceState> = _state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setDensity(scale: DensityScale) {
        prefs.edit().putString(KEY_DENSITY, scale.name).apply()
        _state.value = _state.value.copy(densityScale = scale)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _state.value = _state.value.copy(dynamicColor = enabled)
    }

    private fun load(): AppearanceState = AppearanceState(
        themeMode = prefs.getString(KEY_MODE, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.Auto,
        densityScale = prefs.getString(KEY_DENSITY, null)?.let { runCatching { DensityScale.valueOf(it) }.getOrNull() }
            ?: DensityScale.Comfortable,
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
    )

    companion object {
        private const val PREFS_FILE = "appearance_v1"
        private const val KEY_MODE = "themeMode"
        private const val KEY_DENSITY = "densityScale"
        private const val KEY_DYNAMIC = "dynamicColor"

        fun open(context: Context): AppearancePrefs =
            AppearancePrefs(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))

        internal fun openForTest(prefs: SharedPreferences) = AppearancePrefs(prefs)
    }
}
