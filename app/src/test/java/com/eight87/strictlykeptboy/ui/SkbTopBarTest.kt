package com.eight87.strictlykeptboy.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.components.TestTagIdentityAvatar
import com.eight87.strictlykeptboy.ui.components.TestTagRepoSwitcher
import com.eight87.strictlykeptboy.ui.components.TestTagSyncButton
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbTopBar
import com.eight87.strictlykeptboy.ui.scaffold.TestTagViewTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkbTopBarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun renders_all_regions_and_day_default() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                SkbTopBar(
                    activeRepoName = "test-repo",
                    selectedViewTab = ScheduleViewTab.Day,
                    onSelectViewTab = {},
                    onRepoSwitcherClick = {},
                    onSyncClick = {},
                    onIdentityClick = {},
                )
            }
        }

        composeRule.onNodeWithTag(TestTagRepoSwitcher).assertExists()
        composeRule.onNodeWithText("test-repo").assertExists()
        composeRule.onNodeWithTag(TestTagSyncButton).assertExists()
        composeRule.onNodeWithTag(TestTagIdentityAvatar).assertExists()
        // Day tab is the initial selection.
        composeRule.onNodeWithTag("$TestTagViewTab-Day").assertIsSelected()
        ScheduleViewTab.entries.forEach {
            composeRule.onNodeWithTag("$TestTagViewTab-${it.name}").assertExists()
        }
    }
}
