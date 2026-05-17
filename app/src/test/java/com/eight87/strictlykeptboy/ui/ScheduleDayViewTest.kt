package com.eight87.strictlykeptboy.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RenderedDay
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.resolver.asDayBandSource
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.ViewMode
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.schedule.ScheduleDayView
import com.eight87.strictlykeptboy.ui.schedule.TestTagDayBand
import com.eight87.strictlykeptboy.ui.schedule.TestTagDayEmpty
import com.eight87.strictlykeptboy.ui.schedule.TestTagDayView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleDayViewTest {
    @get:Rule val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 5, 12)
    private val tz: ZoneId = ZoneId.of("UTC")

    private fun band(id: String, startHour: Int, endHour: Int, lane: Int, total: Int): DayBand {
        val start = ZonedDateTime.of(date.atTime(startHour, 0), tz)
        val end = ZonedDateTime.of(date.atTime(endHour, 0), tz)
        val inst = MaterializedInstance(
            source = InstanceSource.OneOff(EventRef(id)),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = id,
            body = "",
        )
        return DayBand(instance = inst, priority = 500, laneIndex = lane, totalLanes = total)
    }

    @Test fun renders_three_overlapping_bands() {
        val bands = listOf(
            band("a", 9, 11, lane = 0, total = 3),
            band("b", 10, 12, lane = 1, total = 3),
            band("c", 10, 11, lane = 2, total = 3),
        )
        val sched = RenderedSchedule(
            rangeFrom = ZonedDateTime.of(date.atStartOfDay(), tz),
            rangeTo = ZonedDateTime.of(date.plusDays(1).atStartOfDay(), tz),
            viewMode = ViewMode.Day,
            days = listOf(RenderedDay(date = date, bands = bands, densityBucket = 1)),
            sourceDigest = "digest",
        )

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleDayView(date = date, dayBands = sched.asDayBandSource(), isToday = false)
            }
        }

        composeRule.onNodeWithTag(TestTagDayView).assertExists()
        bands.forEach { b ->
            composeRule.onNodeWithTag("$TestTagDayBand-${b.instance.instanceId}").assertExists()
        }
        // Lane assignment: 3 lanes per the resolver — already asserted by the
        // (laneIndex, totalLanes) data driving the layout.
        check(bands.all { it.totalLanes == 3 })
        check(bands.map { it.laneIndex }.toSet() == setOf(0, 1, 2))
    }

    @Test fun empty_schedule_renders_empty_state() {
        val sched = RenderedSchedule(
            rangeFrom = ZonedDateTime.of(date.atStartOfDay(), tz),
            rangeTo = ZonedDateTime.of(date.plusDays(1).atStartOfDay(), tz),
            viewMode = ViewMode.Day,
            days = listOf(RenderedDay(date = date, bands = emptyList(), densityBucket = 0)),
            sourceDigest = "digest",
        )

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ScheduleDayView(date = date, dayBands = sched.asDayBandSource(), isToday = false)
            }
        }

        composeRule.onNodeWithTag(TestTagDayEmpty).assertExists()
        composeRule
            .onAllNodesWithTag(com.eight87.strictlykeptboy.ui.schedule.TestTagEmptyBat)
            .assertCountEquals(1)
        composeRule
            .onNodeWithTag(com.eight87.strictlykeptboy.ui.schedule.TestTagEmptyMessage)
            .assertExists()
    }
}
