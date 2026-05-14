package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.strictlykeptboy.task.ConnectionPhase
import com.eight87.strictlykeptboy.task.TaskPlaybackState
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.16.G.1 — D-2.16.d gate.
 *
 * Asserts the MiniPlayer renders THREE distinct Text nodes per the
 * locked-decision (top line = task name, second line = sub-step
 * name, trailing = mono mm:ss countdown) plus the step-count pill
 * for a multi-step task — not a single interpolated string like
 * "Grooming (2/6) — brushing teeth 2:45".
 *
 * subStepDurationMs - subStepElapsedMs = 180_000 - 15_000 = 165_000 ms
 *   → 165 seconds → "2:45" (per [formatMmSs]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerTaskBindingTest {

    @get:Rule val composeRule = createComposeRule()

    private val grooming = TaskPlaybackState(
        hasMedia = true,
        taskName = "Grooming",
        subStepName = "brushing teeth",
        isPlaying = true,
        subStepElapsedMs = 15_000L,
        subStepDurationMs = 180_000L,
        subStepIndex = 2,
        subStepCount = 6,
        taskElapsedMs = 405_000L,
        taskDurationMs = 1_800_000L,
        hasNext = true,
        hasPrevious = true,
        connectionPhase = ConnectionPhase.Connected,
    )

    @Test fun three_distinct_text_nodes_plus_pill() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                MiniPlayer(
                    state = grooming,
                    onTogglePlayPause = {},
                    onClose = {},
                    onExpand = {},
                )
            }
        }
        // Top line — task name.
        composeRule.onNodeWithText("Grooming").assertExists()
        // Second line — sub-step name.
        composeRule.onNodeWithText("brushing teeth").assertExists()
        // Right-aligned mono countdown — 165s = 2:45.
        composeRule.onAllNodesWithText("2:45").assertCountEquals(1)
        // Step-count pill — D-2.16.d (rendered only when subStepCount > 1).
        composeRule.onNodeWithTag("step_count_pill", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2/6").assertExists()
    }
}
