package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.19 — RepoCard expand/collapse state. Body switches are absent
 * from the layout until the header is tapped; tapping again re-collapses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoCardExpandStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun non_write_target_repo_starts_collapsed_expands_on_header_tap() {
        val repo = RepoFixtures.localOnly("alpha")
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoCard(
                    repo = repo,
                    isWriteTarget = false,
                    onSelectWriteTarget = {},
                    onToggleShowOnSchedule = {},
                    onToggleDrawTasksFrom = {},
                    onToggleAutoSync = {},
                    onToggleWifiOnly = {},
                    onToggleImportStickers = {},
                    onOpenMoreSettings = {},
                )
            }
        }

        // Body switches hidden initially (AnimatedVisibility off => not in layout).
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchShowOnSchedule-alpha")
            .assertDoesNotExist()

        // Tap the header — body unfolds.
        composeRule.onNodeWithTag("$TestTagRepoCardHeader-alpha").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchShowOnSchedule-alpha")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchAutoSync-alpha")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("$TestTagRepoCardMoreSettings-alpha")
            .assertIsDisplayed()

        // Tap header again — body folds back.
        composeRule.onNodeWithTag("$TestTagRepoCardHeader-alpha").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchShowOnSchedule-alpha")
            .assertDoesNotExist()
    }

    @Test fun write_target_repo_starts_expanded() {
        val repo = RepoFixtures.localOnly("beta")
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoCard(
                    repo = repo,
                    isWriteTarget = true,
                    onSelectWriteTarget = {},
                    onToggleShowOnSchedule = {},
                    onToggleDrawTasksFrom = {},
                    onToggleAutoSync = {},
                    onToggleWifiOnly = {},
                    onToggleImportStickers = {},
                    onOpenMoreSettings = {},
                )
            }
        }
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchShowOnSchedule-beta")
            .assertIsDisplayed()
    }
}
