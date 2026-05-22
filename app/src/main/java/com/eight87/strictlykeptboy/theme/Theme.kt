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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Phase F.3 — bat-themed dark fallback. Charcoal background, dim purple accent.
// When the device is API 31+ AND dynamicColor is on, we still prefer the
// system-wallpaper-derived scheme; this is the offline fallback.
private val BatDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF9E7BD8),
    onPrimary = Color(0xFF1A1024),
    primaryContainer = Color(0xFF3A2A5C),
    onPrimaryContainer = Color(0xFFE7DDFB),
    secondary = Color(0xFF8A7BAE),
    background = Color(0xFF121017),
    onBackground = Color(0xFFE7E0F4),
    surface = Color(0xFF121017),
    onSurface = Color(0xFFE7E0F4),
    surfaceVariant = Color(0xFF2A2533),
    onSurfaceVariant = Color(0xFFB8B0CC),
    surfaceContainerLowest = Color(0xFF0E0C13),
    surfaceContainerLow = Color(0xFF161420),
    surfaceContainer = Color(0xFF1C1A26),
    surfaceContainerHigh = Color(0xFF22202D),
    surfaceContainerHighest = Color(0xFF2A2737),
    tertiary = Color(0xFFC4A0FF),
    onTertiary = Color(0xFF1F0A3A),
    outline = Color(0xFF665E7A),
    outlineVariant = Color(0xFF3D3548),
    scrim = Color(0xFF000000),
)

private val BatLight: ColorScheme = expressiveLightColorScheme()

/**
 * Avatar-derived tint for the *active* repo, published by MainActivity so the
 * theme can blend it into chrome surfaces when `tintByRepoAvatar` is on. Stored
 * as a 24-bit RGB long; 0L = unset.
 */
val LocalRepoAvatarTint = compositionLocalOf<Long> { 0L }

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
 *    dark scheme. Light scheme is unaffected.
 *  - [BaseTheme.Custom]        → seed a coherent HSL-derived light/dark scheme
 *    from the user-picked RGB.
 *
 * Tint precedence (highest wins):
 *  1. [customChromeTint] when non-zero — explicit "I want this colour".
 *  2. The active repo's avatar tint (published via [LocalRepoAvatarTint])
 *     when [tintByRepoAvatar] is true.
 *  3. No tint — chrome paints with the base scheme.
 *
 * The chosen tint is blended into the surface tier ladder via [blendSurface]
 * (40 % toward the tint), so chrome reads as *tinted by* the source, not as
 * the source.
 */
@Composable
fun StrictlyKeptBoyTheme(
    themeMode: ThemeMode,
    densityScale: DensityScale,
    baseTheme: BaseTheme,
    tintByRepoAvatar: Boolean,
    customChromeTint: Long,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> isSystemInDarkTheme()
    }

    val baseScheme: ColorScheme = when (baseTheme) {
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

    val avatarTint = LocalRepoAvatarTint.current
    val tintRgb: Long? = when {
        customChromeTint != 0L -> customChromeTint
        tintByRepoAvatar && avatarTint != 0L -> avatarTint
        else -> null
    }
    val tint: Color? = tintRgb?.let { Color(0xFF000000L or (it and 0xFFFFFFL)) }

    // NB: do NOT blend surfaceContainerHigh / surfaceContainerHighest. M3
    // AlertDialog + ModalBottomSheet default to surfaceContainerHigh for
    // their container; tinting those toward the chrome tint makes the dialog
    // visually merge with the tinted scaffold behind it (reads as
    // "translucent" even though alpha is 1.0). Keep them on the base scheme
    // so modals stay distinct.
    val tintedScheme = if (tint == null) baseScheme else baseScheme.copy(
        surface = blendSurface(baseScheme.surface, tint),
        surfaceVariant = blendSurface(baseScheme.surfaceVariant, tint),
        background = blendSurface(baseScheme.background, tint),
        surfaceContainerLowest = blendSurface(baseScheme.surfaceContainerLowest, tint),
        surfaceContainerLow = blendSurface(baseScheme.surfaceContainerLow, tint),
        surfaceContainer = blendSurface(baseScheme.surfaceContainer, tint),
        secondaryContainer = blendSurface(baseScheme.secondaryContainer, tint),
    )

    CompositionLocalProvider(LocalDensityScale provides densityScale) {
        MaterialExpressiveTheme(colorScheme = tintedScheme, typography = Typography, content = content)
    }
}

/**
 * D.25.1 — derive a Material 3 [ColorScheme] from a 24-bit RGB seed.
 *
 * Builds primary / secondary / tertiary tonal anchors by shifting the seed's
 * hue (secondary = +30°, tertiary = +60°) and lightness, then plugs them into
 * the canonical [lightColorScheme] / [darkColorScheme] factories. Sidesteps
 * Material 3's `dynamicColorScheme(seed, isDark)` (added in 1.4) so the build
 * works regardless of the active Material 3 version.
 */
internal fun deriveCustomScheme(seedRgb: Long, dark: Boolean): ColorScheme {
    val primary = colorFromRgbLong(seedRgb)
    val (h, s, _) = rgbToHslTriple(primary)
    val secondary = hslColor(((h + 30f) % 360f), (s * 0.7f).coerceIn(0f, 1f), if (dark) 0.7f else 0.45f)
    val tertiary = hslColor(((h + 60f) % 360f), (s * 0.6f).coerceIn(0f, 1f), if (dark) 0.7f else 0.5f)
    val primaryDark = hslColor(h, s, if (dark) 0.7f else 0.4f)
    val onPrimary = if (luminance(primaryDark) > 0.5f) Color.Black else Color.White
    return if (dark) {
        darkColorScheme(
            primary = primaryDark,
            secondary = secondary,
            tertiary = tertiary,
            onPrimary = onPrimary,
        )
    } else {
        lightColorScheme(
            primary = primaryDark,
            secondary = secondary,
            tertiary = tertiary,
            onPrimary = onPrimary,
        )
    }
}

private fun colorFromRgbLong(rgb: Long): Color {
    val r = ((rgb shr 16) and 0xFFL).toInt()
    val g = ((rgb shr 8) and 0xFFL).toInt()
    val b = (rgb and 0xFFL).toInt()
    return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
}

/** Returns (hue 0..360, saturation 0..1, lightness 0..1). */
internal fun rgbToHslTriple(c: Color): Triple<Float, Float, Float> {
    val r = c.red; val g = c.green; val b = c.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val delta = max - min
    if (delta == 0f) return Triple(0f, 0f, l)
    val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val h = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Triple(h, s, l)
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs((hp % 2f) - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(red = (r1 + m).coerceIn(0f, 1f), green = (g1 + m).coerceIn(0f, 1f), blue = (b1 + m).coerceIn(0f, 1f), alpha = 1f)
}

internal fun luminance(c: Color): Float =
    0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

/**
 * Blend [base] toward [tint] by [fraction] (0..1). 40 % is the canonical
 * value tonearmboy uses — chrome stays *chrome*, just tinted.
 */
internal fun blendSurface(base: Color, tint: Color?, fraction: Float = 0.4f): Color {
    if (tint == null) return base
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - f) + tint.red * f,
        green = base.green * (1f - f) + tint.green * f,
        blue = base.blue * (1f - f) + tint.blue * f,
        alpha = base.alpha,
    )
}
