package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RenderedDay
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.26.B.5 — invariants for the unified day-of feed builder.
 */
class UnifiedTodayTest {
    private val tz: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 5, 17)
    private val now: ZonedDateTime = ZonedDateTime.of(today.atTime(8, 0), tz)
    private val a = TaskFixtures.listA

    private fun timebox(
        id: String,
        startHour: Int,
        endHour: Int,
        title: String = id,
    ): DayBand {
        val start = ZonedDateTime.of(today.atTime(startHour, 0), tz)
        val end = ZonedDateTime.of(today.atTime(endHour, 0), tz)
        val inst = MaterializedInstance(
            source = InstanceSource.OneOff(EventRef(id)),
            calendar = CalendarRef("cal-timebox"),
            repo = RepoRef("r1"),
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = title,
            body = "",
        )
        return DayBand(
            instance = inst,
            priority = 500,
            laneIndex = 0,
            totalLanes = 1,
            kind = CalendarKind.Timebox,
        )
    }

    private fun schedule(bands: List<DayBand>): RenderedSchedule = RenderedSchedule(
        rangeFrom = ZonedDateTime.of(today.atStartOfDay(), tz),
        rangeTo = ZonedDateTime.of(today.plusDays(1).atStartOfDay(), tz),
        viewMode = ViewMode.Day,
        days = listOf(RenderedDay(date = today, bands = bands, densityBucket = 1)),
        sourceDigest = "test",
    )

    @Test
    fun empty_schedule_three_tasks_passthrough() {
        val tasks = listOf(
            TaskItem(id = "t1", title = "alpha", todolist = a, due = today),
            TaskItem(id = "t2", title = "bravo", todolist = a, due = today),
            TaskItem(id = "t3", title = "charlie", todolist = a, due = today),
        )
        val items = buildUnifiedToday(tasks, schedule = null, now = now)
        assertEquals(3, items.size)
        assertTrue(items.all { it is UnifiedTodayItem.TaskEntry })
        // sort by title.lowercase() since all share start-of-day sortKey
        assertEquals(
            listOf("t1", "t2", "t3"),
            items.map { (it as UnifiedTodayItem.TaskEntry).task.id },
        )
    }

    @Test
    fun overdue_first_then_interleaved_timeboxes_and_today_tasks() {
        val tasks = listOf(
            TaskItem(id = "ov", title = "overdue-thing", todolist = a, due = today.minusDays(2)),
            TaskItem(id = "td", title = "due-today", todolist = a, due = today),
        )
        val bands = listOf(
            timebox("tb-late", 14, 15, title = "afternoon-block"),
            timebox("tb-morning", 9, 11, title = "morning-block"),
        )
        val items = buildUnifiedToday(tasks, schedule(bands), now)
        // 1 overdue + 2 timeboxes + 1 today task = 4 entries
        assertEquals(4, items.size)
        // overdue first
        assertTrue(items[0] is UnifiedTodayItem.TaskEntry)
        assertEquals("ov", (items[0] as UnifiedTodayItem.TaskEntry).task.id)
        // remaining three: tasks at start-of-day (00:00) sort before timeboxes at 09:00/14:00
        val rest = items.drop(1)
        // the only today task has sortKey = today T00:00, timeboxes 09 + 14
        assertTrue(rest[0] is UnifiedTodayItem.TaskEntry)
        assertEquals("td", (rest[0] as UnifiedTodayItem.TaskEntry).task.id)
        assertTrue(rest[1] is UnifiedTodayItem.TimeboxEntry)
        assertEquals("tb-morning", (rest[1] as UnifiedTodayItem.TimeboxEntry).band.instance.source.let {
            (it as InstanceSource.OneOff).eventId.id
        })
        assertTrue(rest[2] is UnifiedTodayItem.TimeboxEntry)
        assertEquals("tb-late", (rest[2] as UnifiedTodayItem.TimeboxEntry).band.instance.source.let {
            (it as InstanceSource.OneOff).eventId.id
        })
    }

    @Test
    fun no_due_task_sinks_below_timebox_in_today_bucket() {
        // Edge case: a pinned standing task (no due, pinned-for-today) should
        // land in the PinnedStanding bucket — AFTER timeboxes.
        val tasks = listOf(
            TaskItem(id = "pin", title = "pinned-thing", todolist = a, pinnedForToday = true, standing = true),
        )
        val bands = listOf(timebox("tb1", 10, 11))
        val items = buildUnifiedToday(tasks, schedule(bands), now)
        assertEquals(2, items.size)
        // Timebox first (today bucket), pinned standing last
        assertTrue(items[0] is UnifiedTodayItem.TimeboxEntry)
        assertTrue(items[1] is UnifiedTodayItem.TaskEntry)
        assertEquals("pin", (items[1] as UnifiedTodayItem.TaskEntry).task.id)
        // section split mirrors the order
        val sections = items.bySection(today)
        assertEquals(setOf(UnifiedTodaySection.Today, UnifiedTodaySection.PinnedStanding), sections.keys)
    }
}
