package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.theme.DensityScale
import com.eight87.strictlykeptboy.theme.ThemeMode
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs

const val TestTagCatAppearance = "Cat-Appearance"

/**
 * Look and Feel — tonearmboy-parity grouped-card layout with inline
 * keyword search.
 *
 * Three sections: **Theme** (mode picker, dynamic-color toggle),
 * **Display** (density picker, font-scale slider), **Neutral mode**
 * (toggle). The pill-shaped search at the top filters rows by their
 * label + keyword list — typing "amoled" or "dark" jumps to Theme;
 * "dense" / "spacing" jumps to Density; "kink" / "neutral" jumps to
 * Neutral mode. Empty results show a "No settings match …" line so
 * the user knows search ran.
 */
@Composable
fun AppearanceCategory(
    prefs: AppearancePrefs,
    neutral: NeutralModePrefs,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    fun matches(vararg kw: String): Boolean =
        q.isEmpty() || kw.any { it.lowercase().contains(q) }

    val showTheme = matches(
        "theme", "dark", "light", "auto", "system", "appearance",
        "dynamic", "material you", "color", "colour", "palette", "wallpaper",
    )
    val showDensity = matches(
        "density", "compact", "comfortable", "spacious", "spacing", "dense", "padding",
    )
    val showFontScale = matches(
        "font", "size", "scale", "text", "readable", "accessibility",
    )
    val showNeutral = matches(
        "neutral", "kink", "role", "templates", "discreet", "privacy", "hide",
    )

    val nothingMatched = !showTheme && !showDensity && !showFontScale && !showNeutral

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
            .testTag(TestTagCatAppearance),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.settings_appearance_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("$TestTagCatAppearance-Search"),
        )

        if (nothingMatched) {
            Text(
                text = stringResource(R.string.settings_appearance_no_results, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        if (showTheme) {
            SectionHeader(stringResource(R.string.settings_appearance_section_theme))
            CategoryCard {
                // Theme mode picker — chips inline under a labelled row.
                PickerRow(
                    icon = Icons.Outlined.Brightness6,
                    tint = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.settings_appearance_theme),
                    subtitle = stringResource(R.string.settings_appearance_theme_subtitle),
                ) {
                    val items = listOf(
                        ThemeMode.Auto to R.string.settings_appearance_theme_auto,
                        ThemeMode.Light to R.string.settings_appearance_theme_light,
                        ThemeMode.Dark to R.string.settings_appearance_theme_dark,
                    )
                    items.forEach { (mode, label) ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { prefs.setThemeMode(mode) },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("$TestTagCatAppearance-Theme-${mode.name}"),
                        )
                    }
                }
                RowDivider()
                ToggleRowM3(
                    icon = Icons.Outlined.ColorLens,
                    tint = MaterialTheme.colorScheme.secondary,
                    label = stringResource(R.string.settings_appearance_dynamic),
                    subtitle = stringResource(R.string.settings_appearance_dynamic_subtitle),
                    checked = state.dynamicColor,
                    onCheckedChange = { prefs.setDynamicColor(it) },
                    testTag = "$TestTagCatAppearance-Dynamic",
                )
            }
        }

        if (showDensity || showFontScale) {
            SectionHeader(stringResource(R.string.settings_appearance_section_display))
            CategoryCard {
                if (showDensity) {
                    PickerRow(
                        icon = Icons.Outlined.SpaceBar,
                        tint = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.settings_appearance_density),
                        subtitle = stringResource(R.string.settings_appearance_density_subtitle),
                    ) {
                        val items = listOf(
                            DensityScale.Compact to R.string.settings_appearance_density_compact,
                            DensityScale.Comfortable to R.string.settings_appearance_density_comfortable,
                            DensityScale.Spacious to R.string.settings_appearance_density_spacious,
                        )
                        items.forEach { (scale, label) ->
                            FilterChip(
                                selected = state.densityScale == scale,
                                onClick = { prefs.setDensity(scale) },
                                label = { Text(stringResource(label)) },
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .testTag("$TestTagCatAppearance-Density-${scale.name}"),
                            )
                        }
                    }
                }
                if (showDensity && showFontScale) RowDivider()
                if (showFontScale) {
                    var fontScale by remember { mutableFloatStateOf(state.densityScale.multiplier) }
                    PickerRow(
                        icon = Icons.Outlined.FormatSize,
                        tint = MaterialTheme.colorScheme.tertiary,
                        label = stringResource(
                            R.string.settings_appearance_font_scale,
                            "%.2f".format(fontScale),
                        ),
                        subtitle = stringResource(R.string.settings_appearance_font_scale_subtitle),
                    ) {
                        Slider(
                            value = fontScale,
                            onValueChange = { fontScale = it },
                            valueRange = 0.75f..1.5f,
                            steps = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("$TestTagCatAppearance-FontScale"),
                        )
                    }
                }
            }
        }

        if (showNeutral) {
            SectionHeader(stringResource(R.string.settings_appearance_section_neutral))
            CategoryCard {
                var neutralOn by remember { mutableStateOf(neutral.isEnabled()) }
                ToggleRowM3(
                    icon = Icons.Outlined.VisibilityOff,
                    tint = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.settings_appearance_neutral_mode),
                    subtitle = stringResource(R.string.settings_appearance_neutral_mode_blurb),
                    checked = neutralOn,
                    onCheckedChange = { neutralOn = it; neutral.setEnabled(it) },
                    testTag = "$TestTagCatAppearance-Neutral",
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun CategoryCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) { content() }
}

@Composable
private fun LeadingBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = tint.copy(alpha = 0.18f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LeadingBadge(icon, tint)
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}

@Composable
private fun ToggleRowM3(
    icon: ImageVector,
    tint: Color,
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { LeadingBadge(icon, tint) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun RowDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}
