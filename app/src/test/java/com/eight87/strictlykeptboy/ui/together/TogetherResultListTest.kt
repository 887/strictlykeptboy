package com.eight87.strictlykeptboy.ui.together

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.resolver.TimeSlot
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TogetherResultListTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun five_slots_render_and_tap_first_emits_callback() {
        val tz = ZoneId.of("UTC")
        val base = LocalDate.of(2026, 5, 12).atStartOfDay(tz)
        val slots = (0..4).map { i ->
            TimeSlot(base.plusDays(i.toLong()).plusHours(10), base.plusDays(i.toLong()).plusHours(11))
        }
        val tapped = mutableListOf<TimeSlot>()

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                TogetherResultList(slots = slots, onSlotTap = { tapped += it })
            }
        }

        composeRule.onNodeWithTag(TestTagTogetherResultList).assertIsDisplayed()
        // Header reflects all five slots passed in (proves list received them all).
        // TR-A.3 — `together_results_header` is now a `<plurals>`; with
        // count=5 we hit `quantity="other"`.
        composeRule.onNodeWithText("5 slots found").assertIsDisplayed()
        // First card is visible; the LazyColumn may not have composed off-screen
        // cards in the Robolectric viewport — tap the first to verify the
        // onSlotTap callback contract.
        composeRule.onNodeWithTag("$TestTagTogetherResultCardPrefix${slots[0].from}").assertIsDisplayed()
        composeRule.onNodeWithTag("$TestTagTogetherResultCardPrefix${slots[0].from}").performClick()
        assertEquals(listOf(slots[0]), tapped)
    }
}
