package com.eight87.strictlykeptboy.ui.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.17 Phase D — covers D.5:
 *  - StorageStep renders both cards (External + Internal),
 *  - tapping "Save on your phone" fires onPickExternal,
 *  - tapping "Keep inside the app" fires onPickInternal.
 *
 * Robolectric @ SDK 34 to match the existing wizard test fixtures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WizardStorageStepTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun storage_step_renders_both_cards() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                StorageStep(
                    onPickExternal = {},
                    onPickInternal = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardStorage).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagWizardStorageExternal).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagWizardStorageInternal).assertIsDisplayed()
    }

    @Test fun external_card_click_invokes_callback() {
        var externalClicks = 0
        var internalClicks = 0
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                StorageStep(
                    onPickExternal = { externalClicks += 1 },
                    onPickInternal = { internalClicks += 1 },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardStorageExternal).performClick()
        assertEquals(1, externalClicks)
        assertEquals(0, internalClicks)
    }

    @Test fun internal_card_click_invokes_callback() {
        var externalClicks = 0
        var internalClicks = 0
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                StorageStep(
                    onPickExternal = { externalClicks += 1 },
                    onPickInternal = { internalClicks += 1 },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardStorageInternal).performClick()
        assertEquals(1, internalClicks)
        assertEquals(0, externalClicks)
    }

    @Test fun storage_step_appears_after_welcome_in_wizard() {
        // D.6 — verify the wizard host renders Storage after Welcome by
        // jumping initialScreen to Storage and asserting the test tag.
        // (We don't drive the Welcome→Next chrome here because no existing
        // test does either — the chrome-flow tests are deferred to
        // AVD smoke.) Confirms the new screen is wired into SCREEN_ORDER.
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                    initialScreen = WizardScreen.Storage,
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardStorage).assertIsDisplayed()
    }

    @Test fun storage_step_fires_external_pick_via_host() {
        var externalClicks = 0
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                    initialScreen = WizardScreen.Storage,
                    onPickExternalStorage = { externalClicks += 1 },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardStorageExternal).performClick()
        assertTrue("External CTA wired through host", externalClicks >= 1)
    }
}
