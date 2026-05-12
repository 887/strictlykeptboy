package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.TestTagDetailPane
import com.eight87.strictlykeptboy.ui.adaptive.TestTagMasterDetailRow
import com.eight87.strictlykeptboy.ui.adaptive.TestTagMasterPane
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsMasterDetailTest {
    @get:Rule val composeRule = createComposeRule()

    private fun renderAt(widthClass: WindowWidthSizeClass) {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(LocalWindowWidthSizeClass provides widthClass) {
                    Box(Modifier.size(width = 800.dp, height = 1600.dp)) {
                        SettingsPane(importExportState = null)
                    }
                }
            }
        }
    }

    @Test fun compact_shows_category_list_then_pushes_on_tap() {
        renderAt(WindowWidthSizeClass.Compact)
        composeRule.onNodeWithTag(TestTagSettingsPane).assertExists()
        composeRule.onNodeWithTag(TestTagSettingsCategoryList).assertExists()
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertDoesNotExist()
        // Tap the Repos category — content swaps in (single-pane push).
        composeRule.onNodeWithTag("${TestTagSettingsCategoryPrefix}Repos").performClick()
        composeRule.onNodeWithTag(TestTagSettingsContent).assertExists()
        composeRule.onNodeWithTag(TestTagSettingsBack).assertExists()
    }

    @Test fun medium_shows_two_pane() {
        renderAt(WindowWidthSizeClass.Medium)
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagMasterPane).assertExists()
        composeRule.onNodeWithTag(TestTagDetailPane).assertExists()
        composeRule.onNodeWithTag(TestTagSettingsCategoryList).assertExists()
        composeRule.onNodeWithTag(TestTagSettingsContent).assertExists()
    }
}
