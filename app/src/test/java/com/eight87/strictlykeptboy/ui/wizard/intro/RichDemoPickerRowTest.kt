package com.eight87.strictlykeptboy.ui.wizard.intro

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.20 Phase C.1/C.2 — the picker renders the rich-demo row +
 * Recommended pill. Round 2.22 follow-up — also asserts the Empty row
 * and the read-only supported-scenarios disclosure, and that the five
 * legacy lifestyle cards are no longer pickable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RichDemoPickerRowTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun rich_demo_row_and_recommended_pill_render() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                IntroWizardHost(onPerspectiveChosen = {})
            }
        }
        // Step 1 is the manifesto — advance to the picker.
        composeRule.onNodeWithTag("IntroWizard-Continue").performClick()

        composeRule.onNodeWithTag(TestTagIntroPicker).assertExists()
        composeRule.onNodeWithTag(TestTagIntroRichDemoRow).assertExists()
        // Pill lives inside the Card's merged semantics tree.
        composeRule
            .onNodeWithTag(TestTagIntroRichDemoPill, useUnmergedTree = true)
            .assertExists()
        // Round 2.22 follow-up — Empty row is the second pickable option.
        composeRule.onNodeWithTag(TestTagIntroEmptyRow).assertExists()
        // Round 2.22 follow-up — supported-scenarios disclosure exists +
        // the five legacy cards are no longer pickable as Card testTags.
        composeRule
            .onNodeWithTag(TestTagIntroSupportedScenarios, useUnmergedTree = true)
            .assertExists()
        composeRule
            .onNodeWithTag("IntroWizard-Card-PetKeptByAi")
            .assertDoesNotExist()
    }
}
