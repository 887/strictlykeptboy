package com.eight87.strictlykeptboy.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.system.SystemCalendar
import com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Round 2.18 Phase I — power-user Settings row "Calendar app defaults"
 * is always visible and fires Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS.
 *
 * Also asserts that when the detector returns `true`, the
 * "You're the default calendar app" badge is displayed near the top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarDefaultsRowTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var prefs: SystemCalendarPrefsStore
    private lateinit var ctx: Context
    private val calendarsFlow = MutableStateFlow<List<SystemCalendar>>(emptyList())

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val sp = ctx.getSharedPreferences("cal_defaults_row_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        prefs = SystemCalendarPrefsStore.openForTest(sp)
    }

    @Test fun row_fires_action_manage_default_apps_intent() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(
                    prefs = prefs,
                    systemCalendarsFlow = calendarsFlow,
                    detectInstalledCalendarApps = { emptyList() },
                    isDefaultCalendarApp = { false },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsCalendarDefaultsButton)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        val started = Shadows.shadowOf(ctx as Application).nextStartedActivity
        assertNotNull("Calendar-defaults row should start an activity", started)
        assertEquals(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            started!!.action,
        )
    }

    @Test fun row_is_visible_even_when_top_level_is_off() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(
                    prefs = prefs,
                    systemCalendarsFlow = calendarsFlow,
                    detectInstalledCalendarApps = { emptyList() },
                    isDefaultCalendarApp = { false },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsCalendarDefaultsRow)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun badge_displays_when_detector_returns_true() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(
                    prefs = prefs,
                    systemCalendarsFlow = calendarsFlow,
                    detectInstalledCalendarApps = { emptyList() },
                    isDefaultCalendarApp = { true },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsDefaultAppBadge)
            .assertIsDisplayed()
    }

    @Test fun badge_absent_when_detector_returns_false() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(
                    prefs = prefs,
                    systemCalendarsFlow = calendarsFlow,
                    detectInstalledCalendarApps = { emptyList() },
                    isDefaultCalendarApp = { false },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsDefaultAppBadge)
            .assertDoesNotExist()
    }
}
