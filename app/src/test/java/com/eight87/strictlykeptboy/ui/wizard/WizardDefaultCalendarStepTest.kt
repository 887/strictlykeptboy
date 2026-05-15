package com.eight87.strictlykeptboy.ui.wizard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Round 2.18 Phase I — covers the DefaultCalendarStep composable in
 * isolation:
 *  - both buttons render,
 *  - "Got it" advances,
 *  - "Open default apps now" fires Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
 *    and then advances.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WizardDefaultCalendarStepTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun step_renders_both_buttons() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                DefaultCalendarStep(onAdvance = {})
            }
        }
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendar).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendarGotIt).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendarOpenSettings).assertIsDisplayed()
    }

    @Test fun got_it_button_advances() {
        var advances = 0
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                DefaultCalendarStep(onAdvance = { advances += 1 })
            }
        }
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendarGotIt).performClick()
        assertEquals(1, advances)
    }

    @Test fun open_default_apps_fires_intent_and_advances() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        var advances = 0
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                DefaultCalendarStep(onAdvance = { advances += 1 })
            }
        }
        composeRule.onNodeWithTag(TestTagWizardDefaultCalendarOpenSettings).performClick()
        val started = Shadows.shadowOf(ctx as Application).nextStartedActivity
        assertNotNull("Open-default-apps should start an activity", started)
        assertEquals(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            started!!.action,
        )
        assertTrue(
            "intent should request a new task",
            (started.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
        )
        assertEquals(1, advances)
    }
}
