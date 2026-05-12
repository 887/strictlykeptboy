package com.eight87.strictlykeptboy.ui.import_export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.port.ics.IcsParseReport
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.Event
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportWizardTest {

    @get:Rule val composeRule = createComposeRule()

    private val repo = RepoConfig(
        repoId = "test-repo-1",
        displayName = "My Calendar",
        rootDir = "/tmp/skb-test",
        remotes = emptyList(),
        primaryRemote = null,
        authorIdentity = AuthorIdentity("me", "me@example.com"),
    )

    @Test fun shows_repo_row_and_buttons() {
        val flow = MutableStateFlow(listOf(repo))
        val state = ImportExportViewState(repos = flow, onConfirmedImport = {})
        composeRule.setContent {
            ImportExportScreen(state, onPickImportFile = {}, onPickExportFile = {})
        }
        composeRule.onNodeWithTag("$TestTagImportButtonPrefix${repo.repoId}").assertIsDisplayed()
        composeRule.onNodeWithTag("$TestTagExportButtonPrefix${repo.repoId}").assertIsDisplayed()
    }

    @Test fun import_button_invokes_picker_callback() {
        val flow = MutableStateFlow(listOf(repo))
        val state = ImportExportViewState(repos = flow, onConfirmedImport = {})
        var picked: RepoConfig? = null
        composeRule.setContent {
            ImportExportScreen(state, onPickImportFile = { picked = it }, onPickExportFile = {})
        }
        composeRule.onNodeWithTag("$TestTagImportButtonPrefix${repo.repoId}").performClick()
        assertEquals(repo.repoId, picked?.repoId)
    }

    @Test fun confirm_preview_invokes_confirmed_import_callback() {
        val flow = MutableStateFlow(listOf(repo))
        var confirmed: IcsParseReport? = null
        val state = ImportExportViewState(
            repos = flow,
            onConfirmedImport = { confirmed = it },
        )
        val report = IcsParseReport(
            events = listOf(
                Event(
                    header = EntityHeader(
                        id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001",
                        createdAt = "2026-05-12T12:00:00Z",
                        updatedAt = "2026-05-12T12:00:00Z",
                        author = "me",
                    ),
                    title = "T",
                    start = "2026-05-12T14:00:00Z",
                    end = "2026-05-12T15:00:00Z",
                    calendarId = "c",
                )
            ),
            rules = emptyList(),
            exceptions = emptyList(),
        )
        composeRule.setContent {
            ImportExportScreen(state, onPickImportFile = {}, onPickExportFile = {})
        }
        state.showPreview(report)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTagImportPreviewSheet).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagImportPreviewConfirm).performClick()
        composeRule.waitForIdle()
        assertNotNull(confirmed)
        assertEquals(1, confirmed!!.events.size)
    }
}
