package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedTaskListTest {
    private val today = TaskFixtures.today
    private val a = TaskFixtures.listA
    private val b = TaskFixtures.listB
    private val c = TaskFixtures.listC

    @Test fun overdue_first_then_due_then_priority() {
        val tasks = listOf(
            TaskItem(id = "1", title = "future-low", todolist = a, due = today.plusDays(3), priority = 1),
            TaskItem(id = "2", title = "today", todolist = b, due = today, priority = 1),
            TaskItem(id = "3", title = "overdue-low", todolist = c, due = today.minusDays(2), priority = 1),
            TaskItem(id = "4", title = "overdue-high", todolist = a, due = today.minusDays(1), priority = 9),
            TaskItem(id = "5", title = "done", todolist = a, done = true, doneAt = today.minusDays(1)),
            TaskItem(id = "6", title = "today-high", todolist = b, due = today, priority = 8),
            TaskItem(id = "7", title = "no-due", todolist = c, priority = 5),
        )

        val sorted = tasks.sortedForCombined(today)

        // expected order: overdue-low (older date wins), overdue-high, today-high, today,
        // future-low, no-due, then done at the end.
        val ids = sorted.map { it.id }
        assertEquals(listOf("3", "4", "6", "2", "1", "7", "5"), ids)
        assertTrue("done is last", ids.last() == "5")
    }

    @Test fun all_three_lists_merged() {
        val tasks = listOf(
            TaskItem(id = "a1", title = "a", todolist = a, due = today),
            TaskItem(id = "b1", title = "b", todolist = b, due = today),
            TaskItem(id = "c1", title = "c", todolist = c, due = today),
        )
        val sorted = tasks.sortedForCombined(today)
        assertEquals(setOf("a1", "b1", "c1"), sorted.map { it.id }.toSet())
    }
}
