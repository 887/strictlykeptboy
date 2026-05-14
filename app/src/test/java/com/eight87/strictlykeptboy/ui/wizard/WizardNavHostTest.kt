package com.eight87.strictlykeptboy.ui.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WizardNavHostTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun welcome_is_first_screen() {
        // Round 2.14 — Welcome reinstated as the wizard intro. Long
        // wizard's first screen is now Welcome again.
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                )
            }
        }
        composeRule.onNodeWithTag(com.eight87.strictlykeptboy.ui.wizard.TestTagWizardWelcome).assertIsDisplayed()
    }

    @Test fun neutral_mode_hides_kink_role_on_roles_screen() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                    initialScreen = WizardScreen.Roles,
                    initialDraft = WizardDraft(alignment = Alignment.Submissive).normalize(),
                    neutralMode = true,
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardRoles).assertIsDisplayed()
        composeRule.onNodeWithTag("Wizard-Role-self-care", useUnmergedTree = true).assertExists()
        val kinkNodes = composeRule.onAllNodesWithTag("Wizard-Role-kink", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertEquals("kink chip hidden under neutral mode", 0, kinkNodes.size)
    }

    @Test fun unaligned_private_alignment_hides_kink_on_roles_screen() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                    initialScreen = WizardScreen.Roles,
                    initialDraft = WizardDraft(alignment = Alignment.UnalignedPrivate).normalize(),
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardRoles).assertIsDisplayed()
        val kinkNodes = composeRule.onAllNodesWithTag("Wizard-Role-kink", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertEquals("kink chip hidden under unaligned-private", 0, kinkNodes.size)
    }

    @Test fun rerun_from_settings_starts_at_roles_with_existing_selections() {
        val preselected = WizardDraft(
            alignment = Alignment.Submissive,
            roles = setOf(RoleId.SelfCare, RoleId.Workout),
        ).normalize()
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                    initialScreen = WizardScreen.Roles,
                    initialDraft = preselected,
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardRoles).assertIsDisplayed()
        composeRule.onNodeWithTag("Wizard-Role-workout", useUnmergedTree = true).assertExists()
    }
}
