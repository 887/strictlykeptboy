package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.ui.test.assertHasClickAction
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
 * Round 2.16 post-DONE — empty-state MiniPlayer.
 *
 * When hasMedia=false the MiniPlayer no longer returns early; it
 * renders a peek-sized row with the checklist cover, "No active task"
 * title, "Tap to pick one" hint, and a clickable outer row that
 * dispatches onExpand. No transport row / no progress bars / no
 * step-count pill render in this branch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerEmptyStateTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun empty_state_renders_title_hint_and_cover() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                MiniPlayer(
                    state = TaskPlaybackState.Empty,
                    onTogglePlayPause = {},
                    onClose = {},
                    onExpand = {},
                )
            }
        }
        composeRule.onNodeWithText("No active task").assertExists()
        composeRule.onNodeWithText("Tap to pick one").assertExists()
        composeRule
            .onNodeWithTag("mini_player_cover", useUnmergedTree = true)
            .assertExists()
        composeRule
            .onNodeWithTag("mini_player", useUnmergedTree = true)
            .assertExists()
            .assertHasClickAction()
    }
}
