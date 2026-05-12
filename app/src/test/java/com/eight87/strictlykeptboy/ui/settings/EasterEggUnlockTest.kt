package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.settings.categories.ABOUT_EASTER_EGG_RESET_MS
import com.eight87.strictlykeptboy.ui.settings.categories.ABOUT_EASTER_EGG_TAP_COUNT
import com.eight87.strictlykeptboy.ui.settings.categories.AboutCategory
import com.eight87.strictlykeptboy.ui.settings.categories.TestTagCatAbout
import com.eight87.strictlykeptboy.ui.settings.categories.TestTagCatAboutEasterEgg
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EasterEggUnlockTest {
    @get:Rule val composeRule = createComposeRule()

    private val versionTag = "$TestTagCatAbout-Version"

    @Test fun `seven rapid taps unlock easter egg`() {
        var fakeNow = 1_000L
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(width = 600.dp, height = 1200.dp)) {
                    AboutCategory(nowMs = { fakeNow })
                }
            }
        }
        repeat(ABOUT_EASTER_EGG_TAP_COUNT) {
            composeRule.onNodeWithTag(versionTag).performClick()
            fakeNow += 100L
        }
        composeRule.onNodeWithTag(TestTagCatAboutEasterEgg).assertIsDisplayed()
    }

    @Test fun `six taps do not unlock`() {
        var fakeNow = 1_000L
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(width = 600.dp, height = 1200.dp)) {
                    AboutCategory(nowMs = { fakeNow })
                }
            }
        }
        repeat(ABOUT_EASTER_EGG_TAP_COUNT - 1) {
            composeRule.onNodeWithTag(versionTag).performClick()
            fakeNow += 100L
        }
        composeRule.onNodeWithTag(TestTagCatAboutEasterEgg).assertIsNotDisplayed()
    }

    @Test fun `gap of more than 2 seconds resets counter`() {
        var fakeNow = 1_000L
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(width = 600.dp, height = 1200.dp)) {
                    AboutCategory(nowMs = { fakeNow })
                }
            }
        }
        // 4 taps, then a long gap, then 4 more — should NOT trigger (would
        // only trigger if counter is preserved across the gap).
        repeat(4) {
            composeRule.onNodeWithTag(versionTag).performClick()
            fakeNow += 100L
        }
        fakeNow += ABOUT_EASTER_EGG_RESET_MS + 1L
        repeat(4) {
            composeRule.onNodeWithTag(versionTag).performClick()
            fakeNow += 100L
        }
        composeRule.onNodeWithTag(TestTagCatAboutEasterEgg).assertIsNotDisplayed()
    }
}
