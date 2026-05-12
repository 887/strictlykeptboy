package com.eight87.strictlykeptboy.ui.share

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareSheetTest {
    @get:Rule val composeRule = createComposeRule()

    private val origin = RemoteBinding(
        RemoteName.ORIGIN, "https://example.com/x.git",
        Transport.HttpsOAuth, AuthMethod.OAuthGitHub,
    )

    private fun repo(remotes: List<RemoteBinding> = listOf(origin)) = RepoConfig(
        repoId = "r1",
        displayName = "Repo One",
        rootDir = "/tmp",
        remotes = remotes,
        primaryRemote = remotes.firstOrNull()?.name,
        authorIdentity = AuthorIdentity("a", "a@x.com"),
    )

    @Test fun share_sheet_renders_with_default_read_only_link() {
        var copied: String? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ShareSheetContent(
                    repo = repo(),
                    onCopy = { copied = it },
                    onSend = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagShareLinkField, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TestTagShareCopy, useUnmergedTree = true).performClick()
        assertNotNull(copied)
        assertTrue("Got: $copied", copied!!.startsWith("strictlykeptboy://share?"))
        assertTrue(copied!!.contains("mode=read-only"))
    }

    @Test fun expiry_chip_changes_link() {
        val seen = mutableListOf<String>()
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ShareSheetContent(
                    repo = repo(),
                    onCopy = { seen.add(it) },
                    onSend = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagShareCopy, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTagShareExpiry7, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTagShareCopy, useUnmergedTree = true).performClick()
        assertTrue(seen.last().contains("expiry="))
    }

    @Test fun include_mirrors_toggle_only_visible_with_multiple_remotes() {
        val mirror = RemoteBinding(
            RemoteName("mirror"), "https://example.com/m.git",
            Transport.HttpsPat, AuthMethod.ManualPat,
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ShareSheetContent(
                    repo = repo(listOf(origin, mirror)),
                    onCopy = {}, onSend = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagShareIncludeMirrors, useUnmergedTree = true).assertExists()
    }
}
