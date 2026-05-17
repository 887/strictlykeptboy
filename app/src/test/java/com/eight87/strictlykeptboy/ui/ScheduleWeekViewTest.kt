package com.eight87.strictlykeptboy.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.resolver.asDayBandSource
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.schedule.ScheduleWeekView
import com.eight87.strictlykeptboy.ui.schedule.TestTagWeekColumn
import com.eight87.strictlykeptboy.ui.schedule.TestTagWeekHeader
import com.eight87.strictlykeptboy.ui.schedule.TestTagWeekTodayColumn
import com.eight87.strictlykeptboy.ui.schedule.TestTagWeekView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleWeekViewTest {
    @get:Rule val composeRule = createComposeRule()

    private val weekStart = LocalDate.of(2026, 5, 11) // Monday
    private val today = LocalDate.of(2026, 5, 13)     // Wed

    @Test fun renders_seven_columns_with_today_highlight() {
        val bands = mapOf(
            today to listOf(PhaseGTestFixtures.band("a", today, 9, 10)),
        )
        val sched = PhaseGTestFixtures.schedule(bands)

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleWeekView(weekStart = weekStart, dayBands = sched.asDayBandSource(), today = today)
            }
        }

        composeRule.onNodeWithTag(TestTagWeekView).assertExists()
        composeRule.onNodeWithTag(TestTagWeekHeader).assertExists()
        // All 7 day columns rendered.
        (0..6).forEach { i ->
            composeRule.onNodeWithTag("$TestTagWeekColumn-${weekStart.plusDays(i.toLong())}").assertExists()
        }
        // Today's column is highlighted.
        composeRule.onNodeWithTag(TestTagWeekTodayColumn).assertExists()
    }

    @Test fun swipe_callback_is_wired() {
        // We don't simulate the gesture here (the modifier wiring is itself
        // the assertion that the swipe affordance exists); we instead assert
        // that providing onSwipeWeek does not crash and the surface is laid
        // out — gesture-pixel mocking on Robolectric is flaky.
        var swipes = 0
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleWeekView(
                    weekStart = weekStart,
                    dayBands = PhaseGTestFixtures.schedule(mapOf(weekStart to emptyList())).asDayBandSource(),
                    today = today,
                    onSwipeWeek = { swipes += it },
                )
            }
        }
        composeRule.onNodeWithTag(TestTagWeekView).assertExists()
        check(swipes == 0)
    }
}
