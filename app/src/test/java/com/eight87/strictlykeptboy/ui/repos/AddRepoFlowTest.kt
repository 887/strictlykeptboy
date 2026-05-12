package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.eight87.strictlykeptboy.git.auth.DeviceFlowState
import com.eight87.strictlykeptboy.git.auth.OAuthToken
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddRepoFlowTest {
    @get:Rule val composeRule = createComposeRule()

    private fun ComposeContentTestRule.clickAdvance() {
        onNodeWithTag(TestTagAddRepoNext).performScrollTo().performClick()
    }

    @Test fun localOnly_flow_emits_LocalOnly_result() {
        var result: AddRepoResult? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                AddRepoNavHost(
                    onCancel = {},
                    onFinish = { result = it },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagAddRepoBranchLocal).performClick()
        composeRule.onNodeWithTag(TestTagAddRepoLocalForm).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagAddRepoDisplayField).performTextInput("My Diary")
        composeRule.onNodeWithTag(TestTagAddRepoNameField).performTextInput("Alex")
        composeRule.onNodeWithTag(TestTagAddRepoEmailField).performTextInput("a@x.com")
        composeRule.clickAdvance() // Finish

        assertTrue(result is AddRepoResult.LocalOnly)
        val r = result as AddRepoResult.LocalOnly
        assertEquals("My Diary", r.displayName)
        assertEquals("Alex", r.identity.name)
        assertEquals("a@x.com", r.identity.email)
    }

    @Test fun remote_pat_flow_emits_Remote_with_pat() {
        var result: AddRepoResult? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                AddRepoNavHost(
                    onCancel = {},
                    onFinish = { result = it },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagAddRepoBranchRemote).performClick()
        composeRule.onNodeWithTag(TestTagAddRepoRemoteProvider).assertIsDisplayed()
        composeRule.clickAdvance() // Next to auth
        composeRule.onNodeWithTag(TestTagAddRepoRemoteAuth).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagAddRepoAuthPat).performScrollTo().performClick()
        composeRule.clickAdvance() // Next to form
        composeRule.onNodeWithTag(TestTagAddRepoRemoteForm).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagAddRepoUrlField).performTextInput("https://example.com/r.git")
        composeRule.onNodeWithTag(TestTagAddRepoDisplayField).performTextInput("Remote A")
        composeRule.onNodeWithTag(TestTagAddRepoNameField).performTextInput("A")
        composeRule.onNodeWithTag(TestTagAddRepoEmailField).performTextInput("a@x.com")
        composeRule.onNodeWithTag(TestTagAddRepoUsernameField).performTextInput("git")
        composeRule.onNodeWithTag(TestTagAddRepoPatField).performTextInput("ghp_secret")
        composeRule.clickAdvance() // Finish

        val r = result
        assertTrue(r is AddRepoResult.Remote)
        r as AddRepoResult.Remote
        assertEquals(AddRepoProvider.GitHub, r.provider)
        assertEquals(AddRepoAuth.ManualPat, r.auth)
        assertEquals("https://example.com/r.git", r.repoUrl)
        assertNotNull(r.pat)
        assertEquals("ghp_secret", r.pat!!.token)
        assertNull(r.oauthToken)
    }

    @Test fun remote_oauth_flow_captures_token_from_immediate_success() {
        var result: AddRepoResult? = null
        val flow = flowOf<DeviceFlowState>(
            DeviceFlowState.Success(OAuthToken(accessToken = "tok-1")),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                AddRepoNavHost(
                    onCancel = {},
                    onFinish = { result = it },
                    deviceFlowFactory = { _, _ -> flow },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagAddRepoBranchRemote).performClick()
        composeRule.clickAdvance() // Next from provider to auth
        composeRule
            .onNodeWithTag(TestTagAddRepoOAuthProgress, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.clickAdvance() // Next to form
        composeRule.onNodeWithTag(TestTagAddRepoUrlField).performTextInput("https://example.com/r.git")
        composeRule.onNodeWithTag(TestTagAddRepoDisplayField).performTextInput("Remote B")
        composeRule.clickAdvance() // Finish

        val r = result
        assertTrue(r is AddRepoResult.Remote)
        r as AddRepoResult.Remote
        assertEquals(AddRepoAuth.OAuthDevice, r.auth)
        assertEquals("tok-1", r.oauthToken?.accessToken)
    }
}
