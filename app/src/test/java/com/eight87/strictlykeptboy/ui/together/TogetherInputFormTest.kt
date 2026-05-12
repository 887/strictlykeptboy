package com.eight87.strictlykeptboy.ui.together

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TogetherInputFormTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun empty_repo_list_renders_empty_message() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                TogetherInputForm(
                    repoOptions = emptyList(),
                    state = TogetherInputState(),
                    onToggleRepo = {},
                    onToggleDay = {},
                    onUpdate = {},
                    onSubmit = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagTogetherEmptyRepos).assertIsDisplayed()
    }

    @Test fun three_repos_render_three_chips_and_toggle_updates_state() {
        val repos = listOf(
            TogetherRepoOption("repo-a", "Repo A"),
            TogetherRepoOption("repo-b", "Repo B"),
            TogetherRepoOption("repo-c", "Repo C"),
        )
        val toggled = mutableListOf<String>()
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                TogetherInputForm(
                    repoOptions = repos,
                    state = TogetherInputState(),
                    onToggleRepo = { toggled += it },
                    onToggleDay = {},
                    onUpdate = {},
                    onSubmit = {},
                )
            }
        }
        repos.forEach {
            composeRule.onNodeWithTag("$TestTagTogetherRepoChipPrefix${it.repoId}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("${TestTagTogetherRepoChipPrefix}repo-b").performClick()
        assertTrue(toggled == listOf("repo-b"))
    }
}
