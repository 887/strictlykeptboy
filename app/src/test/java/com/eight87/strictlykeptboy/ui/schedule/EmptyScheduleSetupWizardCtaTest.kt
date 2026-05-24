package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W2-U-2 — the prominent "Set up your calendar" CTA on the empty
 * Schedule must (a) render whenever the host wires `onSetupWizard`,
 * (b) fire the callback exactly once on tap, and (c) stay hidden
 * when the host does NOT wire the callback (preview / test entry
 * points that don't carry the wizard surface).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmptyScheduleSetupWizardCtaTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun setup_wizard_cta_renders_and_fires_when_wired() {
        var taps = 0
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                EmptyScheduleState(
                    kind = EmptyScheduleKind.NoRepos,
                    onSetupWizard = { taps += 1 },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagEmptySetupWizard).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagEmptySetupWizard).performClick()
        assertEquals(1, taps)
    }

    @Test fun setup_wizard_cta_hidden_when_callback_null() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                EmptyScheduleState(
                    kind = EmptyScheduleKind.NoRepos,
                    onSetupWizard = null,
                )
            }
        }
        composeRule.onNodeWithTag(TestTagEmptySetupWizard).assertDoesNotExist()
    }

    @Test fun setup_wizard_cta_renders_on_no_events_too() {
        // The CTA is intentionally NOT gated on `kind` — even after a
        // user backs out of the wizard onto an empty schedule, the
        // re-entry CTA must be discoverable.
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                EmptyScheduleState(
                    kind = EmptyScheduleKind.NoEvents,
                    onSetupWizard = { },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagEmptySetupWizard).assertIsDisplayed()
    }
}
