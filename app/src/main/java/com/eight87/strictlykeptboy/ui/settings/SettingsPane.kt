package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box as LayoutBox
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.sync.SyncStatusStore
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import com.eight87.strictlykeptboy.ui.settings.categories.AboutCategory
import com.eight87.strictlykeptboy.ui.settings.categories.AppearanceCategory
import com.eight87.strictlykeptboy.ui.settings.categories.CalendarsCategory
import com.eight87.strictlykeptboy.ui.settings.categories.IdentitiesCategory
import com.eight87.strictlykeptboy.ui.settings.categories.IdentityCategory
import com.eight87.strictlykeptboy.ui.settings.categories.LifestyleCategory
import com.eight87.strictlykeptboy.ui.settings.categories.ModeCategory
import com.eight87.strictlykeptboy.ui.settings.categories.NotificationsCategory
import com.eight87.strictlykeptboy.ui.settings.categories.ReposCategory
import com.eight87.strictlykeptboy.ui.settings.categories.TemplatesCategory
import com.eight87.strictlykeptboy.ui.settings.categories.TodolistsCategory
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs

const val TestTagSettingsPane = "SettingsPane"
const val TestTagSettingsCategoryList = "SettingsCategoryList"
const val TestTagSettingsCategoryPrefix = "SettingsCategory-"
const val TestTagSettingsContent = "SettingsContent"
const val TestTagSettingsBack = "SettingsBack"

/**
 * Phase R.4 — settings shell. Phase S — categories filled with real
 * content under [com.eight87.strictlykeptboy.ui.settings.categories].
 *
 * The sealed [SettingsCategory] hierarchy (R.X.2) lists the eleven Phase
 * S surfaces. Adding a category = adding a sealed-class case, not
 * extending a `when (it)` chain.
 */
sealed class SettingsCategory(val labelRes: Int, val testTag: String) {
    object Repos : SettingsCategory(R.string.settings_category_repos, "Repos")
    object Identities : SettingsCategory(R.string.settings_category_identity, "Identities")
    object Sync : SettingsCategory(R.string.settings_category_sync, "Sync")
    object Notifications : SettingsCategory(R.string.settings_category_notifications, "Notifications")
    object Calendars : SettingsCategory(R.string.settings_category_calendars, "Calendars")
    object Todolists : SettingsCategory(R.string.settings_category_todolists, "Todolists")
    object Templates : SettingsCategory(R.string.settings_category_templates, "Templates")
    object Lifestyle : SettingsCategory(R.string.settings_category_lifestyle, "Lifestyle")
    object Identity : SettingsCategory(R.string.settings_identity_title, "Identity")
    object Appearance : SettingsCategory(R.string.settings_category_appearance, "Appearance")
    object About : SettingsCategory(R.string.settings_category_about, "About")
    object Mode : SettingsCategory(R.string.settings_category_mode, "Mode")

    companion object {
        val all: List<SettingsCategory> by lazy {
            listOf(
                Repos, Identities, Sync, Notifications, Calendars, Todolists,
                Templates, Lifestyle, Identity, Appearance, About, Mode,
            )
        }

        fun fromTag(tag: String): SettingsCategory? = all.firstOrNull { it.testTag == tag }
    }
}

/**
 * R.X.1 / R.X.7 — narrow bag of prefs handed to [SettingsPane].
 *
 * Every field is nullable: composables that don't get their handle fall
 * back to a `placeholder` content surface, which keeps the existing
 * call sites (and the master-detail test) working without forcing
 * everyone to construct a full graph.
 */
data class SettingsAccess(
    val syncPrefs: SyncSettingsPrefs? = null,
    val statusStore: SyncStatusStore? = null,
    val notificationPrefs: NotificationPrefs? = null,
    val calendarVisibility: CalendarVisibilityPrefs? = null,
    val todolistVisibility: CalendarVisibilityPrefs? = null,
    val templateIds: List<String> = emptyList(),
    val identityPrefs: IdentityPrefs? = null,
    val appearancePrefs: AppearancePrefs? = null,
    val neutralPrefs: NeutralModePrefs? = null,
    // Phase WW.5 — sticker pack picker (Settings → Appearance).
    val avatarPackPrefs: com.eight87.strictlykeptboy.avatar.AvatarPackPrefs? = null,
    val packStore: com.eight87.strictlykeptboy.avatar.CompositePackStore? = null,
    val activeAvatarSpecies: String = "bat",
    val modePrefs: ModePrefs? = null,
    val onOpenWizardAtRoles: () -> Unit = {},
    /** Phase CCC.10 / HV-G.1 — Settings → Lifestyle → Plan a trip entry-point. */
    val onPlanTrip: () -> Unit = {},
    val onApplyTemplate: (String) -> Unit = {},
    val onSaveCustomTemplateUrl: (String) -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onOpenRepoLink: () -> Unit = {},
    val onOpenReposList: () -> Unit = {},
    val onOpenPrivacyPolicy: () -> Unit = {},
)

@Composable
fun SettingsPane(
    importExportState: ImportExportViewState?,
    modifier: Modifier = Modifier,
    onPickImportFile: (RepoConfig) -> Unit = {},
    onPickExportFile: (RepoConfig) -> Unit = {},
    access: SettingsAccess = SettingsAccess(),
) {
    val widthClass = LocalWindowWidthSizeClass.current
    var selectedTag by rememberSaveable { mutableStateOf(SettingsCategory.Repos.testTag) }
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
            access = access,
            onJumpToIdentity = {
                selectedTag = SettingsCategory.Identity.testTag
                compactPushed = true
            },
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
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            } else {
                categoryList()
            }
        }
    }
}

/**
 * Tonearmboy parity (user direction 2026-05-13): grouped-card settings
 * list with section headers in accent color, a pill-shaped filled
 * search bar, and per-row colored circular icon + title + subtitle.
 * No left gutter — section headers and cards both start flush with
 * the same horizontal inset.
 */
private data class CategoryMeta(
    val icon: ImageVector,
    val subtitleRes: Int,
    val iconTint: androidx.compose.ui.graphics.Color,
)

@Composable
private fun metaFor(cat: SettingsCategory): CategoryMeta {
    val cs = MaterialTheme.colorScheme
    return when (cat) {
        SettingsCategory.Appearance ->
            CategoryMeta(Icons.Filled.Palette, R.string.settings_subtitle_appearance, cs.tertiary)
        SettingsCategory.Repos ->
            CategoryMeta(Icons.Filled.FolderShared, R.string.settings_subtitle_repos, cs.primary)
        SettingsCategory.Identities ->
            CategoryMeta(Icons.Filled.PeopleAlt, R.string.settings_subtitle_authors, cs.secondary)
        SettingsCategory.Calendars ->
            CategoryMeta(Icons.Filled.CalendarMonth, R.string.settings_subtitle_calendars, cs.primary)
        SettingsCategory.Todolists ->
            CategoryMeta(Icons.Filled.Checklist, R.string.settings_subtitle_todolists, cs.secondary)
        SettingsCategory.Templates ->
            CategoryMeta(Icons.Filled.Description, R.string.settings_subtitle_templates, cs.tertiary)
        SettingsCategory.Sync ->
            CategoryMeta(Icons.Filled.Sync, R.string.settings_subtitle_sync, cs.primary)
        SettingsCategory.Notifications ->
            CategoryMeta(Icons.Filled.Notifications, R.string.settings_subtitle_notifications, cs.secondary)
        SettingsCategory.Mode ->
            CategoryMeta(Icons.Filled.Tune, R.string.settings_subtitle_mode, cs.tertiary)
        SettingsCategory.Lifestyle ->
            CategoryMeta(Icons.Filled.Favorite, R.string.settings_subtitle_lifestyle, cs.primary)
        SettingsCategory.Identity ->
            CategoryMeta(Icons.Filled.Person, R.string.settings_subtitle_identity, cs.secondary)
        SettingsCategory.About ->
            CategoryMeta(Icons.Filled.Info, R.string.settings_subtitle_about, cs.tertiary)
    }
}

private data class SettingsSection(val titleRes: Int, val items: List<SettingsCategory>)

private val sections: List<SettingsSection> = listOf(
    SettingsSection(
        R.string.settings_section_appearance_header,
        listOf(SettingsCategory.Appearance),
    ),
    SettingsSection(
        R.string.settings_section_library,
        listOf(
            SettingsCategory.Repos,
            SettingsCategory.Identities,
            SettingsCategory.Calendars,
            SettingsCategory.Todolists,
            SettingsCategory.Templates,
        ),
    ),
    SettingsSection(
        R.string.settings_section_behaviour,
        listOf(
            SettingsCategory.Sync,
            SettingsCategory.Notifications,
            SettingsCategory.Mode,
        ),
    ),
    SettingsSection(
        R.string.settings_section_lifestyle,
        listOf(
            SettingsCategory.Lifestyle,
            SettingsCategory.Identity,
        ),
    ),
    SettingsSection(
        R.string.settings_section_about_header,
        listOf(SettingsCategory.About),
    ),
)

@Composable
private fun SettingsCategoryList(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visibleSections = remember(query) {
        if (query.isBlank()) sections
        else sections.mapNotNull { sec ->
            val kept = sec.items.filter { it.testTag.contains(query, ignoreCase = true) }
            if (kept.isEmpty()) null else SettingsSection(sec.titleRes, kept)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(TestTagSettingsCategoryList),
    ) {
        // Pill-shaped filled search bar (tonearmboy parity). Edge-to-edge
        // with 16dp horizontal margin matching the card insets — keeps the
        // search and the cards optically aligned and removes the previous
        // 100+px left-gutter.
        androidx.compose.material3.TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("SettingsSearchField"),
        )

        visibleSections.forEach { section ->
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp,
                ),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                section.items.forEachIndexed { idx, cat ->
                    val meta = metaFor(cat)
                    val selectedNow = cat::class == selected::class
                    ListItem(
                        headlineContent = { Text(stringResource(cat.labelRes)) },
                        supportingContent = {
                            Text(
                                stringResource(meta.subtitleRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            LayoutBox(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = meta.iconTint.copy(alpha = 0.18f),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = meta.icon,
                                    contentDescription = null,
                                    tint = meta.iconTint,
                                )
                            }
                        },
                        colors = if (selectedNow) {
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                        } else {
                            ListItemDefaults.colors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            )
                        },
                        modifier = Modifier
                            .testTag("$TestTagSettingsCategoryPrefix${cat.testTag}")
                            .clickable { onSelect(cat) },
                    )
                    if (idx < section.items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
        // bottom breathing room
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    importExportState: ImportExportViewState?,
    onPickImportFile: (RepoConfig) -> Unit,
    onPickExportFile: (RepoConfig) -> Unit,
    access: SettingsAccess,
    onJumpToIdentity: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().testTag(TestTagSettingsContent)) {
        when (category) {
            SettingsCategory.Repos -> ReposCategory(
                importExportState = importExportState,
                onPickImportFile = onPickImportFile,
                onPickExportFile = onPickExportFile,
                onOpenReposList = access.onOpenReposList,
            )
            SettingsCategory.Identities -> IdentitiesCategory(onOpenIdentity = onJumpToIdentity)
            SettingsCategory.Sync -> access.syncPrefs?.let { p ->
                com.eight87.strictlykeptboy.ui.settings.categories.SyncCategory(
                    prefs = p,
                    statusStore = access.statusStore,
                )
            } ?: CategoryPlaceholder(stringResource(category.labelRes))
            SettingsCategory.Notifications -> access.notificationPrefs?.let { p ->
                NotificationsCategory(prefs = p)
            } ?: CategoryPlaceholder(stringResource(category.labelRes))
            SettingsCategory.Calendars -> access.calendarVisibility?.let { p ->
                CalendarsCategory(prefs = p)
            } ?: CategoryPlaceholder(stringResource(category.labelRes))
            SettingsCategory.Todolists -> access.todolistVisibility?.let { p ->
                TodolistsCategory(prefs = p)
            } ?: CategoryPlaceholder(stringResource(category.labelRes))
            SettingsCategory.Templates -> TemplatesCategory(
                templateIds = access.templateIds,
                onApply = access.onApplyTemplate,
                onSaveCustomUrl = access.onSaveCustomTemplateUrl,
            )
            SettingsCategory.Lifestyle -> LifestyleCategory(
                onOpenWizardAtRoles = access.onOpenWizardAtRoles,
                onPlanTrip = access.onPlanTrip,
            )
            SettingsCategory.Identity -> access.identityPrefs?.let { p ->
                IdentityCategory(prefs = p)
            } ?: CategoryPlaceholder(stringResource(category.labelRes))
            SettingsCategory.Appearance -> {
                val ap = access.appearancePrefs
                val np = access.neutralPrefs
                if (ap != null && np != null) {
                    AppearanceCategory(
                        prefs = ap,
                        neutral = np,
                        avatarPackPrefs = access.avatarPackPrefs,
                        packStore = access.packStore,
                        activeSpecies = access.activeAvatarSpecies,
                    )
                } else {
                    CategoryPlaceholder(stringResource(category.labelRes))
                }
            }
            SettingsCategory.About -> AboutCategory(
                onOpenLicenses = access.onOpenLicenses,
                onOpenRepo = access.onOpenRepoLink,
                onOpenPrivacyPolicy = access.onOpenPrivacyPolicy,
            )
            SettingsCategory.Mode -> access.modePrefs?.let { p ->
                ModeCategory(prefs = p)
            } ?: CategoryPlaceholder(stringResource(category.labelRes))
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
