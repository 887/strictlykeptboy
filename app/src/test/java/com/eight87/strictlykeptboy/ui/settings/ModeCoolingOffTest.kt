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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test fun switch_to_free_requires_typed_phrase() {
        val mode = freshModePrefs()
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(width = 600.dp, height = 1200.dp)) {
                    ModeCategory(prefs = mode)
                }
            }
        }
        // Trigger transition affordance.
        composeRule.onNodeWithTag("$TestTagCatMode-SwitchFree").performScrollTo()
        composeRule.onNodeWithTag("$TestTagCatMode-SwitchFree").performClick()
        // Confirmation dialog appears.
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmDialog").assertIsDisplayed()
        // Confirm is disabled until typed phrase matches.
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmInput")
            .performTextInput(ModePrefs.FREE_CONFIRMATION_PHRASE)
        composeRule.onNodeWithTag("$TestTagCatMode-ConfirmButton").performClick()
        assertEquals(AppMode.Free, mode.state.value.mode)
    }
}
