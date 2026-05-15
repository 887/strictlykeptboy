package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.system.SystemCalendar
import com.eight87.strictlykeptboy.system.SystemCalendarAppDetector
import com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.F.7 — given a stubbed detector that reports Google +
 * Outlook installed, the "Suppress system notifications" section lists
 * one row per detected candidate. Tap targets are present and
 * resolvable via the test-tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuppressSystemNotifSettingsTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var ctx: Context
    private lateinit var prefs: SystemCalendarPrefsStore
    private val calendarsFlow = MutableStateFlow<List<SystemCalendar>>(emptyList())

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val sp = ctx.getSharedPreferences("suppress_notif_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        prefs = SystemCalendarPrefsStore.openForTest(sp)
    }

    @Test fun listsOneRowPerInstalledCandidate() {
        val stub = listOf(
            SystemCalendarAppDetector.InstalledCandidate(
                packageName = "com.google.android.calendar",
                displayName = "Google Calendar",
                fallbackLabel = "Google Calendar",
            ),
            SystemCalendarAppDetector.InstalledCandidate(
                packageName = "com.microsoft.office.outlook",
                displayName = "Outlook",
                fallbackLabel = "Outlook",
            ),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(
                    prefs = prefs,
                    systemCalendarsFlow = calendarsFlow,
                    detectInstalledCalendarApps = { _ -> stub },
                )
            }
        }
        // Section exists; tag-based existence covers row count for the
        // scroll case (the screen is in a verticalScroll container so the
        // second row may sit below the fold under Robolectric).
        composeRule.onNodeWithTag(TestTagExternalCalendarsSuppressSection).assertExists()
        composeRule.onNodeWithTag(
            TestTagExternalCalendarsSuppressRow + "com.google.android.calendar",
        ).assertExists()
        composeRule.onNodeWithTag(
            TestTagExternalCalendarsSuppressRow + "com.microsoft.office.outlook",
        ).assertExists()
    }

    @Test fun detectorBuildsAppNotificationSettingsIntent() {
        val intent = SystemCalendarAppDetector.appNotificationSettingsIntent(
            "com.google.android.calendar",
        )
        org.junit.Assert.assertEquals(
            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            intent.action,
        )
        org.junit.Assert.assertEquals(
            "com.google.android.calendar",
            intent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE),
        )
    }

    @Test fun rendersEmptyStateWhenNoCandidatesInstalled() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(
                    prefs = prefs,
                    systemCalendarsFlow = calendarsFlow,
                    detectInstalledCalendarApps = { _ -> emptyList() },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsSuppressSection).assertExists()
    }
}
