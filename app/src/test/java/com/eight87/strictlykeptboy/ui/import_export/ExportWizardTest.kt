package com.eight87.strictlykeptboy.ui.import_export

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportWizardTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun export_button_invokes_picker_with_repo() {
        val repo = RepoConfig(
            repoId = "rep-export",
            displayName = "Export-Me",
            rootDir = "/tmp/skb-x",
            remotes = emptyList(),
            primaryRemote = null,
            authorIdentity = AuthorIdentity("me", "me@example.com"),
        )
        val flow = MutableStateFlow(listOf(repo))
        val state = ImportExportViewState(repos = flow, onConfirmedImport = {})
        var picked: RepoConfig? = null
        composeRule.setContent {
            ImportExportScreen(state, onPickImportFile = {}, onPickExportFile = { picked = it })
        }
        composeRule.onNodeWithTag("$TestTagExportButtonPrefix${repo.repoId}").performClick()
        assertEquals(repo.repoId, picked?.repoId)
    }
}
