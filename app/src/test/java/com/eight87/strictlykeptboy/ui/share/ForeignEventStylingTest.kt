package com.eight87.strictlykeptboy.ui.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForeignEventStylingTest {
    @get:Rule val composeRule = createComposeRule()

    private fun foreignRepo() = RepoConfig(
        repoId = "shared-x",
        displayName = "Shared",
        rootDir = "/tmp/shared",
        remotes = emptyList(),
        primaryRemote = null,
        authorIdentity = AuthorIdentity("me", "me@x.com"),
        readOnlyViaShare = true,
        sourceRepoLabel = "alex's cal",
    )

    @Test fun foreign_chip_renders_with_source_label() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ForeignEventSourceChip(sourceLabel = "alex's cal")
            }
        }
        composeRule.onNodeWithTag(TestTagForeignEventChip, useUnmergedTree = true).assertExists()
    }

    @Test fun foreign_event_background_modifier_applies_when_read_only_via_share() {
        val repo = foreignRepo()
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                val mod = if (repo.readOnlyViaShare) Modifier.foreignEventBackground() else Modifier
                Box(mod.size(40.dp)) { Text("e") }
            }
        }
        composeRule.onNodeWithTag(TestTagForeignEventBg, useUnmergedTree = true).assertExists()
    }
}
