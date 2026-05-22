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
import com.eight87.strictlykeptboy.ui.components.CategoryAccentName
import com.eight87.strictlykeptboy.ui.components.CategoryIconCircle
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import com.eight87.strictlykeptboy.ui.settings.categories.AboutCategory
import com.eight87.strictlykeptboy.ui.settings.categories.AppearanceCategory
import com.eight87.strictlykeptboy.ui.settings.categories.CalendarsCategory
import com.eight87.strictlykeptboy.ui.settings.categories.CalDavCategory
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
/**
 * @param labelRes title shown in the category list + content header
 * @param testTag stable id for tests (DO NOT translate)
 * @param searchKeywordRes keyword string-resource ids indexed by the
 *   outer settings search (2.1.E.9). Each resource may contain a
 *   comma-separated keyword list. Empty list = label + subtitle only.
 */
sealed class SettingsCategory(
    val labelRes: Int,
    val testTag: String,
    val searchKeywordRes: List<Int> = emptyList(),
) {
    object Repos : SettingsCategory(R.string.settings_category_repos, "Repos")
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
    /** 2.1.E.12 — CalDAV stub category, sits under Behaviour. */
    object CalDav : SettingsCategory(R.string.settings_category_caldav, "CalDav")
    /** 2.2.D.6 — Access aggregator (sits between Behaviour and Lifestyle). */
    object Access : SettingsCategory(R.string.settings_category_access, "Access")
    /** 2.2.D.7 — Android Auto + tablet master-detail preferences. */
    object AutoTablet : SettingsCategory(R.string.settings_category_autotablet, "AutoTablet")
    /**
     * Round 2.17 Phase E.2 — Storage category. Replaces the 2.7.D
     * `BackupLocation` surface; the `testTag` is bumped to `"Storage"`
     * so existing UI tests targeting the old tag fail loud rather than
     * landing on the wrong screen. Tap routes to the in-pane
     * StorageFolderScreen / AdoptExistingSheet / BackupRestoreScreen
     * sub-flows owned by `SettingsCategoryContent`.
     */
    object Storage : SettingsCategory(R.string.settings_category_storage, "Storage")

    /**
     * Round 2.18.B.3 — External (system) calendars entry-point.
     * Renders [ExternalCalendarsScreen] for the global toggle +
     * permission flow + per-calendar visibility list.
     */
    object ExternalCalendars : SettingsCategory(R.string.settings_category_external_calendars, "ExternalCalendars")

    companion object {
        val all: List<SettingsCategory> by lazy {
            listOf(
                Repos, Sync, Notifications, Calendars, Todolists,
                Templates, Lifestyle, Identity, Appearance, About, Mode, CalDav,
                Access, AutoTablet, Storage, ExternalCalendars,
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
    /**
     * Round 2.1.B.11 — cross-repo calendars feed. When set, the
     * Calendars settings category renders the master list across all
     * repos via `CalendarsCategoryMaster`.
     */
    val calendarsFlow: kotlinx.coroutines.flow.StateFlow<List<com.eight87.strictlykeptboy.resolver.CalendarMeta>>? = null,
    /** Row-tap → open CalendarSettingsSheet. */
    val onEditCalendar: (com.eight87.strictlykeptboy.resolver.CalendarMeta) -> Unit = {},
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
    // 2.2.D.2 — Repos as in-pane list.
    val reposFlow: kotlinx.coroutines.flow.StateFlow<List<com.eight87.strictlykeptboy.git.RepoConfig>>? = null,
    /**
     * Round 2.23 Phase E (D-2.23.e) — Reviews destination feed. Host
     * collects review entries via [com.eight87.strictlykeptboy.ui.reviews.ReviewFeedReader]
     * and exposes them here. `null` ⇒ empty-state fallback (Phase DDD.13
     * behaviour preserved).
     */
    val reviewItemsFlow: kotlinx.coroutines.flow.StateFlow<List<com.eight87.strictlykeptboy.ui.reviews.ReviewEntry>>? = null,
    val onOpenRepo: (com.eight87.strictlykeptboy.git.RepoConfig) -> Unit = {},
    // 2.2.D.6 — Access aggregator.
    val accessAggregator: com.eight87.strictlykeptboy.store.AccessAggregator? = null,
    val onOpenShareFor: (String) -> Unit = {},
    // 2.2.D.7 — Auto & Tablet prefs.
    val autoTabletPrefs: AutoTabletPrefs? = null,
    // 2.2.D.13 — Trip-summary feed for the Lifestyle card.
    val tripFeed: com.eight87.strictlykeptboy.ui.trip.TripFeed? = null,
    val onOpenTrip: (com.eight87.strictlykeptboy.ui.trip.TripSummary) -> Unit = {},
    // Round 2.7.B.4-UI — backup-folder mirror prefs + picker handle.
    // Round 2.17 Phase E.2 — `onPickBackupFolder` keeps its name for
    // back-compat with the Repos-pane reminder banner wiring; it fires
    // the parent SAF picker. `onRemoveBackupFolder` is gone in favour
    // of the Storage screen's "Switch to internal" button.
    val repoStoragePrefs: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs? = null,
    val onPickBackupFolder: () -> Unit = {},
    val onRemoveBackupFolder: () -> Unit = {},
    /**
     * Round 2.17 Phase E.4 — "Adopt existing folder" picker. Fires a
     * SAF picker that DOES NOT change the parent; instead the caller
     * runs `ParentReconciler.reconcileExternal(uri)` against the
     * picked folder and presents an in-sheet confirmation list.
     */
    val onAdoptExistingFolder: () -> Unit = {},
    /**
     * Round 2.17 Phase E.5 — "Change folder" entry-point from the
     * StorageFolderScreen. Fires the SAF picker via the activity, but
     * the activity routes the grant through `RepoMover` instead of
     * setting prefs in place (distinct from `onPickBackupFolder`,
     * which is the wizard / banner / legacy-set-in-place path).
     */
    val onChangeStorageFolder: () -> Unit = {},
    /**
     * Round 2.17 Phase E.7 — observable "SAF permission revoked" flag
     * for the Repos pane banner. When true, the red banner shows.
     */
    val safPermissionRevokedFlow: kotlinx.coroutines.flow.StateFlow<Boolean>? = null,
    /**
     * Round 2.17 Phase E.5 — "Switch to internal" / "Change folder"
     * routes both trigger a [com.eight87.strictlykeptboy.sync.RepoMover]
     * run. The caller owns the worker instance + the move-job dialog
     * UI; this callback just kicks off the move with the resolved
     * `newParentLocation`.
     */
    val onSwitchToInternal: () -> Unit = {},
    /**
     * Round 2.17 Phase F.3 — "Export backup" row in BackupRestoreScreen.
     * Fires the SAF `CreateDocument("application/gzip")` picker via the
     * activity. The activity (Phase F.4) then writes the tar.gz on
     * `Dispatchers.IO` once the user grants a destination URI.
     */
    val onExportBackup: () -> Unit = {},
    /**
     * Round 2.17 Phase G.3 — "Restore from current folder" row in
     * BackupRestoreScreen. The dialog has already confirmed when this
     * fires; callers do the rescan + RepoStore.replaceAll + cache
     * invalidation on Dispatchers.IO.
     */
    val onRestoreFromFolder: () -> Unit = {},
    /**
     * Round 2.17 Phase G.3 — "Restore from backup archive" row in
     * BackupRestoreScreen. The dialog has already confirmed when this
     * fires; the activity parks a handler on the SAF
     * OpenDocument launcher and consumes the picked URI.
     */
    val onPickRestoreArchive: () -> Unit = {},
    // Round 2.15 — demo-mode toggle prefs.
    val demoModePrefs: com.eight87.strictlykeptboy.prefs.DemoModePrefs? = null,
    /**
     * Round 2.18.B — handles for the External Calendars settings screen.
     * Both nullable so callers that don't wire the system-calendar
     * stack still compile; the screen falls back to a diagnostic
     * banner just like other categories with missing prefs.
     */
    val systemCalendarPrefs: com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore? = null,
    val systemCalendarsFlow: kotlinx.coroutines.flow.StateFlow<List<com.eight87.strictlykeptboy.system.SystemCalendar>>? = null,
    /**
     * Round 2.18.G.6 — fires when "Make skb visible to other Android
     * apps" toggle flips. Caller invokes
     * `SkbAccountManager.enableForAllRepos()` / `disableForAllRepos()`.
     */
    val onPublishToOsChanged: (Boolean) -> Unit = { _ -> },
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
            onJumpToLifestyle = {
                selectedTag = SettingsCategory.Lifestyle.testTag
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
    val accent: CategoryAccentName,
)

/**
 * Non-composable subtitle lookup for outer-search indexing (2.1.E.9).
 * Mirrors `metaFor` but does not depend on `MaterialTheme`, so it can
 * be called from `remember { … }` blocks.
 */
private fun subtitleResFor(cat: SettingsCategory): Int = when (cat) {
    SettingsCategory.Appearance -> R.string.settings_subtitle_appearance
    SettingsCategory.Repos -> R.string.settings_subtitle_repos
    SettingsCategory.Calendars -> R.string.settings_subtitle_calendars
    SettingsCategory.Todolists -> R.string.settings_subtitle_todolists
    SettingsCategory.Templates -> R.string.settings_subtitle_templates
    SettingsCategory.Sync -> R.string.settings_subtitle_sync
    SettingsCategory.Notifications -> R.string.settings_subtitle_notifications
    SettingsCategory.Mode -> R.string.settings_subtitle_mode
    SettingsCategory.Lifestyle -> R.string.settings_subtitle_lifestyle
    SettingsCategory.Identity -> R.string.settings_subtitle_identity
    SettingsCategory.About -> R.string.settings_subtitle_about
    SettingsCategory.CalDav -> R.string.settings_subtitle_caldav
    SettingsCategory.Access -> R.string.settings_subtitle_access
    SettingsCategory.AutoTablet -> R.string.settings_subtitle_autotablet
    SettingsCategory.Storage -> R.string.settings_subtitle_storage
    SettingsCategory.ExternalCalendars -> R.string.settings_subtitle_external_calendars_off
}

@Composable
private fun metaFor(cat: SettingsCategory): CategoryMeta {
    val cs = MaterialTheme.colorScheme
    return when (cat) {
        SettingsCategory.Appearance ->
            CategoryMeta(Icons.Filled.Palette, R.string.settings_subtitle_appearance, cs.tertiary, CategoryAccentName.Magenta)
        SettingsCategory.Repos ->
            CategoryMeta(Icons.Filled.FolderShared, R.string.settings_subtitle_repos, cs.primary, CategoryAccentName.SkyBlue)
        SettingsCategory.CalDav ->
            CategoryMeta(Icons.Filled.Sync, R.string.settings_subtitle_caldav, cs.tertiary, CategoryAccentName.SkyBlue)
        SettingsCategory.Calendars ->
            CategoryMeta(Icons.Filled.CalendarMonth, R.string.settings_subtitle_calendars, cs.primary, CategoryAccentName.Teal)
        SettingsCategory.Todolists ->
            CategoryMeta(Icons.Filled.Checklist, R.string.settings_subtitle_todolists, cs.secondary, CategoryAccentName.Green)
        SettingsCategory.Templates ->
            CategoryMeta(Icons.Filled.Description, R.string.settings_subtitle_templates, cs.tertiary, CategoryAccentName.Orange)
        SettingsCategory.Sync ->
            CategoryMeta(Icons.Filled.Sync, R.string.settings_subtitle_sync, cs.primary, CategoryAccentName.SkyBlue)
        SettingsCategory.Notifications ->
            CategoryMeta(Icons.Filled.Notifications, R.string.settings_subtitle_notifications, cs.secondary, CategoryAccentName.Red)
        SettingsCategory.Mode ->
            CategoryMeta(Icons.Filled.Tune, R.string.settings_subtitle_mode, cs.tertiary, CategoryAccentName.Indigo)
        SettingsCategory.Lifestyle ->
            CategoryMeta(Icons.Filled.Favorite, R.string.settings_subtitle_lifestyle, cs.primary, CategoryAccentName.Purple)
        SettingsCategory.Identity ->
            CategoryMeta(Icons.Filled.Person, R.string.settings_subtitle_identity, cs.secondary, CategoryAccentName.Pink)
        SettingsCategory.About ->
            CategoryMeta(Icons.Filled.Info, R.string.settings_subtitle_about, cs.tertiary, CategoryAccentName.Orange)
        SettingsCategory.Access ->
            CategoryMeta(Icons.Filled.FolderShared, R.string.settings_subtitle_access, cs.secondary, CategoryAccentName.Amber)
        SettingsCategory.AutoTablet ->
            CategoryMeta(Icons.Filled.Tune, R.string.settings_subtitle_autotablet, cs.tertiary, CategoryAccentName.Cyan)
        SettingsCategory.Storage ->
            CategoryMeta(Icons.Filled.FolderShared, R.string.settings_subtitle_storage, cs.primary, CategoryAccentName.Brown)
        SettingsCategory.ExternalCalendars ->
            CategoryMeta(Icons.Filled.CalendarMonth, R.string.settings_subtitle_external_calendars_off, cs.tertiary, CategoryAccentName.SkyBlue)
    }
}

private data class SettingsSection(val titleRes: Int, val items: List<SettingsCategory>)

private val sections: List<SettingsSection> = listOf(
    SettingsSection(
        R.string.settings_section_appearance_header,
        listOf(SettingsCategory.Appearance),
    ),
    // 2.1.E.5 — Identities stub retired; Identity promoted with sub-sections (see IdentityCategory).
    SettingsSection(
        R.string.settings_section_library,
        listOf(
            SettingsCategory.Repos,
            SettingsCategory.Calendars,
            SettingsCategory.Todolists,
            SettingsCategory.Templates,
            // Round 2.18.B.3 — External (CalendarContract) calendars row.
            SettingsCategory.ExternalCalendars,
        ),
    ),
    // 2.1.E.12 — CalDAV stub category lives under Behaviour.
    // 2.2.D.7 — Auto & Tablet sits at the end of Behaviour.
    SettingsSection(
        R.string.settings_section_behaviour,
        listOf(
            SettingsCategory.Sync,
            SettingsCategory.Notifications,
            SettingsCategory.Mode,
            SettingsCategory.CalDav,
            SettingsCategory.Access,
            SettingsCategory.AutoTablet,
            SettingsCategory.Storage,
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
    // 2.1.E.9 — index label + subtitle + per-category searchKeywordRes
    // (mirrors AppearanceCategory's pattern). Drops the testTag-substring
    // fallback so the user typing "neutral" or "tablet" gets sensible hits.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val visibleSections = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) sections
        else sections.mapNotNull { sec ->
            val kept = sec.items.filter { cat ->
                val label = ctx.getString(cat.labelRes).lowercase()
                val subtitle = ctx.getString(subtitleResFor(cat)).lowercase()
                val keywords = cat.searchKeywordRes
                    .joinToString(" ") { ctx.getString(it) }
                    .lowercase()
                label.contains(q) || subtitle.contains(q) || keywords.contains(q)
            }
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
                            CategoryIconCircle(
                                icon = meta.icon,
                                accentName = meta.accent,
                                contentDescription = null,
                            )
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
    onJumpToLifestyle: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().testTag(TestTagSettingsContent)) {
        when (category) {
            // 2.2.D.2 — in-pane list of repo rows + Import/Export sub-card.
            SettingsCategory.Repos -> ReposCategory(
                reposFlow = access.reposFlow,
                importExportState = importExportState,
                onPickImportFile = onPickImportFile,
                onPickExportFile = onPickExportFile,
                onOpenRepo = access.onOpenRepo,
                onOpenReposList = access.onOpenReposList,
            )
            SettingsCategory.Sync -> access.syncPrefs?.let { p ->
                com.eight87.strictlykeptboy.ui.settings.categories.SyncCategory(
                    prefs = p,
                    statusStore = access.statusStore,
                )
            } ?: DiagnosticMissingPrefBanner(category, "syncPrefs")
            SettingsCategory.Notifications -> access.notificationPrefs?.let { p ->
                NotificationsCategory(prefs = p, calendarsFlow = access.calendarsFlow)
            } ?: DiagnosticMissingPrefBanner(category, "notificationPrefs")
            SettingsCategory.Calendars -> access.calendarVisibility?.let { p ->
                val flow = access.calendarsFlow
                if (flow != null) {
                    com.eight87.strictlykeptboy.ui.settings.categories.CalendarsCategoryMaster(
                        prefs = p,
                        calendarsFlow = flow,
                        onEditCalendar = access.onEditCalendar,
                    )
                } else {
                    CalendarsCategory(prefs = p)
                }
            } ?: CategoryPlaceholder(stringResource(category.labelRes))
            SettingsCategory.Todolists -> access.todolistVisibility?.let { p ->
                TodolistsCategory(prefs = p)
            } ?: DiagnosticMissingPrefBanner(category, "todolistVisibility")
            SettingsCategory.Templates -> TemplatesCategory(
                templateIds = access.templateIds,
                onApply = access.onApplyTemplate,
                onSaveCustomUrl = access.onSaveCustomTemplateUrl,
            )
            // 2.1.E.4 — Neutral mode toggle relocates to Lifestyle.
            // 2.1.E.13 — Trip-summary card hook reserved (rendered as bare-CTA today).
            SettingsCategory.Lifestyle -> LifestyleCategory(
                onOpenWizardAtRoles = access.onOpenWizardAtRoles,
                onPlanTrip = access.onPlanTrip,
                onOpenTrip = access.onOpenTrip,
                neutralPrefs = access.neutralPrefs,
                tripFeed = access.tripFeed,
            )
            // 2.1.E.5 — IdentityCategory renders sub-sections ("My persona" + "Signing & authors").
            SettingsCategory.Identity -> access.identityPrefs?.let { p ->
                IdentityCategory(prefs = p)
            } ?: DiagnosticMissingPrefBanner(category, "identityPrefs")
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
                        // 2.1.E.4 — surface a deeplink chip back to Lifestyle so
                        // outer search still finds "neutral" / "kink" here.
                        onJumpToLifestyleNeutral = onJumpToLifestyle,
                    )
                } else {
                    DiagnosticMissingPrefBanner(
                        category,
                        if (ap == null) "appearancePrefs" else "neutralPrefs",
                    )
                }
            }
            SettingsCategory.About -> AboutCategory(
                onOpenLicenses = access.onOpenLicenses,
                onOpenRepo = access.onOpenRepoLink,
                onOpenPrivacyPolicy = access.onOpenPrivacyPolicy,
            )
            SettingsCategory.Mode -> access.modePrefs?.let { p ->
                ModeCategory(prefs = p)
            } ?: DiagnosticMissingPrefBanner(category, "modePrefs")
            // 2.1.E.12 — CalDAV stub category.
            SettingsCategory.CalDav -> CalDavCategory()
            // 2.2.D.6 — Access aggregator (read-only).
            SettingsCategory.Access -> com.eight87.strictlykeptboy.ui.settings.categories.AccessCategory(
                rows = access.accessAggregator?.aggregate().orEmpty(),
                onOpenShareFor = access.onOpenShareFor,
            )
            // 2.2.D.7 — Auto & Tablet preferences.
            SettingsCategory.AutoTablet -> access.autoTabletPrefs?.let { p ->
                com.eight87.strictlykeptboy.ui.settings.categories.AutoTabletCategory(
                    prefs = p,
                    reposFlow = access.reposFlow,
                )
            } ?: DiagnosticMissingPrefBanner(category, "autoTabletPrefs")
            // Round 2.17 Phase E.2 — Storage category (3-row entry; the
            // active sub-screen is held in `storageSubScreen` state so
            // tapping a row swaps the content pane without changing the
            // outer category selection).
            // Round 2.18.B.3 — External (system) calendars screen.
            SettingsCategory.ExternalCalendars -> {
                val sp = access.systemCalendarPrefs
                val sf = access.systemCalendarsFlow
                if (sp != null && sf != null) {
                    ExternalCalendarsScreen(
                        prefs = sp,
                        systemCalendarsFlow = sf,
                        onPublishToOsChanged = access.onPublishToOsChanged,
                    )
                } else {
                    DiagnosticMissingPrefBanner(
                        category,
                        if (sp == null) "systemCalendarPrefs" else "systemCalendarsFlow",
                    )
                }
            }
            SettingsCategory.Storage -> access.repoStoragePrefs?.let { p ->
                StorageCategoryHost(
                    prefs = p,
                    reposFlow = access.reposFlow,
                    onChangeFolder = access.onChangeStorageFolder,
                    onAdoptExistingFolder = access.onAdoptExistingFolder,
                    onSwitchToInternal = access.onSwitchToInternal,
                    onExportBackup = access.onExportBackup,
                    onRestoreFromFolder = access.onRestoreFromFolder,
                    onPickRestoreArchive = access.onPickRestoreArchive,
                )
            } ?: DiagnosticMissingPrefBanner(category, "repoStoragePrefs")
        }
    }
}

/**
 * 2.1.E.10 — Diagnostic banner replacing the silent
 * `CategoryPlaceholder` for categories whose pref handle is null.
 * Names the missing handle so the wire-up gap is obvious instead of
 * looking identical to a feature with no content yet.
 */
@Composable
private fun DiagnosticMissingPrefBanner(category: SettingsCategory, handleName: String) {
    val label = stringResource(category.labelRes)
    androidx.compose.runtime.SideEffect {
        android.util.Log.w(
            "SettingsAccess",
            "Category ${category.testTag} ($label) rendered without its `$handleName` pref handle wired into SettingsAccess",
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.settings_diagnostic_missing_pref_title, label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(R.string.settings_diagnostic_missing_pref_body, label, handleName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


/**
 * Round 2.17 Phase E.2/E.3 — Storage category host. Renders the
 * top-level row list and swaps to a sub-screen when one is open. State
 * is local to the host so navigating away + back returns the user to
 * the rows.
 */
@Composable
private fun StorageCategoryHost(
    prefs: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs,
    reposFlow: kotlinx.coroutines.flow.StateFlow<List<com.eight87.strictlykeptboy.git.RepoConfig>>?,
    onChangeFolder: () -> Unit,
    onAdoptExistingFolder: () -> Unit,
    onSwitchToInternal: () -> Unit,
    onExportBackup: () -> Unit = {},
    onRestoreFromFolder: () -> Unit = {},
    onPickRestoreArchive: () -> Unit = {},
) {
    var sub by remember { mutableStateOf(StorageSubScreen.Rows) }
    when (sub) {
        StorageSubScreen.Rows -> com.eight87.strictlykeptboy.ui.settings.categories.StorageCategory(
            prefs = prefs,
            onOpenStorageFolder = { sub = StorageSubScreen.Folder },
            onAdoptExistingFolder = onAdoptExistingFolder,
            onOpenBackupRestore = { sub = StorageSubScreen.BackupRestore },
        )
        StorageSubScreen.Folder -> {
            val flow = reposFlow ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
            StorageFolderScreen(
                prefs = prefs,
                reposFlow = flow,
                onChangeFolder = onChangeFolder,
                onSwitchToInternal = onSwitchToInternal,
            )
        }
        StorageSubScreen.BackupRestore -> BackupRestoreScreen(
            onExportBackup = onExportBackup,
            onRestoreFromFolder = onRestoreFromFolder,
            onPickRestoreArchive = onPickRestoreArchive,
        )
    }
}

private enum class StorageSubScreen { Rows, Folder, BackupRestore }

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
