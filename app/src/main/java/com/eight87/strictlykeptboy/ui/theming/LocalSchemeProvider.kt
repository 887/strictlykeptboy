package com.eight87.strictlykeptboy.ui.theming

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Phase T.5 — per-pane color-scheme override.
 *
 * The top-level [com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme]
 * installs the app-wide M3E scheme. Per-repo panes can override that
 * scheme locally by wrapping their content in [PerRepoColorScope] with
 * a repo-derived seed color. Other repos continue rendering with the
 * system dynamic scheme.
 *
 * Read the active scheme via [LocalSchemeProvider.current] (or simply
 * `MaterialTheme.colorScheme` — the wrapper re-installs the override
 * into the M3E `CompositionLocalProvider`).
 */
val LocalSchemeProvider = compositionLocalOf<ColorScheme?> { null }

/**
 * Scope a sub-tree to a repo-specific M3E color scheme derived from
 * [seedArgb]. When [seedArgb] is null, the parent (app-wide) scheme
 * passes through untouched.
 */
@Composable
fun PerRepoColorScope(
    seedArgb: Int?,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = seedArgb?.let { schemeFromSeed(it, darkTheme) }
    if (scheme == null) {
        content()
    } else {
        // Re-install scheme into MaterialExpressiveTheme so child composables
        // pick it up via the usual `MaterialTheme.colorScheme` path.
        MaterialExpressiveTheme(colorScheme = scheme) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalSchemeProvider provides scheme,
                content = content,
            )
        }
    }
}

/**
 * Derive a full M3E [ColorScheme] from a single ARGB seed color.
 *
 * On API 31+, falls through to the system dynamic-color generator with
 * a tinted context (best fidelity to the device's wallpaper-aware
 * scheme math). On older API levels, falls back to a hand-rolled
 * primary-mapped scheme — every Color slot is filled so [MaterialTheme]
 * never sees a null channel.
 */
@Composable
private fun schemeFromSeed(argb: Int, darkTheme: Boolean): ColorScheme {
    val seed = Color(argb)
    // Pre-API-31: build a hand-rolled scheme keyed on the seed. The M3E
    // dynamic-color path needs a Context that's not trivially derivable
    // from a Color, so we avoid the dynamic*ColorScheme call entirely
    // here — keeps the function pure on the seed input.
    val ctx = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Use the *system* dynamic-color scheme as the base, then tint
        // primary + primaryContainer with the seed so the override is
        // visible against the dynamic background without re-deriving
        // every channel.
        val base = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        base.copy(
            primary = seed,
            primaryContainer = seed.copy(alpha = 0.35f).compositeOver(base.primaryContainer),
            tertiary = seed,
        )
    } else if (darkTheme) {
        darkColorScheme(primary = seed, primaryContainer = seed.copy(alpha = 0.35f))
    } else {
        expressiveLightColorScheme().copy(primary = seed, primaryContainer = seed.copy(alpha = 0.35f))
    }
}

private fun Color.compositeOver(other: Color): Color {
    val a = alpha + other.alpha * (1f - alpha)
    if (a <= 0f) return Color.Transparent
    val r = (red * alpha + other.red * other.alpha * (1f - alpha)) / a
    val g = (green * alpha + other.green * other.alpha * (1f - alpha)) / a
    val b = (blue * alpha + other.blue * other.alpha * (1f - alpha)) / a
    return Color(r, g, b, a)
}

/** Pure, test-friendly variant of [schemeFromSeed]: no system context. */
fun schemeFromSeedPure(argb: Int, darkTheme: Boolean): ColorScheme {
    val seed = Color(argb)
    return if (darkTheme) darkColorScheme(
        primary = seed,
        primaryContainer = seed.copy(alpha = 0.35f),
    ) else lightColorScheme(
        primary = seed,
        primaryContainer = seed.copy(alpha = 0.35f),
    )
}
