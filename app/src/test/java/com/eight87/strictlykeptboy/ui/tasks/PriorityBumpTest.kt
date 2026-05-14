package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.HourRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.1.D.5 + D.6 — todolist.priority feeds into sortedForCombined,
 * and active-hours scope adds the [ACTIVE_HOURS_PRIORITY_BUMP] on top.
 */
class PriorityBumpTest {
    private val today = TaskFixtures.today

    @Test fun list_priority_bumps_same_day_items() {
        val highList = TodolistInfo(id = "h", repoId = "r", name = "High", priority = 100)
        val lowList = TodolistInfo(id = "l", repoId = "r", name = "Low", priority = 1)
        val tasks = listOf(
            TaskItem(id = "low", title = "low", todolist = lowList, due = today, priority = 1),
            TaskItem(id = "high", title = "high", todolist = highList, due = today, priority = 1),
        )
        val sorted = tasks.sortedForCombined(today)
        assertEquals(listOf("high", "low"), sorted.map { it.id })
    }

    @Test fun active_hours_bump_applied() {
        val work = TodolistInfo(
            id = "w", repoId = "r", name = "Work", priority = 1,
            activeHours = listOf(
                HourRange(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)),
            ),
            tzId = ZoneId.of("UTC"),
        )
        // 2026-05-13 is a Wednesday.
        val inside = ZonedDateTime.parse("2026-05-13T10:00:00Z")
        val outside = ZonedDateTime.parse("2026-05-13T22:00:00Z")
        val item = TaskItem(id = "x", title = "x", todolist = work, due = today, priority = 1)
        val bumped = item.effectivePriority(inside)
        val plain = item.effectivePriority(outside)
        assertEquals(plain + ACTIVE_HOURS_PRIORITY_BUMP, bumped)
        assertTrue("bump must equal 50", ACTIVE_HOURS_PRIORITY_BUMP == 50)
    }
}
