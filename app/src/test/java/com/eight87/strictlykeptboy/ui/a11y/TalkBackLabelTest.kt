package com.eight87.strictlykeptboy.ui.a11y

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.components.RepoSwitcherChip
import com.eight87.strictlykeptboy.ui.components.SyncButton
import com.eight87.strictlykeptboy.ui.components.SyncButtonState
import com.eight87.strictlykeptboy.ui.components.TestTagRepoSwitcher
import com.eight87.strictlykeptboy.ui.components.TestTagSyncButton
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbTopBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase U.1 — verifies TalkBack content-descriptions are set on the
 * core interactive surfaces. This is a smoke test against a representative
 * sample (top-bar chrome), not exhaustive coverage; full real-device
 * TalkBack verification is covered by the manual AVD smoke at end-of-phase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalkBackLabelTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun sync_button_idle_has_content_description() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                SyncButton(onClick = {}, state = SyncButtonState.Idle)
            }
        }
        composeRule.onNodeWithTag(TestTagSyncButton).assertExists()
        // The cd_sync string ("Sync") is attached to the Icon child.
        composeRule.onNodeWithContentDescription("Sync").assertExists()
    }

    @Test fun sync_button_syncing_state_has_content_description() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                SyncButton(onClick = {}, state = SyncButtonState.Syncing)
            }
        }
        composeRule.onNodeWithContentDescription("Syncing").assertExists()
    }

    @Test fun sync_button_error_state_has_content_description() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                SyncButton(onClick = {}, state = SyncButtonState.Error)
            }
        }
        composeRule.onNodeWithContentDescription("Sync error").assertExists()
    }

    @Test fun repo_switcher_chip_has_content_description_with_repo_name() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoSwitcherChip(activeRepoName = "alpha-repo", onClick = {})
            }
        }
        val node = composeRule.onNodeWithTag(TestTagRepoSwitcher).fetchSemanticsNode()
        val cd = node.config[SemanticsProperties.ContentDescription].joinToString(" ")
        check("alpha-repo" in cd) { "expected 'alpha-repo' in cd, got '$cd'" }
        check("Switch repo" in cd || "active" in cd) {
            "expected switcher cd to describe action, got '$cd'"
        }
    }

    @Test fun top_bar_view_tabs_render_with_labels() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                SkbTopBar(
                    activeRepoName = "r",
                    selectedViewTab = ScheduleViewTab.Day,
                    onSelectViewTab = {},
                    onRepoSwitcherClick = {},
                    onSyncClick = {},
                    onIdentityClick = {},
                )
            }
        }
        // Every tab in the strip is present.
        ScheduleViewTab.entries.forEach {
            composeRule.onNodeWithTag("ViewTab-${it.name}").assertExists()
        }
    }

}
