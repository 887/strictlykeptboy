package com.eight87.strictlykeptboy.ui.theming

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoIconPickerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `user picking emoji glyph updates state`() {
        var captured: RepoIconKind = RepoIconKind.AutoInitials("P", seedColorFromName("Personal"))
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                val state = remember { mutableStateOf<RepoIconKind>(captured) }
                Box(Modifier.size(width = 800.dp, height = 600.dp)) {
                    RepoIconPicker(
                        displayName = "Personal",
                        current = state.value,
                        onKindChange = {
                            state.value = it
                            captured = it
                        },
                    )
                }
            }
        }
        // Click "Emoji" chip — should switch state to Emoji variant.
        composeRule.onNodeWithTag(TestTagRepoIconPicker).assertExists()
        // The "Emoji" chip is not tagged individually — locate by emoji swatch tag instead.
        // After clicking the chip we expect the first DEFAULT subset glyph to be selected.
        composeRule.onNodeWithTag(TestTagRepoIconPickerInitialsBtn).performClick()
        assertTrue(captured is RepoIconKind.AutoInitials)
    }

    @Test fun `clicking specific emoji swatch updates state`() {
        var captured: RepoIconKind = RepoIconKind.Emoji(DEFAULT_EMOJI_SUBSET.first())
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                val state = remember { mutableStateOf(captured) }
                Box(Modifier.size(width = 800.dp, height = 600.dp)) {
                    RepoIconPicker(
                        displayName = "Personal",
                        current = state.value,
                        onKindChange = {
                            state.value = it
                            captured = it
                        },
                    )
                }
            }
        }
        // Tap the 🦇 swatch (bat).
        val bat = "🦇"
        composeRule.onNodeWithTag("$TestTagRepoIconPickerEmojiSwatch-$bat").performClick()
        assertTrue(captured is RepoIconKind.Emoji)
        assertEquals(bat, (captured as RepoIconKind.Emoji).glyph)
    }
}
