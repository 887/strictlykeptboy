package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoSettingsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun no_remotes_repo_shows_local_only_cta() {
        val repo = RepoFixtures.localOnly("local-x")
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSettingsScreen(
                    repo = repo,
                    onBack = {}, onUpdate = {},
                    onAddRemote = {}, onRemoveRemote = {}, onSetPrimaryRemote = {},
                    onRemoveRepo = {}, onOpenIdentities = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagRepoSettingsRemotes, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TestTagRepoSettingsNoRemotesCta, useUnmergedTree = true).assertExists()
    }

    @Test fun multi_remote_repo_renders_per_remote_rows() {
        val r1 = RemoteBinding(RemoteName.ORIGIN, "https://example.com/origin.git", Transport.HttpsOAuth, AuthMethod.OAuthGitHub)
        val r2 = RemoteBinding(RemoteName("mirror-1"), "https://example.com/mirror.git", Transport.HttpsPat, AuthMethod.ManualPat)
        val repo = RepoConfig(
            repoId = "multi", displayName = "Multi", rootDir = "/tmp/multi",
            remotes = listOf(r1, r2), primaryRemote = r1.name,
            authorIdentity = AuthorIdentity("a", "a@x.com"),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSettingsScreen(
                    repo = repo,
                    onBack = {}, onUpdate = {},
                    onAddRemote = {}, onRemoveRemote = {}, onSetPrimaryRemote = {},
                    onRemoveRepo = {}, onOpenIdentities = {},
                )
            }
        }
        composeRule.onNodeWithTag("$TestTagRepoSettingsRemoteRow-origin", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("$TestTagRepoSettingsRemoteRow-mirror-1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TestTagRepoSettingsAddRemote, useUnmergedTree = true).assertExists()
    }

    @Test fun remove_remote_invokes_callback() {
        val r = RemoteBinding(RemoteName.ORIGIN, "u", Transport.HttpsOAuth, AuthMethod.OAuthGitHub)
        val repo = RepoConfig(
            repoId = "r", displayName = "R", rootDir = "/tmp",
            remotes = listOf(r), primaryRemote = r.name,
            authorIdentity = AuthorIdentity("a", "a@x.com"),
        )
        var removedName: RemoteName? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSettingsScreen(
                    repo = repo, onBack = {}, onUpdate = {},
                    onAddRemote = {}, onRemoveRemote = { removedName = it },
                    onSetPrimaryRemote = {}, onRemoveRepo = {}, onOpenIdentities = {},
                )
            }
        }
        composeRule.onNodeWithTag("$TestTagRepoSettingsRemoveRemote-origin", useUnmergedTree = true)
            .performScrollTo().performClick()
        assertEquals(RemoteName.ORIGIN, removedName)
    }

    @Test fun remove_repo_dialog_invokes_callback_with_delete_local_flag() {
        val repo = RepoFixtures.localOnly("to-remove")
        var removed: Boolean? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSettingsScreen(
                    repo = repo, onBack = {}, onUpdate = {},
                    onAddRemote = {}, onRemoveRemote = {}, onSetPrimaryRemote = {},
                    onRemoveRepo = { removed = it }, onOpenIdentities = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagRepoSettingsRemoveRepo).performScrollTo().performClick()
        // Default unchecked → confirm.
        composeRule.onNodeWithTag(TestTagRepoSettingsConfirmRemove).performClick()
        assertNotNull(removed)
        assertEquals(false, removed)
    }
}
