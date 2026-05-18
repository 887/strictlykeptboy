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
 *
 * Look-and-feel parity port (matches tonearmboy's `ThemeSettings`): adds
 * [BaseTheme], [AppearanceState.tintByRepoAvatar] (sister app: tint-by-
 * album-art), and [AppearanceState.customChromeTint] alongside the
 * original [ThemeMode] / [DensityScale]. The legacy `dynamicColor`
 * boolean is now a derived projection of [BaseTheme.MaterialYou] so
 * existing call-sites keep working during the transition.
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
    val baseTheme: BaseTheme = BaseTheme.Default,
    val tintByRepoAvatar: Boolean = true,
    /** 24-bit `0xRRGGBB`. 0L = unset (fall back to repo-avatar tint). */
    val customChromeTint: Long = 0L,
) {
    /**
     * Back-compat derivation. Old call-sites read `dynamicColor` as a
     * Boolean; that meaning is now expressed via [BaseTheme.MaterialYou].
     * Keep this projection so we don't have to touch every reader in one
     * pass.
     */
    val dynamicColor: Boolean get() = baseTheme is BaseTheme.MaterialYou
}

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

    fun setBaseTheme(base: BaseTheme) {
        prefs.edit().putString(KEY_BASE_THEME, BaseTheme.toStored(base)).apply()
        _state.value = _state.value.copy(baseTheme = base)
    }

    /**
     * Back-compat shim. New code should call [setBaseTheme] directly;
     * mapping kept so old toggles don't break the build.
     */
    fun setDynamicColor(enabled: Boolean) {
        setBaseTheme(if (enabled) BaseTheme.MaterialYou else BaseTheme.DefaultColors)
    }

    fun setTintByRepoAvatar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TINT_REPO, enabled).apply()
        _state.value = _state.value.copy(tintByRepoAvatar = enabled)
    }

    fun setCustomChromeTint(rgb: Long) {
        prefs.edit().putLong(KEY_CHROME_TINT, rgb).apply()
        _state.value = _state.value.copy(customChromeTint = rgb)
    }

    private fun load(): AppearanceState {
        val mode = prefs.getString(KEY_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Auto
        val density = prefs.getString(KEY_DENSITY, null)
            ?.let { runCatching { DensityScale.valueOf(it) }.getOrNull() }
            ?: DensityScale.Comfortable
        // Migration: if the legacy KEY_DYNAMIC boolean is present and KEY_BASE_THEME
        // is not, project the boolean into the new sealed type.
        val baseTheme: BaseTheme = prefs.getString(KEY_BASE_THEME, null)
            ?.let { BaseTheme.fromStored(it) }
            ?: run {
                val legacy = prefs.getBoolean(KEY_DYNAMIC, true)
                if (legacy) BaseTheme.MaterialYou else BaseTheme.DefaultColors
            }
        val tintRepo = prefs.getBoolean(KEY_TINT_REPO, true)
        val chromeTint = prefs.getLong(KEY_CHROME_TINT, 0L)
        return AppearanceState(
            themeMode = mode,
            densityScale = density,
            baseTheme = baseTheme,
            tintByRepoAvatar = tintRepo,
            customChromeTint = chromeTint,
        )
    }

    companion object {
        private const val PREFS_FILE = "appearance_v1"
        private const val KEY_MODE = "themeMode"
        private const val KEY_DENSITY = "densityScale"

        // Legacy boolean — kept readable for migration; new writes go to KEY_BASE_THEME.
        private const val KEY_DYNAMIC = "dynamicColor"
        private const val KEY_BASE_THEME = "baseTheme"
        private const val KEY_TINT_REPO = "tintByRepoAvatar"
        private const val KEY_CHROME_TINT = "customChromeTint"

        fun open(context: Context): AppearancePrefs =
            AppearancePrefs(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))

        internal fun openForTest(prefs: SharedPreferences) = AppearancePrefs(prefs)
    }
}
