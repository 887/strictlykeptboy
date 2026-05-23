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
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.eight87.strictlykeptboy.theme.BaseTheme
import com.eight87.strictlykeptboy.theme.DensityScale
import com.eight87.strictlykeptboy.theme.ThemeMode
import com.eight87.strictlykeptboy.theme.baseThemeMatch
import com.eight87.strictlykeptboy.theme.baseThemePickerOptions
import com.eight87.strictlykeptboy.ui.components.CategoryAccentName
import com.eight87.strictlykeptboy.ui.components.CategoryIconCircle
import com.eight87.strictlykeptboy.ui.components.ColorPickerDialog
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs
import com.eight87.strictlykeptboy.avatar.AvatarPackPrefs
import com.eight87.strictlykeptboy.avatar.CompositePackStore
import com.eight87.strictlykeptboy.avatar.StickerPack

const val TestTagCatAppearance = "Cat-Appearance"
const val TestTagCatAppearanceStickerSection = "Cat-Appearance-StickerSection"
const val TestTagCatAppearanceStickerPackChip = "Cat-Appearance-StickerPackChip"

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
    avatarPackPrefs: AvatarPackPrefs? = null,
    packStore: CompositePackStore? = null,
    activeSpecies: String = "bat",
    // 2.1.E.4 — Neutral mode toggle moved to Lifestyle. Appearance keeps
    // a deeplink chip here so the search index ("neutral" / "kink")
    // still surfaces a hit and the user lands at the new home.
    onJumpToLifestyleNeutral: () -> Unit = {},
) {
    val state by prefs.state.collectAsState()
    // Inline per-subpage search retired (post-`ui(settings)` cleanup).
    // The top-level Settings search now indexes every subpage row via
    // SettingsSearchRegistry, so a second search field here would be
    // both visually noisy and functionally redundant. All sections
    // render unconditionally; theme + display + neutral hits surface
    // through the outer search.
    val showTheme = true
    val showDensity = true
    val showFontScale = true
    val showNeutral = true
    val showStickers = avatarPackPrefs != null && packStore != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
            .testTag(TestTagCatAppearance),
    ) {
        if (showTheme) {
            SectionHeader(stringResource(R.string.settings_appearance_section_theme))
            // Look-and-feel parity port — 4-row Theme section (tonearmboy
            // shape, with album-art → repo-avatar substitution).
            var showThemeDialog by remember { mutableStateOf(false) }
            var showBaseThemeDialog by remember { mutableStateOf(false) }
            var showBaseThemeColorPicker by remember { mutableStateOf(false) }
            var showChromeTintPicker by remember { mutableStateOf(false) }
            CategoryCard {
                ThemeListItem(
                    icon = Icons.Outlined.Brightness6,
                    accent = CategoryAccentName.SkyBlue,
                    label = stringResource(R.string.settings_appearance_theme),
                    supporting = themeModeLabel(state.themeMode),
                    onClick = { showThemeDialog = true },
                    testTag = "$TestTagCatAppearance-Theme",
                )
                RowDivider()
                ThemeListItem(
                    icon = Icons.Outlined.Palette,
                    accent = CategoryAccentName.Magenta,
                    label = stringResource(R.string.settings_appearance_base_theme),
                    supporting = baseThemeLabel(state.baseTheme),
                    onClick = { showBaseThemeDialog = true },
                    testTag = "$TestTagCatAppearance-BaseTheme",
                    trailing = (state.baseTheme as? BaseTheme.Custom)?.let { custom ->
                        { SwatchDot(rgb = custom.seedRgb, tag = "$TestTagCatAppearance-BaseThemeSwatch") }
                    },
                )
                RowDivider()
                ToggleRowM3(
                    icon = Icons.Outlined.ColorLens,
                    accent = CategoryAccentName.Orange,
                    label = stringResource(R.string.settings_appearance_tint_by_repo_avatar),
                    subtitle = stringResource(R.string.settings_appearance_tint_by_repo_avatar_subtitle),
                    checked = state.tintByRepoAvatar,
                    onCheckedChange = { prefs.setTintByRepoAvatar(it) },
                    testTag = "$TestTagCatAppearance-TintByRepoAvatar",
                )
                RowDivider()
                ThemeListItem(
                    icon = Icons.Outlined.ColorLens,
                    accent = CategoryAccentName.Purple,
                    label = stringResource(R.string.settings_appearance_custom_chrome_tint),
                    supporting = if (state.customChromeTint == 0L) {
                        stringResource(R.string.settings_appearance_custom_chrome_tint_unset)
                    } else "#%06X".format(state.customChromeTint),
                    onClick = { showChromeTintPicker = true },
                    testTag = "$TestTagCatAppearance-CustomChromeTint",
                    trailing = if (state.customChromeTint != 0L) {
                        { SwatchDot(rgb = state.customChromeTint, tag = "$TestTagCatAppearance-CustomChromeTintSwatch") }
                    } else null,
                )
            }

            if (showThemeDialog) {
                ThemeModeDialog(
                    current = state.themeMode,
                    onPick = {
                        prefs.setThemeMode(it)
                        showThemeDialog = false
                    },
                    onDismiss = { showThemeDialog = false },
                )
            }
            if (showBaseThemeDialog) {
                BaseThemeDialog(
                    current = state.baseTheme,
                    onPick = { picked ->
                        showBaseThemeDialog = false
                        if (picked is BaseTheme.Custom) {
                            showBaseThemeColorPicker = true
                        } else {
                            prefs.setBaseTheme(picked)
                        }
                    },
                    onDismiss = { showBaseThemeDialog = false },
                )
            }
            if (showBaseThemeColorPicker) {
                val seed = (state.baseTheme as? BaseTheme.Custom)?.seedRgb ?: 0x6750A4L
                ColorPickerDialog(
                    initialRgb = seed,
                    onConfirm = { rgb ->
                        prefs.setBaseTheme(BaseTheme.Custom(rgb))
                        showBaseThemeColorPicker = false
                    },
                    onDismiss = { showBaseThemeColorPicker = false },
                    onReset = {
                        prefs.setBaseTheme(BaseTheme.Default)
                        showBaseThemeColorPicker = false
                    },
                )
            }
            if (showChromeTintPicker) {
                val seed = if (state.customChromeTint == 0L) 0x6464C8L else state.customChromeTint
                ColorPickerDialog(
                    initialRgb = seed,
                    onConfirm = { rgb ->
                        prefs.setCustomChromeTint(rgb)
                        showChromeTintPicker = false
                    },
                    onDismiss = { showChromeTintPicker = false },
                    onReset = {
                        prefs.setCustomChromeTint(0L)
                        showChromeTintPicker = false
                    },
                )
            }
        }

        if (showDensity || showFontScale) {
            SectionHeader(stringResource(R.string.settings_appearance_section_display))
            CategoryCard {
                if (showDensity) {
                    PickerRow(
                        icon = Icons.Outlined.SpaceBar,
                        accent = CategoryAccentName.Cyan,
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
                        accent = CategoryAccentName.Teal,
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
            // 2.1.E.4 — Neutral mode lives in Lifestyle now. Keep a row here
            // so search hits for "neutral"/"kink" still land somewhere
            // useful; the row deeplinks back to the Lifestyle category.
            SectionHeader(stringResource(R.string.settings_appearance_section_neutral))
            CategoryCard {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_appearance_neutral_mode))
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_appearance_neutral_moved_blurb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        LeadingBadge(Icons.Outlined.VisibilityOff, CategoryAccentName.Indigo)
                    },
                    trailingContent = {
                        androidx.compose.material3.TextButton(
                            onClick = onJumpToLifestyleNeutral,
                            modifier = Modifier.testTag("$TestTagCatAppearance-NeutralDeeplink"),
                        ) {
                            Text(stringResource(R.string.settings_appearance_neutral_open_lifestyle))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        if (showStickers && avatarPackPrefs != null && packStore != null) {
            StickerPackSection(
                packStore = packStore,
                prefs = avatarPackPrefs,
                species = activeSpecies,
                neutralOn = neutral.isEnabled(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Phase WW.5 — Settings → Appearance → Sticker pack picker.
 *
 * Lists every pack the [CompositePackStore] knows about that matches
 * the current [species]; the user picks one to activate. When neutral-
 * mode is on, displays a small hint that the resolver applies the
 * neutral-tag filter on top of the chosen pack.
 */
@Composable
private fun StickerPackSection(
    packStore: CompositePackStore,
    prefs: AvatarPackPrefs,
    species: String,
    neutralOn: Boolean,
) {
    val all = remember(packStore, species) {
        packStore.all().filter { it.species.equals(species, ignoreCase = true) }
    }
    var active by remember(species) { mutableStateOf(prefs.activePackFor(species)) }
    SectionHeader(stringResource(R.string.settings_appearance_section_stickers))
    CategoryCard {
        PickerRow(
            icon = Icons.Outlined.Palette,
            accent = CategoryAccentName.Pink,
            label = stringResource(R.string.settings_appearance_sticker_pack),
            subtitle = stringResource(R.string.settings_appearance_sticker_pack_subtitle),
        ) {
            if (all.isEmpty()) {
                Text(
                    stringResource(R.string.settings_appearance_sticker_pack_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.testTag(TestTagCatAppearanceStickerSection)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        all.forEach { p: StickerPack ->
                            FilterChip(
                                selected = p.packId == active,
                                onClick = {
                                    active = p.packId
                                    prefs.setActivePackFor(species, p.packId)
                                },
                                label = { Text(p.name) },
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .testTag("$TestTagCatAppearanceStickerPackChip-${p.packId}"),
                            )
                        }
                    }
                    if (neutralOn) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_appearance_sticker_pack_neutral_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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

/**
 * M3E colourful row icon — the shutterboy-parity treatment ported
 * from the top-level category list to every subpage row. Replaces the
 * old `tint.copy(alpha = 0.18f)` watery-square look so the theme is
 * consistent end-to-end.
 */
@Composable
private fun LeadingBadge(icon: ImageVector, accent: CategoryAccentName) {
    CategoryIconCircle(icon = icon, accentName = accent, contentDescription = null)
}

/** Legacy two-arg form kept for the few neutral-mode call site below. */
@Composable
private fun LeadingBadge(icon: ImageVector, @Suppress("UNUSED_PARAMETER") tint: Color) {
    // Map onto a sensible accent — the colour argument is ignored;
    // each call site that still uses this overload is at a row that
    // doesn't have a strong semantic colour mapping (e.g. the neutral-
    // mode deeplink). Magenta keeps it visually paired with the
    // Appearance category accent.
    LeadingBadge(icon, CategoryAccentName.Magenta)
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    accent: CategoryAccentName,
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
        LeadingBadge(icon, accent)
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
    accent: CategoryAccentName,
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
        leadingContent = { LeadingBadge(icon, accent) },
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

@Composable
private fun ThemeListItem(
    icon: ImageVector,
    accent: CategoryAccentName,
    label: String,
    supporting: String,
    onClick: () -> Unit,
    testTag: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { LeadingBadge(icon, accent) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag),
    )
}

@Composable
private fun SwatchDot(rgb: Long, tag: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                color = Color(0xFF000000L or (rgb and 0xFFFFFFL)),
                shape = CircleShape,
            )
            .testTag(tag),
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.Auto -> R.string.settings_appearance_theme_auto
        ThemeMode.Light -> R.string.settings_appearance_theme_light
        ThemeMode.Dark -> R.string.settings_appearance_theme_dark
    },
)

@Composable
private fun baseThemeLabel(b: BaseTheme): String = stringResource(
    when (b) {
        BaseTheme.DefaultColors -> R.string.settings_appearance_base_theme_default_colors
        BaseTheme.MaterialYou -> R.string.settings_appearance_base_theme_material_you
        BaseTheme.PureBlack -> R.string.settings_appearance_base_theme_pure_black
        is BaseTheme.Custom -> R.string.settings_appearance_base_theme_custom
    },
)

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ThemeMode.Auto to R.string.settings_appearance_theme_auto,
        ThemeMode.Light to R.string.settings_appearance_theme_light,
        ThemeMode.Dark to R.string.settings_appearance_theme_dark,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_appearance_theme_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().testTag("$TestTagCatAppearance-ThemeDialog")) {
                options.forEach { (mode, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(mode) }
                            .padding(vertical = 8.dp)
                            .testTag("$TestTagCatAppearance-ThemeDialog-${mode.name}"),
                    ) {
                        RadioButton(selected = current == mode, onClick = { onPick(mode) })
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_appearance_dialog_close))
            }
        },
    )
}

@Composable
private fun BaseThemeDialog(
    current: BaseTheme,
    onPick: (BaseTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val matched = baseThemeMatch(current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_appearance_base_theme_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().testTag("$TestTagCatAppearance-BaseThemeDialog")) {
                baseThemePickerOptions.forEach { option ->
                    val tag = when (option) {
                        BaseTheme.DefaultColors -> "DefaultColors"
                        BaseTheme.MaterialYou -> "MaterialYou"
                        BaseTheme.PureBlack -> "PureBlack"
                        is BaseTheme.Custom -> "Custom"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                            .padding(vertical = 8.dp)
                            .testTag("$TestTagCatAppearance-BaseThemeDialog-$tag"),
                    ) {
                        RadioButton(selected = matched == option, onClick = { onPick(option) })
                        Spacer(Modifier.size(8.dp))
                        Text(baseThemeLabel(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_appearance_dialog_close))
            }
        },
    )
}
