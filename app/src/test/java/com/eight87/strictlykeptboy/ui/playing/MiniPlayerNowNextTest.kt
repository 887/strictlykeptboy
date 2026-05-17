package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.strictlykeptboy.task.TaskPlaybackState
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.25 Phase B.3 — when a NowNextSnapshot is wired into the
 * MiniPlayer, the empty-state row overrides "No active task" with
 * `Now: <title>` and the right column renders `Next: <title>` plus
 * the relative-time line. When the snapshot is empty, the legacy
 * "No active task / Tap to pick one" copy still renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerNowNextTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun snapshot_with_now_and_next_renders_both_lines() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                MiniPlayer(
                    state = TaskPlaybackState.Empty,
                    onTogglePlayPause = {},
                    onClose = {},
                    onExpand = {},
                    nowTitle = "deep focus",
                    nextTitle = "brush teeth",
                    nextRelative = "in 2h 15m",
                )
            }
        }
        composeRule.onNodeWithText("Now: deep focus").assertExists()
        composeRule.onNodeWithText("Next: brush teeth").assertExists()
        composeRule.onNodeWithText("in 2h 15m").assertExists()
        composeRule.onNodeWithTag("mini_player_next_column", useUnmergedTree = true).assertExists()
    }

    @Test fun empty_snapshot_falls_back_to_legacy_copy() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                MiniPlayer(
                    state = TaskPlaybackState.Empty,
                    onTogglePlayPause = {},
                    onClose = {},
                    onExpand = {},
                    // nowTitle / nextTitle null — same as snapshot Empty
                )
            }
        }
        composeRule.onNodeWithText("No active task").assertExists()
        composeRule.onNodeWithText("Tap to pick one").assertExists()
        composeRule
            .onNodeWithTag("mini_player_next_column", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
