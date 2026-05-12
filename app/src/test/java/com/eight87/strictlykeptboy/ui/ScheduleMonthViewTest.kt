package com.eight87.strictlykeptboy.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.schedule.ScheduleMonthView
import com.eight87.strictlykeptboy.ui.schedule.TestTagMonthCell
import com.eight87.strictlykeptboy.ui.schedule.TestTagMonthOverflow
import com.eight87.strictlykeptboy.ui.schedule.TestTagMonthTodayCell
import com.eight87.strictlykeptboy.ui.schedule.TestTagMonthView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleMonthViewTest {
    @get:Rule val composeRule = createComposeRule()

    private val anchor = LocalDate.of(2026, 5, 12)

    @Test fun renders_grid_with_today_highlight_and_overflow() {
        val busy = LocalDate.of(2026, 5, 15)
        val bands = mapOf(
            busy to (1..6).map { PhaseGTestFixtures.band("e$it", busy, 8 + it, 9 + it, title = "Event $it") },
        )
        val sched = PhaseGTestFixtures.schedule(bands)

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleMonthView(monthAnchor = anchor, schedule = sched, today = anchor)
            }
        }
        composeRule.onNodeWithTag(TestTagMonthView).assertExists()
        composeRule.onNodeWithTag(TestTagMonthTodayCell).assertExists()
        // Overflow chip appears for the busy day (6 events > 3 visible).
        composeRule.onNodeWithTag("$TestTagMonthOverflow-$busy").assertExists()
    }

    @Test fun tap_day_invokes_callback() {
        var tapped: LocalDate? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleMonthView(
                    monthAnchor = anchor,
                    schedule = PhaseGTestFixtures.schedule(mapOf(anchor to emptyList())),
                    today = anchor,
                    onDayTap = { tapped = it },
                )
            }
        }
        val target = LocalDate.of(2026, 5, 14)
        composeRule.onNodeWithTag("$TestTagMonthCell-$target").performClick()
        check(tapped == target) { "expected $target, got $tapped" }
    }
}
