package com.eight87.strictlykeptboy.ui.together

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TogetherEmptyStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun empty_state_renders_bat_and_message() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                TogetherEmptyState()
            }
        }
        composeRule.onNodeWithTag(TestTagTogetherEmptyBat).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagTogetherEmptyMessage).assertIsDisplayed()
    }
}
