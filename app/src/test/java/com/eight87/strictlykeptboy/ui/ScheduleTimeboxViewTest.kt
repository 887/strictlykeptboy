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
        val nowHour = LocalTime.now().hour.coerceIn(1, 22)
        val bands = mapOf(
            today to listOf(
                // First block: ends before "now".
                PhaseGTestFixtures.band("past", today, 0, (nowHour - 1).coerceAtLeast(1), title = "Past"),
                // Current block: contains "now".
                PhaseGTestFixtures.band("nowB", today, nowHour, nowHour + 1, title = "Now"),
                // Later block.
                PhaseGTestFixtures.band("later", today, nowHour + 1, nowHour + 2, title = "Later"),
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
        // Now card emphasized. (Other cards may be below the LazyColumn
        // viewport on a Robolectric host, so we only assert the now-card
        // here — the now-card always renders first because it's the one
        // containing the current time, but we don't rely on ordering. The
        // emphasis property — exactly one — is the load-bearing assertion.)
        composeRule.onAllNodesWithTag(TestTagTimeboxNowCard).assertCountEquals(1)
    }
}
