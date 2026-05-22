package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive colourful circular row-icon avatar — shutterboy parity.
 *
 * A 40-dp filled circle in [accent.container] with a 24-dp filled glyph
 * tinted [accent.onContainer]. Used for per-row category identity in
 * the Settings overlay and About page (mirrors shutterboy's
 * `CategoryAvatar` + `CategoryAccent`).
 *
 * Pair with `Icons.Filled.*` glyphs — outlined glyphs read weak inside
 * a coloured circle.
 *
 * Accent pairs are NOT driven off `dynamicDarkColorScheme()` —
 * letting the user's wallpaper steamroll per-category intent would
 * defeat the colour-coding. Hand-picked stays hand-picked even when
 * dynamic colour is on elsewhere.
 */
@Immutable
data class CategoryAccent(
    val container: Color,
    val onContainer: Color,
)

/**
 * Pick a light-or-dark [CategoryAccent] for the active theme. Each
 * named accent has a hand-tuned (container, onContainer) pair in both
 * palettes so circles stay readable on either page surface.
 */
@Composable
fun rememberCategoryAccent(name: CategoryAccentName): CategoryAccent {
    val palette = if (isSystemInDarkTheme()) DarkCategoryPalette else LightCategoryPalette
    return palette.getValue(name)
}

enum class CategoryAccentName {
    SkyBlue,    // sync / external / library-blue (e.g. Repos, Sync)
    Purple,     // lifestyle / persona accents
    Pink,       // identity / rose
    Orange,     // templates, about-version, brand-warm
    Green,      // todolists, GitHub-source
    Brown,      // licenses / acknowledgments
    Red,        // notifications
    Amber,      // access
    Cyan,       // auto & tablet
    Teal,       // calendars
    Indigo,     // mode / behaviour
    Magenta,    // appearance / palette
}

internal val LightCategoryPalette: Map<CategoryAccentName, CategoryAccent> = mapOf(
    CategoryAccentName.SkyBlue to CategoryAccent(Color(0xFFCFE8FF), Color(0xFF003047)),
    CategoryAccentName.Purple  to CategoryAccent(Color(0xFFE8DEF8), Color(0xFF21005D)),
    CategoryAccentName.Pink    to CategoryAccent(Color(0xFFFFD8E5), Color(0xFF3F0026)),
    CategoryAccentName.Orange  to CategoryAccent(Color(0xFFFFDDB6), Color(0xFF2B1700)),
    CategoryAccentName.Green   to CategoryAccent(Color(0xFFC8E8D2), Color(0xFF002912)),
    CategoryAccentName.Brown   to CategoryAccent(Color(0xFFE8D8C6), Color(0xFF3A2410)),
    CategoryAccentName.Red     to CategoryAccent(Color(0xFFFFD7D2), Color(0xFF410002)),
    CategoryAccentName.Amber   to CategoryAccent(Color(0xFFFFE9B5), Color(0xFF3D2C00)),
    CategoryAccentName.Cyan    to CategoryAccent(Color(0xFFB8ECEF), Color(0xFF002E33)),
    CategoryAccentName.Teal    to CategoryAccent(Color(0xFFB7E9D3), Color(0xFF002117)),
    CategoryAccentName.Indigo  to CategoryAccent(Color(0xFFDCE0FF), Color(0xFF0E1947)),
    CategoryAccentName.Magenta to CategoryAccent(Color(0xFFFFD7F1), Color(0xFF3E0030)),
)

internal val DarkCategoryPalette: Map<CategoryAccentName, CategoryAccent> = mapOf(
    CategoryAccentName.SkyBlue to CategoryAccent(Color(0xFF003D5C), Color(0xFF8FCEFF)),
    CategoryAccentName.Purple  to CategoryAccent(Color(0xFF3F2C73), Color(0xFFD0BCFF)),
    CategoryAccentName.Pink    to CategoryAccent(Color(0xFF5C2940), Color(0xFFFFB1C8)),
    CategoryAccentName.Orange  to CategoryAccent(Color(0xFF5C3300), Color(0xFFFFB877)),
    CategoryAccentName.Green   to CategoryAccent(Color(0xFF1F4D2E), Color(0xFF9CDDB4)),
    CategoryAccentName.Brown   to CategoryAccent(Color(0xFF4B3422), Color(0xFFE4C2A1)),
    CategoryAccentName.Red     to CategoryAccent(Color(0xFF5C1A1A), Color(0xFFFFB4AB)),
    CategoryAccentName.Amber   to CategoryAccent(Color(0xFF5C4200), Color(0xFFFFD08A)),
    CategoryAccentName.Cyan    to CategoryAccent(Color(0xFF00474D), Color(0xFF80D8E0)),
    CategoryAccentName.Teal    to CategoryAccent(Color(0xFF0F4F3A), Color(0xFF7FD7B7)),
    CategoryAccentName.Indigo  to CategoryAccent(Color(0xFF2D3F7A), Color(0xFFB8C2FF)),
    CategoryAccentName.Magenta to CategoryAccent(Color(0xFF5A2A47), Color(0xFFFFAFE0)),
)

/**
 * Renders the filled coloured circle with a centred white-on-tonal
 * glyph. Default 40-dp outer / 22-dp inner mirrors shutterboy's
 * row-avatar treatment.
 */
@Composable
fun CategoryIconCircle(
    icon: ImageVector,
    accent: CategoryAccent,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
    iconSizeDp: Int = 22,
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(accent.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accent.onContainer,
            modifier = Modifier.size(iconSizeDp.dp),
        )
    }
}

/** Convenience overload — pick palette by name in one call site. */
@Composable
fun CategoryIconCircle(
    icon: ImageVector,
    accentName: CategoryAccentName,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
    iconSizeDp: Int = 22,
) {
    CategoryIconCircle(
        icon = icon,
        accent = rememberCategoryAccent(accentName),
        contentDescription = contentDescription,
        modifier = modifier,
        sizeDp = sizeDp,
        iconSizeDp = iconSizeDp,
    )
}
