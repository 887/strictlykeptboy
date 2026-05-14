package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.settings.categories.ModeCategory
import com.eight87.strictlykeptboy.ui.settings.categories.TestTagCatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.1.K.4 — D.86 24h cooling-off gate. First tap of "switch to
 * free" only arms the timer; the typed-confirmation dialog is only
 * reachable once 24h have elapsed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeCoolingOffTest {
    @get:Rule val composeRule = createComposeRule()

    private fun freshModePrefs(): ModePrefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("mode_cool_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        return ModePrefs.openForTest(prefs).also { it.setMode(AppMode.StrictlyKept) }
    }

    @Test fun first_tap_arms_timer_does_not_flip_mode() {
        val mode = freshModePrefs()
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(width = 600.dp, height = 1200.dp)) {
                    ModeCategory(prefs = mode)
                }
            }
        }
        // Initial: no transition request armed.
        assertNull(mode.state.value.transitionRequestAtMs)
        // Phase 2.2.B.4 — picking "Just a calendar app" while currently
        // strict arms the 24h cooling-off (the dedicated SwitchFree
        // button collapsed into the six-radio Lifestyle picker).
        composeRule.onNodeWithTag("$TestTagCatMode-Lifestyle-JustCalendar").performScrollTo()
        composeRule.onNodeWithTag("$TestTagCatMode-Lifestyle-JustCalendar").performClick()
        assertNotNull(mode.state.value.transitionRequestAtMs)
        // Still strictly-kept — flip did not happen.
        assertEquals(AppMode.StrictlyKept, mode.state.value.mode)
    }

    @Test fun cooling_off_after_24h_enables_confirm_and_typed_phrase_flips_to_free() {
        val mode = freshModePrefs()
        // Simulate "24h25min ago" arm timestamp so the gate is open by
        // the time the composable mounts.
        mode.update {
            it.copy(transitionRequestAtMs = System.currentTimeMillis() - (25L * 60L * 60_000L))
        }
        assertTrue(mode.canConfirmFreeTransition())

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(width = 600.dp, height = 1200.dp)) {
                    ModeCategory(prefs = mode)
                }
            }
        }
        // The cooling-off-ready button is the confirm. Click it.
        composeRule.onNodeWithTag("$TestTagCatMode-CoolingOffConfirm").performScrollTo()
        composeRule.onNodeWithTag("$TestTagCatMode-CoolingOffConfirm").performClick()
        // Typed-confirmation dialog appears.
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmDialog").assertIsDisplayed()
        // Confirm is disabled until phrase matches.
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmInput")
            .performTextInput(ModePrefs.FREE_CONFIRMATION_PHRASE)
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmButton").performClick()
        assertEquals(AppMode.Free, mode.state.value.mode)
    }
}
