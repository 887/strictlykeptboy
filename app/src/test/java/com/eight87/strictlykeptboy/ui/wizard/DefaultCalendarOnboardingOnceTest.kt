package com.eight87.strictlykeptboy.ui.wizard

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18 Phase I — verifies the one-shot gating: when
 * `SystemCalendarGlobalPrefs.defaultCalendarOnboardingShown` is already
 * `true`, [WizardNavHost] auto-skips the [WizardScreen.DefaultCalendar]
 * step. We assert by jumping `initialScreen = WizardScreen.DefaultCalendar`
 * and confirming the DefaultCalendar test tag is NOT displayed (the
 * LaunchedEffect bounces the wizard past it).
 *
 * Sister-test verifies the first-arrival case: when the flag is `false`,
 * the wizard renders the card AND flips the flag (so subsequent runs
 * skip).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultCalendarOnboardingOnceTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var prefs: SystemCalendarPrefsStore
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val sp = ctx.getSharedPreferences("default_cal_once_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        prefs = SystemCalendarPrefsStore.openForTest(sp)
    }

    @Test fun step_is_skipped_when_already_shown() {
        prefs.markDefaultCalendarOnboardingShown()
        assertTrue(prefs.globalState.value.defaultCalendarOnboardingShown)

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                    initialScreen = WizardScreen.DefaultCalendar,
                    systemCalendarPrefs = prefs,
                )
            }
        }
        // The LaunchedEffect should immediately advance off the
        // DefaultCalendar step; its tag is not present.
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendar).assertDoesNotExist()
    }

    @Test fun step_renders_on_first_arrival_and_flag_flips_on_advance() {
        // Flag starts off — wizard should render the step. The flag
        // flips when the user advances (Got it / Open default apps),
        // not just on arrival, so the card actually stays visible long
        // enough to read.
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                WizardNavHost(
                    onFinish = {},
                    onCancel = {},
                    onScaffold = { Result.success(Unit) },
                    initialScreen = WizardScreen.DefaultCalendar,
                    systemCalendarPrefs = prefs,
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendar).assertExists()
        // Flag is still false — we haven't advanced yet.
        assertTrue(
            "flag remains false until the user advances off the step",
            !prefs.globalState.value.defaultCalendarOnboardingShown,
        )
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendarGotIt).performClick()
        composeRule.waitForIdle()
        assertTrue(
            "advancing should flip defaultCalendarOnboardingShown",
            prefs.globalState.value.defaultCalendarOnboardingShown,
        )
    }
}
