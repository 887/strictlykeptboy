package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReposPaneTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun empty_state_shown_when_no_repos() {
        val store = RepoFixtures.store("repos_empty")
        val state = ReposViewState(store)
        composeRule.setContent {
            StrictlyKeptBoyTheme { ReposPane(state = state) }
        }
        composeRule.onNodeWithTag(TestTagReposPaneEmpty).assertIsDisplayed()
    }

    @Test fun add_local_only_persists_repo_to_store() = runTest {
        val store = RepoFixtures.store("repos_add_persist")
        val secrets = RepoFixtures.secretsStore("secrets_add_persist")
        val state = ReposViewState(store)

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ReposPane(state = state, secretsStore = secrets)
            }
        }
        composeRule.onNodeWithTag(TestTagRepoSwitcherAdd, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTagAddRepoBranchLocal, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTagAddRepoLocalForm, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagAddRepoNext).performScrollTo().performClick()

        composeRule.waitForIdle()
        repeat(20) {
            if (store.list().isNotEmpty()) return@repeat
            composeRule.mainClock.advanceTimeBy(50)
            composeRule.waitForIdle()
        }
        assertEquals(1, store.list().size)
        assertTrue(store.list().first().remotes.isEmpty())
        assertEquals(store.list().first().repoId, state.activeRepoId.value)
    }

    @Test fun add_remote_pat_persists_pat_credential() = runTest {
        val store = RepoFixtures.store("repos_pat")
        val secrets = RepoFixtures.secretsStore("secrets_pat")
        val state = ReposViewState(store)

        composeRule.setContent {
            StrictlyKeptBoyTheme { ReposPane(state = state, secretsStore = secrets) }
        }
        composeRule.onNodeWithTag(TestTagRepoSwitcherAdd, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTagAddRepoBranchRemote, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTagAddRepoNext).performScrollTo().performClick()
        composeRule.onNodeWithTag(TestTagAddRepoRemoteAuth).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagAddRepoAuthPat).performScrollTo().performClick()
        composeRule.onNodeWithTag(TestTagAddRepoNext).performScrollTo().performClick()

        composeRule.onNodeWithTag(TestTagAddRepoUrlField)
            .performTextInput("https://example.com/r.git")
        composeRule.onNodeWithTag(TestTagAddRepoPatField).performTextInput("ghp_secret")
        composeRule.onNodeWithTag(TestTagAddRepoNext).performScrollTo().performClick()

        composeRule.waitForIdle()
        repeat(20) {
            if (store.list().isNotEmpty()) return@repeat
            composeRule.mainClock.advanceTimeBy(50)
            composeRule.waitForIdle()
        }
        assertEquals(1, store.list().size)
        val cfg = store.list().first()
        val pat = secrets.getPat(cfg.repoId, cfg.primaryRemote!!)
        assertEquals("ghp_secret", pat?.token)
    }
}
