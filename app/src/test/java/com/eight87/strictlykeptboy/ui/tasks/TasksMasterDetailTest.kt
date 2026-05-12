package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.TestTagDetailPane
import com.eight87.strictlykeptboy.ui.adaptive.TestTagMasterDetailRow
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasksMasterDetailTest {
    @get:Rule val composeRule = createComposeRule()

    private fun renderAt(widthClass: WindowWidthSizeClass) {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(LocalWindowWidthSizeClass provides widthClass) {
                    TasksPane(activeRepoName = "demo-repo", state = TasksViewState())
                }
            }
        }
    }

    @Test fun compact_no_two_pane() {
        renderAt(WindowWidthSizeClass.Compact)
        composeRule.onNodeWithTag(TestTagTasksPane).assertExists()
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertDoesNotExist()
    }

    @Test fun medium_two_pane_with_empty_detail() {
        renderAt(WindowWidthSizeClass.Medium)
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagDetailPane).assertExists()
        composeRule.onNodeWithTag(TestTagTasksDetailEmpty).assertExists()
    }

    @Test fun expanded_two_pane_visible() {
        renderAt(WindowWidthSizeClass.Expanded)
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagDetailPane).assertExists()
    }
}
