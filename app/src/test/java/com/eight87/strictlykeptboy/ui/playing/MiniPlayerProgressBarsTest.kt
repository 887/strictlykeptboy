package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.task.ConnectionPhase
import com.eight87.strictlykeptboy.task.TaskPlaybackState
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.16.G.1 — D-2.16.c gate. Asserts both progress bars render:
 *
 *  - the wide 4-dp sub-step bar (`mini_player_substep_progress`)
 *  - the thin 2-dp whole-task bar (`mini_player_task_progress`)
 *
 * Robolectric does not produce a reliable measured-width readout
 * without Paparazzi, so this test asserts node existence (the layout
 * is exercised by the AVD smoke gate G.2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerProgressBarsTest {

    @get:Rule val composeRule = createComposeRule()

    private val midway = TaskPlaybackState(
        hasMedia = true,
        taskName = "Grooming",
        subStepName = "brushing teeth",
        isPlaying = true,
        subStepElapsedMs = 90_000L,
        subStepDurationMs = 180_000L,
        subStepIndex = 2,
        subStepCount = 6,
        taskElapsedMs = 900_000L,
        taskDurationMs = 1_800_000L,
        hasNext = true,
        hasPrevious = true,
        connectionPhase = ConnectionPhase.Connected,
    )

    @Test fun both_bars_present() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                MiniPlayer(
                    state = midway,
                    onTogglePlayPause = {},
                    onClose = {},
                    onExpand = {},
                )
            }
        }
        composeRule.onNodeWithTag("mini_player_substep_progress", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag("mini_player_task_progress", useUnmergedTree = true)
            .assertExists()
    }
}
