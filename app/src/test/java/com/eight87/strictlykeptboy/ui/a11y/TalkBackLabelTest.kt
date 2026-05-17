package com.eight87.strictlykeptboy.ui.a11y

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.components.RepoSwitcherChip
import com.eight87.strictlykeptboy.ui.components.SyncButton
import com.eight87.strictlykeptboy.ui.components.SyncButtonState
import com.eight87.strictlykeptboy.ui.components.TestTagRepoSwitcher
import com.eight87.strictlykeptboy.ui.components.TestTagSyncButton
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbAppShell
import com.eight87.strictlykeptboy.ui.scaffold.TestTagShellRailItemPrefix
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase U.1 — verifies TalkBack content-descriptions are set on the
 * core interactive surfaces. Smoke test against a representative
 * sample, not exhaustive coverage; full real-device TalkBack verification
 * is covered by the manual AVD smoke at end-of-phase.
 *
 * Nav-swap polish rewrite: the SkbTopBar component has been folded into
 * [SkbAppShell]; the per-pane view-mode tabs now live in the left
 * vertical rail under the [TestTagShellRailItemPrefix] testTag namespace
 * (one tag per rail entry).
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
                SyncButton(onClick = {}, state = SyncButtonState.Error(reason = "test"))
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

    @Test fun shell_left_rail_view_tabs_render_with_labels() {
        val repoName = MutableStateFlow("r")
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList()),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                val state = ScheduleViewState(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    snapshotFlow = snapshot,
                    sourcesFlow = sources,
                )
                SkbAppShell(activeRepoNameFlow = repoName, scheduleState = state)
            }
        }
        ScheduleViewTab.entries.forEach {
            composeRule.onNodeWithTag("$TestTagShellRailItemPrefix${it.name}").assertExists()
        }
    }
}
