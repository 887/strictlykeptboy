package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.23.2 follow-up — verify the zoom row renders descriptive
 * labels ("Auto" / "Compact" / "Normal" / "Detail" / "Spacious")
 * rather than raw dp/h numbers, and that taps still propagate the
 * underlying override level (0..4 / null).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoomLevelRowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun renders_five_descriptive_labels() {
        composeRule.setContent {
            ZoomLevelRow(selectedOverride = null, onSelect = {})
        }
        listOf("Auto", "Compact", "Normal", "Detail", "Spacious").forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }
    }

    @Test fun auto_is_selected_when_override_is_null() {
        composeRule.setContent {
            ZoomLevelRow(selectedOverride = null, onSelect = {})
        }
        composeRule.onNodeWithTag(TestTagZoomLevelAuto).assertIsSelected()
    }

    @Test fun tapping_compact_invokes_onSelect_with_level_1() {
        var captured: Int? = -1
        composeRule.setContent {
            ZoomLevelRow(selectedOverride = null, onSelect = { captured = it })
        }
        composeRule.onNodeWithText("Compact").performClick()
        assertEquals(1, captured)
    }

    @Test fun tapping_spacious_invokes_onSelect_with_level_4() {
        var captured: Int? = -1
        composeRule.setContent {
            ZoomLevelRow(selectedOverride = null, onSelect = { captured = it })
        }
        composeRule.onNodeWithText("Spacious").performClick()
        assertEquals(4, captured)
    }

    @Test fun tapping_auto_invokes_onSelect_with_null() {
        var captured: Int? = 2
        composeRule.setContent {
            ZoomLevelRow(selectedOverride = 2, onSelect = { captured = it })
        }
        composeRule.onNodeWithText("Auto").performClick()
        assertNull(captured)
    }
}
