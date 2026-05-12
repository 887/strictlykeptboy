package com.eight87.strictlykeptboy.ui.share

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
import com.eight87.strictlykeptboy.ui.repos.RepoSettingsScreen
import com.eight87.strictlykeptboy.ui.repos.TestTagRepoSettingsRemoteReadOnlyToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadOnlyToggleTest {
    @get:Rule val composeRule = createComposeRule()

    private fun repo(treat: Boolean = false, detected: Boolean = false): RepoConfig {
        val r = RemoteBinding(
            RemoteName.ORIGIN,
            "https://example.com/x.git",
            Transport.HttpsOAuth,
            AuthMethod.OAuthGitHub,
            readOnlyDetected = detected,
            treatAsReadOnly = treat,
        )
        return RepoConfig(
            repoId = "r",
            displayName = "R",
            rootDir = "/tmp",
            remotes = listOf(r),
            primaryRemote = r.name,
            authorIdentity = AuthorIdentity("a", "a@x.com"),
        )
    }

    @Test fun banner_shown_when_user_treats_as_read_only() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSettingsScreen(
                    repo = repo(treat = true),
                    onBack = {}, onUpdate = {},
                    onAddRemote = {}, onRemoveRemote = {}, onSetPrimaryRemote = {},
                    onRemoveRepo = {}, onOpenIdentities = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagReadOnlyBanner, useUnmergedTree = true).assertExists()
    }

    @Test fun banner_shown_when_auto_detected_read_only() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSettingsScreen(
                    repo = repo(detected = true),
                    onBack = {}, onUpdate = {},
                    onAddRemote = {}, onRemoveRemote = {}, onSetPrimaryRemote = {},
                    onRemoveRepo = {}, onOpenIdentities = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagReadOnlyBanner, useUnmergedTree = true).assertExists()
    }

    @Test fun toggle_invokes_callback_with_remote_name_and_value() {
        val r = repo(treat = false)
        var captured: Pair<RemoteName, Boolean>? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSettingsScreen(
                    repo = r,
                    onBack = {}, onUpdate = {},
                    onAddRemote = {}, onRemoveRemote = {}, onSetPrimaryRemote = {},
                    onRemoveRepo = {}, onOpenIdentities = {},
                    onToggleRemoteReadOnly = { name, v -> captured = name to v },
                )
            }
        }
        composeRule.onNodeWithTag(
            "$TestTagRepoSettingsRemoteReadOnlyToggle-origin",
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        assertEquals(RemoteName.ORIGIN, captured?.first)
        assertEquals(true, captured?.second)
    }
}
