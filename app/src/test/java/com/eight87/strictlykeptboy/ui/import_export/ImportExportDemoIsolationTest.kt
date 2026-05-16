package com.eight87.strictlykeptboy.ui.import_export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.20.1 Phase B — demo repos (isDemo = true) MUST NOT appear in
 * the Import/Export screen's repo list. They live in app-internal asset
 * storage, not on user-writable disk, so "Export .ics" against them
 * would either crash or, worse, silently succeed against bytes the user
 * cannot inspect / share / version-control. The fix is in
 * [ImportExportScreen] which filters `!isDemo` at consumption time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportExportDemoIsolationTest {

    @get:Rule val composeRule = createComposeRule()

    private fun repo(repoId: String, isDemo: Boolean) = RepoConfig(
        repoId = repoId,
        displayName = repoId,
        rootDir = "/tmp/skb-$repoId",
        remotes = emptyList(),
        primaryRemote = null,
        authorIdentity = AuthorIdentity("me", "me@example.com"),
        isDemo = isDemo,
    )

    @Test fun demo_repo_does_not_appear_in_repo_list() {
        val real = repo("real-repo", isDemo = false)
        val demo = repo("rich-demo", isDemo = true)
        val flow = MutableStateFlow(listOf(real, demo))
        val state = ImportExportViewState(repos = flow, onConfirmedImport = {})
        composeRule.setContent {
            ImportExportScreen(state, onPickImportFile = {}, onPickExportFile = {})
        }
        composeRule.onNodeWithTag("$TestTagExportButtonPrefix${real.repoId}").assertIsDisplayed()
        composeRule.onNodeWithTag("$TestTagExportButtonPrefix${demo.repoId}").assertDoesNotExist()
    }

    @Test fun all_demo_renders_empty_state() {
        val demo = repo("rich-demo", isDemo = true)
        val flow = MutableStateFlow(listOf(demo))
        val state = ImportExportViewState(repos = flow, onConfirmedImport = {})
        composeRule.setContent {
            ImportExportScreen(state, onPickImportFile = {}, onPickExportFile = {})
        }
        composeRule.onNodeWithTag("$TestTagExportButtonPrefix${demo.repoId}").assertDoesNotExist()
    }
}
