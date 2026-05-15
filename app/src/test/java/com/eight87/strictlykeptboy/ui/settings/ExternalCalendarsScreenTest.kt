package com.eight87.strictlykeptboy.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.system.SystemCalendar
import com.eight87.strictlykeptboy.system.SystemCalendarOverride
import com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Round 2.18.B.2/B.4/B.5 — verifies the ExternalCalendarsScreen
 * renders the top-level toggle, gates the secondary toggle on the
 * top-level state, and writes per-calendar visibility into the
 * SystemCalendarPrefsStore.
 *
 * Direct permission-launcher assertions are out of scope (Robolectric
 * cannot drive `ActivityResultContracts.RequestPermission` end-to-end
 * here); we instead exercise the deny path by pre-granting READ_CALENDAR
 * and verifying the toggle flips state via the store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalCalendarsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var prefs: SystemCalendarPrefsStore
    private lateinit var ctx: Context
    private val calendarsFlow = MutableStateFlow<List<SystemCalendar>>(emptyList())

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val sp = ctx.getSharedPreferences("ext_cal_screen_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        prefs = SystemCalendarPrefsStore.openForTest(sp)
    }

    @Test fun rendersTopLevelToggleOffByDefault() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(prefs = prefs, systemCalendarsFlow = calendarsFlow)
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsScreen).assertExists()
        composeRule.onNodeWithTag(TestTagExternalCalendarsShowSwitch)
            .assertIsDisplayed()
            .assertIsOff()
        // Secondary toggle is disabled while top-level is off.
        composeRule.onNodeWithTag(TestTagExternalCalendarsAllowEditSwitch)
            .assertIsNotEnabled()
    }

    @Test fun togglingShowOnWithPermissionAlreadyGrantedFlipsStore() {
        // Pre-grant READ_CALENDAR so the switch flips synchronously
        // without going through the activity-result launcher.
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(prefs = prefs, systemCalendarsFlow = calendarsFlow)
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsShowSwitch).performClick()
        composeRule.waitForIdle()
        assertTrue(prefs.globalState.value.showSystemCalendars)
        composeRule.onNodeWithTag(TestTagExternalCalendarsShowSwitch).assertIsOn()
    }

    @Test fun togglingShowOffDisablesAllowEditingToo() {
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        prefs.setShowSystemCalendars(true)
        prefs.setAllowEditing(true)
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(prefs = prefs, systemCalendarsFlow = calendarsFlow)
            }
        }
        composeRule.onNodeWithTag(TestTagExternalCalendarsShowSwitch).performClick()
        composeRule.waitForIdle()
        assertFalse(prefs.globalState.value.showSystemCalendars)
        // Secondary auto-disables to keep state coherent.
        assertFalse(prefs.globalState.value.allowEditing)
    }

    @Test fun perCalendarSwitchTogglesVisibilityInStore() {
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        prefs.setShowSystemCalendars(true)
        calendarsFlow.value = listOf(
            SystemCalendar(
                id = 42L,
                accountName = "alice@gmail.com",
                accountType = "com.google",
                displayName = "Work",
                color = 0xFFCAFE99.toInt(),
                accessLevel = 700,
                ownerAccount = "alice@gmail.com",
                isPrimary = true,
            ),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalCalendarsScreen(prefs = prefs, systemCalendarsFlow = calendarsFlow)
            }
        }
        val rowTag = TestTagExternalCalendarsCalendarRow + 42L
        composeRule.onNodeWithTag(rowTag).assertIsOn()
        composeRule.onNodeWithTag(rowTag).performClick()
        composeRule.waitForIdle()
        val key = SystemCalendarPrefsStore.keyFor("com.google", "alice@gmail.com", 42L)
        assertEquals(false, prefs.state.value[key]?.visible)
    }
}
