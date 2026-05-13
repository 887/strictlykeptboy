package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()
    private fun freshPrefs(name: String) = ctx()
        .getSharedPreferences(name, Context.MODE_PRIVATE)
        .also { it.edit().clear().apply() }

    private fun fullAccess(): SettingsAccess = SettingsAccess(
        syncPrefs = SyncSettingsPrefs.openForTest(freshPrefs("sync_nav")),
        notificationPrefs = NotificationPrefs.openForTest(freshPrefs("notif_nav")),
        calendarVisibility = CalendarVisibilityPrefs.openForTest(freshPrefs("cal_nav"), ListKind.Calendars),
        todolistVisibility = CalendarVisibilityPrefs.openForTest(freshPrefs("td_nav"), ListKind.Todolists),
        identityPrefs = IdentityPrefs.openForTest(freshPrefs("id_nav")),
        appearancePrefs = AppearancePrefs.openForTest(freshPrefs("app_nav")),
        neutralPrefs = NeutralModePrefs.openForTest(freshPrefs("neu_nav")),
        modePrefs = ModePrefs.openForTest(freshPrefs("mode_nav")),
        templateIds = listOf("atomic-medical", "atomic-flight"),
    )

    private fun renderAt(widthClass: WindowWidthSizeClass) {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(LocalWindowWidthSizeClass provides widthClass) {
                    // Box with bounded size — the category Composables nest a
                    // verticalScroll inside fillMaxSize, so the host needs a
                    // finite height for Robolectric to lay it out.
                    Box(Modifier.size(width = 800.dp, height = 1600.dp)) {
                        SettingsPane(importExportState = null, access = fullAccess())
                    }
                }
            }
        }
    }

    // 2.1.E.5 — Identities stub retired; folded into Identity → Signing
    // sub-section. 2.1.E.12 — CalDAV stub category added under Behaviour.
    private val allCategoryTags = listOf(
        "Repos", "Sync", "Notifications",
        "Calendars", "Todolists", "Templates", "Lifestyle",
        "Identity", "Appearance", "About", "Mode", "CalDav",
    )

    @Test fun compact_routes_each_category_to_stacked_content() {
        renderAt(WindowWidthSizeClass.Compact)
        for (tag in allCategoryTags) {
            composeRule.onNodeWithTag(TestTagSettingsCategoryList)
                .performScrollToNode(hasTestTag("$TestTagSettingsCategoryPrefix$tag"))
            composeRule.onNodeWithTag("$TestTagSettingsCategoryPrefix$tag").performClick()
            composeRule.onNodeWithTag(TestTagSettingsContent).assertExists()
            composeRule.onNodeWithTag(TestTagSettingsBack).assertExists()
            composeRule.onNodeWithTag(TestTagSettingsBack).performClick()
        }
    }

    @Test fun medium_renders_master_detail_for_each_category() {
        renderAt(WindowWidthSizeClass.Medium)
        composeRule.onNodeWithTag(TestTagSettingsContent).assertExists()
        for (tag in allCategoryTags) {
            composeRule.onNodeWithTag(TestTagSettingsCategoryList)
                .performScrollToNode(hasTestTag("$TestTagSettingsCategoryPrefix$tag"))
            composeRule.onNodeWithTag("$TestTagSettingsCategoryPrefix$tag").performClick()
            composeRule.onNodeWithTag(TestTagSettingsContent).assertExists()
        }
    }
}
