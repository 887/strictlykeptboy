package com.eight87.strictlykeptboy.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Phase F.3 — bat-themed dark fallback. Charcoal background, dim purple accent.
// When the device is API 31+ AND dynamicColor is on, we still prefer the
// system-wallpaper-derived scheme; this is the offline fallback.
//
// Round 2.16 follow-up — fill in surface-container + content tokens so the
// peek MiniPlayer (which renders on `surfaceContainerHigh`) stays in the
// dark palette instead of falling back to Material's light defaults.
private val BatDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF9E7BD8),       // dim purple
    onPrimary = Color(0xFF1A1024),
    primaryContainer = Color(0xFF3A2A5C),
    onPrimaryContainer = Color(0xFFE7DDFB),
    secondary = Color(0xFF8A7BAE),
    background = Color(0xFF121017),    // charcoal
    onBackground = Color(0xFFE7E0F4),
    surface = Color(0xFF121017),
    onSurface = Color(0xFFE7E0F4),
    surfaceVariant = Color(0xFF2A2533),
    onSurfaceVariant = Color(0xFFB8B0CC),
    surfaceContainerLowest = Color(0xFF0E0C13),
    surfaceContainerLow = Color(0xFF161420),
    surfaceContainer = Color(0xFF1C1A26),
    surfaceContainerHigh = Color(0xFF22202D),     // peek bar background
    surfaceContainerHighest = Color(0xFF2A2737),  // active queue row / pill
    tertiary = Color(0xFFC4A0FF),
    onTertiary = Color(0xFF1F0A3A),
    outline = Color(0xFF665E7A),
    outlineVariant = Color(0xFF3D3548),
    scrim = Color(0xFF000000),
)

private val BatLight: ColorScheme = expressiveLightColorScheme()

@Composable
fun StrictlyKeptBoyTheme(
    themeMode: ThemeMode = ThemeMode.Auto,
    densityScale: DensityScale = DensityScale.Comfortable,
    /** Legacy back-compat path. Prefer the [baseTheme] overload. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    StrictlyKeptBoyTheme(
        themeMode = themeMode,
        densityScale = densityScale,
        baseTheme = if (dynamicColor) BaseTheme.MaterialYou else BaseTheme.DefaultColors,
        // No-op pass-through — tintByRepoAvatar + customChromeTint are
        // surfaced for round-trip persistence today; actual chrome
        // overlay wiring is deferred (see comments below).
        tintByRepoAvatar = true,
        customChromeTint = 0L,
        content = content,
    )
}

/**
 * Theme entry-point with the full BaseTheme shape ported from tonearmboy.
 *
 *  - [BaseTheme.DefaultColors] → built-in BatDark / BatLight palette.
 *  - [BaseTheme.MaterialYou]   → wallpaper-derived dynamic palette on API 31+;
 *    falls back to BatDark/BatLight on older devices.
 *  - [BaseTheme.PureBlack]     → force background + surface to black on the
 *    dark scheme. Light scheme is unaffected (pure-black is an AMOLED dark
 *    affordance, not a light theme).
 *  - [BaseTheme.Custom]        → seed a deterministic light/dark scheme from
 *    the user-picked RGB.
 *
 * NO-OP follow-ups (round-trip via prefs, behaviour parked):
 *  - [tintByRepoAvatar] is read for API parity with tonearmboy's
 *    `albumArtTintEnabled`. The repo-avatar-dominant-color extractor isn't
 *    wired here yet; flipping this toggle persists but doesn't paint
 *    anything different until a follow-up phase wires it.
 *  - [customChromeTint] is read for the same reason. The chrome-overlay
 *    pipeline (top bar / FAB / rail accent override) ships in a later
 *    phase; today the seed colour participates as the Custom scheme's
 *    seed when [baseTheme] is [BaseTheme.Custom], but does NOT separately
 *    overlay chrome when the base theme is something else.
 */
@Composable
fun StrictlyKeptBoyTheme(
    themeMode: ThemeMode,
    densityScale: DensityScale,
    baseTheme: BaseTheme,
    @Suppress("UNUSED_PARAMETER") tintByRepoAvatar: Boolean,
    @Suppress("UNUSED_PARAMETER") customChromeTint: Long,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> isSystemInDarkTheme()
    }

    val colorScheme: ColorScheme = when (baseTheme) {
        BaseTheme.MaterialYou -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) BatDark else BatLight
            }
        }
        BaseTheme.DefaultColors -> if (darkTheme) BatDark else BatLight
        BaseTheme.PureBlack -> {
            val foundation = if (darkTheme) BatDark else BatLight
            if (darkTheme) {
                foundation.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0A0A0A),
                    surfaceContainer = Color(0xFF101010),
                    surfaceContainerHigh = Color(0xFF161616),
                    surfaceContainerHighest = Color(0xFF1C1C1C),
                )
            } else foundation
        }
        is BaseTheme.Custom -> deriveCustomScheme(baseTheme.seedRgb, darkTheme)
    }

    CompositionLocalProvider(LocalDensityScale provides densityScale) {
        MaterialExpressiveTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

/**
 * Deterministic light/dark scheme from a 24-bit RGB seed. Lightweight
 * port of tonearmboy's `deriveCustomScheme` — we don't pull in
 * `androidx.compose.material3.dynamiccolor` (not on classpath) and the
 * full Hct pipeline is overkill for this surface; instead derive a
 * sensible primary/secondary/tertiary from the seed and let M3
 * lightColorScheme / darkColorScheme fill the rest.
 */
private fun deriveCustomScheme(seedRgb: Long, dark: Boolean): ColorScheme {
    val primary = Color(0xFF000000L or (seedRgb and 0xFFFFFFL))
    val secondary = primary.shift(0.85f)
    val tertiary = primary.shift(0.70f)
    return if (dark) {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
        )
    }
}

/** Crude HSV-ish shift — scale RGB channels by `factor` toward black. */
private fun Color.shift(factor: Float): Color =
    Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
