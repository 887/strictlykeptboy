package com.eight87.strictlykeptboy.theme

/**
 * Look-and-feel base theme — the foundation [androidx.compose.material3.ColorScheme]
 * the rest of the app paints on top of.
 *
 * Ported from tonearmboy's `BaseTheme` (D.20.4 / D.25.1) so the four boy-apps
 * share one settings shape. In strictlykeptboy:
 *
 *  - [DefaultColors] — the built-in M3E "bat" palette (offline-safe).
 *  - [MaterialYou] — system wallpaper-derived dynamic palette (Android 12+).
 *  - [PureBlack] — pitch-black background + surface on dark schemes (AMOLED).
 *  - [Custom] — user-picked 24-bit RGB seed; derives a coherent scheme.
 */
sealed interface BaseTheme {
    data object DefaultColors : BaseTheme
    data object MaterialYou : BaseTheme
    data object PureBlack : BaseTheme

    /** 24-bit `0xRRGGBB` seed; alpha is ignored. */
    data class Custom(val seedRgb: Long) : BaseTheme

    companion object {
        val Default: BaseTheme = MaterialYou

        /** Wire-format (de)serialisation for SharedPreferences storage. */
        fun toStored(b: BaseTheme): String = when (b) {
            DefaultColors -> "default_colors"
            MaterialYou -> "material_you"
            PureBlack -> "pure_black"
            is Custom -> "custom:${b.seedRgb}"
        }

        fun fromStored(raw: String?): BaseTheme = when {
            raw == null -> Default
            raw == "default_colors" -> DefaultColors
            raw == "material_you" -> MaterialYou
            raw == "pure_black" -> PureBlack
            raw.startsWith("custom:") -> raw.substringAfter("custom:")
                .toLongOrNull()?.let(::Custom) ?: Default
            else -> Default
        }
    }
}

/** Picker display set — sealed interfaces can't auto-enumerate leaves. */
val baseThemePickerOptions: List<BaseTheme> = listOf(
    BaseTheme.DefaultColors,
    BaseTheme.MaterialYou,
    BaseTheme.PureBlack,
    BaseTheme.Custom(seedRgb = 0x6750A4L), // M3 default purple
)

/** Map a stored value to the picker sentinel so the radio dialog highlights correctly. */
fun baseThemeMatch(stored: BaseTheme): BaseTheme = when (stored) {
    BaseTheme.DefaultColors -> BaseTheme.DefaultColors
    BaseTheme.MaterialYou -> BaseTheme.MaterialYou
    BaseTheme.PureBlack -> BaseTheme.PureBlack
    is BaseTheme.Custom -> baseThemePickerOptions.last()
}
