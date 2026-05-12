package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTaskListTest {
    private val today = TaskFixtures.today
    private val a = TaskFixtures.listA
    private val b = TaskFixtures.listB

    @Test fun today_overdue_fromEvents_pinned_all_appear() {
        val tasks = listOf(
            TaskItem(id = "today", title = "today", todolist = a, due = today),
            TaskItem(id = "overdue", title = "od", todolist = a, due = today.minusDays(2)),
            TaskItem(id = "evt", title = "evt", todolist = b, source = TaskSource.FromEvents, due = today),
            TaskItem(id = "pin", title = "pin", todolist = b, standing = true, pinnedForToday = true),
            TaskItem(id = "future", title = "f", todolist = b, due = today.plusDays(2)),
            TaskItem(id = "standing-unpinned", title = "su", todolist = b, standing = true),
        )

        val out = tasks.forToday(today).map { it.id }.toSet()
        assertTrue(out.containsAll(setOf("today", "overdue", "evt", "pin")))
        assertFalse(out.contains("future"))
        assertFalse(out.contains("standing-unpinned"))
    }

    @Test fun completed_overdue_excluded() {
        val tasks = listOf(
            TaskItem(id = "done", title = "done", todolist = a, due = today.minusDays(1), done = true, doneAt = today.minusDays(1)),
        )
        // done tasks aren't overdue per isOverdue and aren't today-dated
        assertEquals(0, tasks.forToday(today).size)
    }
}
