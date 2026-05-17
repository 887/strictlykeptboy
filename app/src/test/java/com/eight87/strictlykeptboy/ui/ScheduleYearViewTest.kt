package com.eight87.strictlykeptboy.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.resolver.asDayBandSource
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.schedule.ScheduleYearView
import com.eight87.strictlykeptboy.ui.schedule.TestTagYearMonth
import com.eight87.strictlykeptboy.ui.schedule.TestTagYearView
import com.eight87.strictlykeptboy.ui.schedule.intensityForCount
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.Month

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleYearViewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun heatmap_intensity_matches_count_buckets() {
        check(intensityForCount(0) == 0f)
        check(intensityForCount(1) == 0.2f)
        check(intensityForCount(2) == 0.4f)
        check(intensityForCount(3) == 0.6f)
        check(intensityForCount(4) == 0.8f)
        check(intensityForCount(5) == 1.0f)
        check(intensityForCount(99) == 1.0f)
    }

    @Test fun renders_twelve_months() {
        val bands = mapOf(
            LocalDate.of(2026, 1, 15) to listOf(PhaseGTestFixtures.band("a", LocalDate.of(2026, 1, 15), 9, 10)),
            LocalDate.of(2026, 6, 20) to listOf(PhaseGTestFixtures.band("b", LocalDate.of(2026, 6, 20), 9, 10)),
        )
        val sched = PhaseGTestFixtures.schedule(bands)

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleYearView(year = 2026, dayBands = sched.asDayBandSource())
            }
        }
        composeRule.onNodeWithTag(TestTagYearView).assertExists()
        // All 12 months are rendered (LazyVerticalGrid materializes everything
        // visible; the test viewport is generous so we expect all 12).
        Month.entries.forEach { m ->
            composeRule.onNodeWithTag("$TestTagYearMonth-${m.name}", useUnmergedTree = true).assertExists()
        }
    }
}
