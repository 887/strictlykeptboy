package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.theme.DensityScale
import com.eight87.strictlykeptboy.theme.ThemeMode
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs

const val TestTagCatAppearance = "Cat-Appearance"

/**
 * Phase S.9 — Appearance category.
 *
 * Theme mode, dynamic color, density, font scale (display only — the
 * system font scale is the source of truth), neutral-mode toggle.
 */
@Composable
fun AppearanceCategory(
    prefs: AppearancePrefs,
    neutral: NeutralModePrefs,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    CategorySurface(
        testTag = TestTagCatAppearance,
        title = stringResource(R.string.settings_category_appearance),
        modifier = modifier,
    ) {
        SectionLabel(stringResource(R.string.settings_appearance_theme))
        Row {
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
                    modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatAppearance-Theme-${mode.name}"),
                )
            }
        }
        ToggleRow(
            label = stringResource(R.string.settings_appearance_dynamic),
            checked = state.dynamicColor,
            onCheckedChange = { prefs.setDynamicColor(it) },
            testTag = "$TestTagCatAppearance-Dynamic",
        )
        SectionLabel(stringResource(R.string.settings_appearance_density))
        Row {
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
                    modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatAppearance-Density-${scale.name}"),
                )
            }
        }
        SectionLabel(stringResource(R.string.settings_appearance_font_scale, "%.2f".format(state.densityScale.multiplier)))
        var fontScale by remember { mutableFloatStateOf(state.densityScale.multiplier) }
        Slider(
            value = fontScale,
            onValueChange = { fontScale = it },
            valueRange = 0.75f..1.5f,
            steps = 5,
            modifier = Modifier.testTag("$TestTagCatAppearance-FontScale"),
        )

        SectionLabel(stringResource(R.string.settings_appearance_neutral_mode))
        Text(
            stringResource(R.string.settings_appearance_neutral_mode_blurb),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        var neutralOn by remember { androidx.compose.runtime.mutableStateOf(neutral.isEnabled()) }
        ToggleRow(
            label = stringResource(R.string.settings_appearance_neutral_mode),
            checked = neutralOn,
            onCheckedChange = { neutralOn = it; neutral.setEnabled(it) },
            testTag = "$TestTagCatAppearance-Neutral",
        )
        Spacer(Modifier.height(16.dp))
    }
}
