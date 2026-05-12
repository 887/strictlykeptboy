package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import com.eight87.strictlykeptboy.ui.import_export.ImportExportScreen
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState

const val TestTagSettingsPane = "SettingsPane"
const val TestTagSettingsCategoryList = "SettingsCategoryList"
const val TestTagSettingsCategoryPrefix = "SettingsCategory-"
const val TestTagSettingsContent = "SettingsContent"
const val TestTagSettingsBack = "SettingsBack"

/**
 * Phase R.4 — settings shell.
 *
 * Sealed hierarchy (R.X.2) of categories — each variant knows its own
 * label string + how to render its content. New categories ship by
 * adding a sealed-class case, not by extending a `when (it)` chain.
 *
 * - Compact: list of categories; tapping a category swaps to its
 *   content (single-pane stack semantics).
 * - Medium / Expanded: list on left, content on right
 *   ([MasterDetailLayout]); default selection = Repos so the Import/
 *   Export flow that lived at the Settings root pre-Phase-R is still
 *   the first thing the user sees.
 */
sealed class SettingsCategory(val labelRes: Int, val testTag: String) {
    object General : SettingsCategory(R.string.settings_category_general, "General")
    object Identity : SettingsCategory(R.string.settings_category_identity, "Identity")
    object Mode : SettingsCategory(R.string.settings_category_mode, "Mode")
    object Notifications : SettingsCategory(R.string.settings_category_notifications, "Notifications")
    object Repos : SettingsCategory(R.string.settings_category_repos, "Repos")
    object About : SettingsCategory(R.string.settings_category_about, "About")

    companion object {
        // Initialised lazily — listing the objects at top-level here
        // ran into a JVM class-init ordering glitch under Robolectric
        // where the sealed `object` singletons read as `null` while the
        // companion's `val all` field was being constructed. The lazy
        // delegate defers the resolution until first access, by which
        // point every `object` has been initialised.
        val all: List<SettingsCategory> by lazy {
            listOf(General, Identity, Mode, Notifications, Repos, About)
        }

        fun fromTag(tag: String): SettingsCategory? = all.firstOrNull { it.testTag == tag }
    }
}

@Composable
fun SettingsPane(
    importExportState: ImportExportViewState?,
    modifier: Modifier = Modifier,
    onPickImportFile: (RepoConfig) -> Unit = {},
    onPickExportFile: (RepoConfig) -> Unit = {},
) {
    val widthClass = LocalWindowWidthSizeClass.current
    // `rememberSaveable` survives recomposition + config-change so the
    // category selection persists across rotations. Store by tag string
    // (Saver-friendly) and resolve back to the sealed-type instance.
    var selectedTag by rememberSaveable {
        mutableStateOf(SettingsCategory.Repos.testTag)
    }
    val selected = remember(selectedTag) {
        SettingsCategory.fromTag(selectedTag) ?: SettingsCategory.Repos
    }
    var compactPushed by rememberSaveable { mutableStateOf(false) }

    val categoryList: @Composable () -> Unit = {
        SettingsCategoryList(
            selected = selected,
            onSelect = { cat ->
                selectedTag = cat.testTag
                compactPushed = true
            },
        )
    }

    val content: @Composable () -> Unit = {
        SettingsCategoryContent(
            category = selected,
            importExportState = importExportState,
            onPickImportFile = onPickImportFile,
            onPickExportFile = onPickExportFile,
        )
    }

    Box(modifier = modifier.fillMaxSize().testTag(TestTagSettingsPane)) {
        if (widthClass.isTwoPane()) {
            MasterDetailLayout(
                widthClass = widthClass,
                master = categoryList,
                detail = content,
            )
        } else {
            if (compactPushed) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            IconButton(
                                onClick = { compactPushed = false },
                                modifier = Modifier.testTag(TestTagSettingsBack),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.settings_back_to_categories),
                                )
                            }
                            Text(
                                text = stringResource(selected.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) { content() }
                }
            } else {
                categoryList()
            }
        }
    }
}

@Composable
private fun SettingsCategoryList(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TestTagSettingsCategoryList),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_categories_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        items(SettingsCategory.all) { cat ->
            val selectedNow = cat::class == selected::class
            ListItem(
                headlineContent = { Text(stringResource(cat.labelRes)) },
                modifier = Modifier
                    .testTag("$TestTagSettingsCategoryPrefix${cat.testTag}")
                    .clickableForCategory(onClick = { onSelect(cat) }),
                colors = if (selectedNow) {
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    ListItemDefaults.colors()
                },
            )
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    importExportState: ImportExportViewState?,
    onPickImportFile: (RepoConfig) -> Unit,
    onPickExportFile: (RepoConfig) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().testTag(TestTagSettingsContent)) {
        when (category) {
            SettingsCategory.Repos -> if (importExportState != null) {
                ImportExportScreen(
                    state = importExportState,
                    onPickImportFile = onPickImportFile,
                    onPickExportFile = onPickExportFile,
                )
            } else {
                CategoryPlaceholder(stringResource(category.labelRes))
            }
            SettingsCategory.About -> AboutContent()
            else -> CategoryPlaceholder(stringResource(category.labelRes))
        }
    }
}

@Composable
private fun CategoryPlaceholder(label: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.settings_category_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutContent() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            stringResource(R.string.settings_about_app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

private fun Modifier.clickableForCategory(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
