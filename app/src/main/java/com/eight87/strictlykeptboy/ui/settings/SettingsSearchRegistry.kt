package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.components.CategoryAccentName

/**
 * A row living inside a Settings subpage that the top-level Settings
 * search should be able to surface. Tapping a hit deeplinks the user
 * to the owning [category]; field-level scroll/focus is out of scope
 * for v1.
 */
data class SearchableSettingsEntry(
    val category: SettingsCategory,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
    val icon: ImageVector? = null,
    val accent: CategoryAccentName? = null,
)

/**
 * Declarative registry of every searchable row across the Settings
 * subpages. Built up once per Composable; each subpage owns a chunk
 * of `entriesFor*()` builders so the registry stays close to the
 * surface that renders the row.
 *
 * v1 surfaces hit-the-subpage deeplinks (not field-level focus). When
 * adding a new searchable row, add an entry here and (optionally)
 * keep the in-subpage filter aligned with the same keyword list.
 */
object SettingsSearchRegistry {
    fun allEntries(ctx: Context): List<SearchableSettingsEntry> = buildList {
        addAll(appearanceEntries(ctx))
        addAll(identityEntries(ctx))
        addAll(notificationsEntries(ctx))
        addAll(lifestyleEntries(ctx))
        addAll(modeEntries(ctx))
    }

    private fun appearanceEntries(ctx: Context): List<SearchableSettingsEntry> = listOf(
        SearchableSettingsEntry(
            category = SettingsCategory.Appearance,
            title = ctx.getString(R.string.settings_appearance_theme),
            subtitle = ctx.getString(R.string.settings_appearance_section_theme),
            keywords = listOf("theme", "dark", "light", "auto", "system", "appearance"),
            icon = Icons.Filled.Brightness6,
            accent = CategoryAccentName.Magenta,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Appearance,
            title = ctx.getString(R.string.settings_appearance_base_theme),
            subtitle = ctx.getString(R.string.settings_appearance_section_theme),
            keywords = listOf("base", "palette", "color", "colour", "material you", "wallpaper", "pure black", "amoled", "custom"),
            icon = Icons.Filled.Palette,
            accent = CategoryAccentName.Magenta,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Appearance,
            title = ctx.getString(R.string.settings_appearance_tint_by_repo_avatar),
            subtitle = ctx.getString(R.string.settings_appearance_section_theme),
            keywords = listOf("tint", "chrome", "avatar", "repo"),
            icon = Icons.Filled.ColorLens,
            accent = CategoryAccentName.Magenta,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Appearance,
            title = ctx.getString(R.string.settings_appearance_custom_chrome_tint),
            subtitle = ctx.getString(R.string.settings_appearance_section_theme),
            keywords = listOf("tint", "chrome", "custom", "color", "colour"),
            icon = Icons.Filled.ColorLens,
            accent = CategoryAccentName.Magenta,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Appearance,
            title = ctx.getString(R.string.settings_appearance_density),
            subtitle = ctx.getString(R.string.settings_appearance_section_display),
            keywords = listOf("density", "compact", "comfortable", "spacious", "spacing", "dense", "padding"),
            icon = Icons.Filled.SpaceBar,
            accent = CategoryAccentName.Cyan,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Appearance,
            title = safeString(ctx, R.string.settings_appearance_font_scale, "1.00"),
            subtitle = ctx.getString(R.string.settings_appearance_section_display),
            keywords = listOf("font", "size", "scale", "text", "readable", "accessibility"),
            icon = Icons.Filled.FormatSize,
            accent = CategoryAccentName.Cyan,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Appearance,
            title = ctx.getString(R.string.settings_appearance_neutral_mode),
            subtitle = ctx.getString(R.string.settings_appearance_section_neutral),
            keywords = listOf("neutral", "kink", "discreet", "privacy", "hide"),
            icon = Icons.Filled.VisibilityOff,
            accent = CategoryAccentName.Indigo,
        ),
    )

    private fun identityEntries(ctx: Context): List<SearchableSettingsEntry> = listOf(
        SearchableSettingsEntry(
            category = SettingsCategory.Identity,
            title = ctx.getString(R.string.settings_identity_praise),
            subtitle = ctx.getString(R.string.settings_identity_section_persona),
            keywords = listOf("praise", "term", "persona"),
            icon = Icons.Filled.Person,
            accent = CategoryAccentName.Pink,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Identity,
            title = ctx.getString(R.string.settings_identity_pronouns),
            subtitle = ctx.getString(R.string.settings_identity_section_persona),
            keywords = listOf("pronouns", "he", "she", "they"),
            icon = Icons.Filled.Person,
            accent = CategoryAccentName.Pink,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Identity,
            title = ctx.getString(R.string.settings_identity_honorific),
            subtitle = ctx.getString(R.string.settings_identity_section_persona),
            keywords = listOf("honorific", "sir", "ma'am"),
            icon = Icons.Filled.Person,
            accent = CategoryAccentName.Pink,
        ),
        SearchableSettingsEntry(
            category = SettingsCategory.Identity,
            title = ctx.getString(R.string.settings_identity_tone),
            subtitle = ctx.getString(R.string.settings_identity_section_persona),
            keywords = listOf("tone", "register", "soft", "stern", "formal", "playful"),
            icon = Icons.Filled.Tune,
            accent = CategoryAccentName.Pink,
        ),
    )

    private fun notificationsEntries(ctx: Context): List<SearchableSettingsEntry> = listOf(
        SearchableSettingsEntry(
            category = SettingsCategory.Notifications,
            title = ctx.getString(R.string.settings_category_notifications),
            subtitle = safeString(ctx, R.string.settings_subtitle_notifications, ""),
            keywords = listOf("notification", "reminder", "alarm", "alert", "sound", "vibrate", "channel"),
            icon = Icons.Filled.Notifications,
            accent = CategoryAccentName.Red,
        ),
    )

    private fun lifestyleEntries(ctx: Context): List<SearchableSettingsEntry> = listOf(
        SearchableSettingsEntry(
            category = SettingsCategory.Lifestyle,
            title = ctx.getString(R.string.settings_category_lifestyle),
            subtitle = safeString(ctx, R.string.settings_subtitle_lifestyle, ""),
            keywords = listOf("lifestyle", "neutral", "kink", "role", "trip", "wizard"),
            icon = Icons.Filled.Tune,
            accent = CategoryAccentName.Purple,
        ),
    )

    private fun modeEntries(ctx: Context): List<SearchableSettingsEntry> = listOf(
        SearchableSettingsEntry(
            category = SettingsCategory.Mode,
            title = ctx.getString(R.string.settings_category_mode),
            subtitle = safeString(ctx, R.string.settings_subtitle_mode, ""),
            keywords = listOf("mode", "free", "strictly kept", "discipline"),
            icon = Icons.Filled.Tune,
            accent = CategoryAccentName.Indigo,
        ),
    )

    private fun safeString(ctx: Context, resId: Int, vararg fmt: Any): String =
        try {
            if (fmt.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *fmt)
        } catch (_: Throwable) {
            ""
        }
}
