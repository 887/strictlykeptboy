package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoSwitcherDropdownTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun lists_configured_repos_and_house_glyph_for_no_origin() {
        val repos = listOf(
            RepoFixtures.localOnly("local-1"),
            RepoFixtures.withRemote("remote-2"),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSwitcherDropdown(
                    repos = repos,
                    activeRepoId = "local-1",
                    statusFor = { if (it.remotes.isEmpty()) SyncStatus.LocalOnly else SyncStatus.Synced },
                    onSelect = {},
                    onAddRepo = {},
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagRepoSwitcherDropdown).assertIsDisplayed()
        composeRule.onNodeWithTag("$TestTagRepoSwitcherRow-local-1").assertIsDisplayed()
        composeRule.onNodeWithTag("$TestTagRepoSwitcherRow-remote-2").assertIsDisplayed()
        // House glyph appears for local-only repo.
        composeRule.onNodeWithTag(TestTagRepoSwitcherHouseGlyph, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun tap_row_invokes_onSelect() {
        var picked: String? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSwitcherDropdown(
                    repos = listOf(RepoFixtures.localOnly("a"), RepoFixtures.localOnly("b")),
                    activeRepoId = "a",
                    statusFor = { SyncStatus.LocalOnly },
                    onSelect = { picked = it },
                    onAddRepo = {},
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onNodeWithTag("$TestTagRepoSwitcherRow-b").performClick()
        assertEquals("b", picked)
    }

    @Test fun add_entry_invokes_onAddRepo() {
        var clicked = false
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSwitcherDropdown(
                    repos = listOf(RepoFixtures.localOnly("a")),
                    activeRepoId = "a",
                    statusFor = { SyncStatus.LocalOnly },
                    onSelect = {},
                    onAddRepo = { clicked = true },
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagRepoSwitcherAdd).performClick()
        assertTrue(clicked)
    }

    @Test fun settings_icon_invokes_onOpenSettings() {
        var openedFor: String? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSwitcherDropdown(
                    repos = listOf(RepoFixtures.localOnly("xyz")),
                    activeRepoId = "xyz",
                    statusFor = { SyncStatus.LocalOnly },
                    onSelect = {},
                    onAddRepo = {},
                    onOpenSettings = { openedFor = it },
                )
            }
        }
        composeRule.onNodeWithTag("$TestTagRepoSwitcherSettings-xyz").performClick()
        assertEquals("xyz", openedFor)
    }
}
