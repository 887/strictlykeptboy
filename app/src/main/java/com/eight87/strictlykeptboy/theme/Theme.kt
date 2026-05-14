package com.eight87.strictlykeptboy.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BatDark
        else -> BatLight
    }

    CompositionLocalProvider(LocalDensityScale provides densityScale) {
        MaterialExpressiveTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
