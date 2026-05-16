package com.eight87.strictlykeptboy.ui.wizard.intro

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.20 Phase C.3 — the picker pre-selects RichDemo on first
 * launch, so a user who taps "Confirm" without touching any row
 * lands on the rich demo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RichDemoDefaultSelectionTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun confirming_without_changing_selection_emits_rich_demo() {
        var picked: DemoPerspectiveChoice? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                IntroWizardHost(onPerspectiveChosen = { picked = it })
            }
        }
        composeRule.onNodeWithTag("IntroWizard-Continue").performClick()
        composeRule.onNodeWithTag(TestTagIntroPickerConfirm).performClick()

        assertNotNull("Confirm should emit a choice", picked)
        assertEquals(DemoPerspectiveChoice.RichDemo, picked)
    }
}
