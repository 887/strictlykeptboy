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
 * W2-U-2 — picking the "Empty calendar" row in the intro picker
 * must emit [DemoPerspectiveChoice.Empty] from `onPerspectiveChosen`.
 * MainActivity's inline branch then fires
 * `setWizardEntryRequest(WizardScreen.Welcome)` so the wizard auto-
 * launches instead of leaving the user staring at an empty Schedule.
 *
 * The downstream routing is asserted in
 * `FirstLaunchRoutingTest.empty-pick fires wizard entry request at Welcome`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmptyChoiceAutoLaunchTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun empty_calendar_row_emits_empty_choice() {
        var picked: DemoPerspectiveChoice? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                IntroWizardHost(onPerspectiveChosen = { picked = it })
            }
        }
        composeRule.onNodeWithTag("IntroWizard-Continue").performClick()
        composeRule.onNodeWithTag(TestTagIntroEmptyRow).performClick()
        composeRule.onNodeWithTag(TestTagIntroPickerConfirm).performClick()

        assertNotNull("Confirm should emit a choice", picked)
        assertEquals(DemoPerspectiveChoice.Empty, picked)
    }
}
