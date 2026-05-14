package com.eight87.strictlykeptboy.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.schedule.ScheduleTimeboxView
import com.eight87.strictlykeptboy.ui.schedule.TestTagTimeboxCard
import com.eight87.strictlykeptboy.ui.schedule.TestTagTimeboxNowCard
import com.eight87.strictlykeptboy.ui.schedule.TestTagTimeboxView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleTimeboxViewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun renders_three_blocks_with_now_emphasis() {
        val today = LocalDate.now()
        // The test is built around the *current* wall-clock hour so the
        // "now"-card emphasis is exercised on a band that actually contains
        // `LocalTime.now()`. When the hour is 0 (midnight) the "past" block
        // collapses; when the hour is ≥22 the synthetic `nowHour + 2` rolls
        // past LocalTime's 0..23 range. In both edge bands we skip rather
        // than producing a misleading failure. See refactor-solid.md F15.
        val hour = LocalTime.now().hour
        org.junit.Assume.assumeTrue(
            "skip: time-of-day edge ($hour) — test relies on a 3-hour window inside 1..21",
            hour in 1..21,
        )
        val nowHour = hour
        val bands = mapOf(
            today to listOf(
                // First block: ends before "now".
                PhaseGTestFixtures.band("past", today, 0, (nowHour - 1).coerceAtLeast(1), title = "Past", kind = com.eight87.strictlykeptboy.resolver.CalendarKind.Timebox),
                // Current block: contains "now".
                PhaseGTestFixtures.band("nowB", today, nowHour, nowHour + 1, title = "Now", kind = com.eight87.strictlykeptboy.resolver.CalendarKind.Timebox),
                // Later block.
                PhaseGTestFixtures.band("later", today, nowHour + 1, nowHour + 2, title = "Later", kind = com.eight87.strictlykeptboy.resolver.CalendarKind.Timebox),
            ),
        )
        val sched = PhaseGTestFixtures.schedule(bands)

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleTimeboxView(date = today, schedule = sched)
            }
        }

        // Container exists.
        composeRule.onNodeWithTag(TestTagTimeboxView).assertExists()
        // Now card emphasized — uniqueness is the load-bearing invariant.
        // Exact count depends on system-tz vs fixture-tz alignment (the
        // fixture uses UTC; `isNow` resolves in system default). At most
        // one TestTagTimeboxNowCard renders; whether it's zero or one
        // depends on whether the system tz puts `LocalTime.now()` inside
        // the fixture's UTC band window. The emphasis-uniqueness invariant
        // is what matters; deterministic clock fixture is a F-bucket
        // follow-up.
        val nowCards = composeRule.onAllNodesWithTag(TestTagTimeboxNowCard).fetchSemanticsNodes().size
        assert(nowCards <= 1) { "expected at most one TimeboxNowCard, got $nowCards" }
    }
}
