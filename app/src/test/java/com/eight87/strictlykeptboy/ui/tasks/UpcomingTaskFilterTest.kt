package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-11 fix — unit tests for [forUpcoming].
 *
 * Upcoming must include ONLY non-done tasks with a due date strictly after
 * today. Today-due, overdue, no-due, and done tasks all stay out.
 */
class UpcomingTaskFilterTest {

    private val today = TaskFixtures.today
    private val a = TaskFixtures.listA

    @Test fun future_tasks_are_included() {
        val tasks = listOf(
            TaskItem(id = "future1", title = "tomorrow", todolist = a, due = today.plusDays(1)),
            TaskItem(id = "future2", title = "next week", todolist = a, due = today.plusDays(7)),
        )
        val ids = tasks.forUpcoming(today).map { it.id }
        assertEquals(listOf("future1", "future2"), ids)
    }

    @Test fun today_due_is_excluded() {
        val tasks = listOf(
            TaskItem(id = "t", title = "today", todolist = a, due = today),
        )
        assertTrue(tasks.forUpcoming(today).isEmpty())
    }

    @Test fun overdue_is_excluded() {
        val tasks = listOf(
            TaskItem(id = "od", title = "overdue", todolist = a, due = today.minusDays(1)),
        )
        assertTrue(tasks.forUpcoming(today).isEmpty())
    }

    @Test fun no_due_is_excluded() {
        val tasks = listOf(
            TaskItem(id = "nd", title = "no due", todolist = a),
        )
        assertTrue(tasks.forUpcoming(today).isEmpty())
    }

    @Test fun done_future_task_is_excluded() {
        val tasks = listOf(
            TaskItem(id = "df", title = "done future", todolist = a, due = today.plusDays(3), done = true, doneAt = today),
        )
        assertTrue(tasks.forUpcoming(today).isEmpty())
    }

    @Test fun results_sorted_chronologically_by_due_date() {
        val tasks = listOf(
            TaskItem(id = "far", title = "far", todolist = a, due = today.plusDays(10)),
            TaskItem(id = "near", title = "near", todolist = a, due = today.plusDays(2)),
            TaskItem(id = "mid", title = "mid", todolist = a, due = today.plusDays(5)),
        )
        val ids = tasks.forUpcoming(today).map { it.id }
        assertEquals(listOf("near", "mid", "far"), ids)
    }

    @Test fun mixed_bag_only_future_non_done_appear() {
        val tasks = listOf(
            TaskItem(id = "future", title = "future", todolist = a, due = today.plusDays(1)),
            TaskItem(id = "todaydue", title = "today", todolist = a, due = today),
            TaskItem(id = "overdue", title = "overdue", todolist = a, due = today.minusDays(1)),
            TaskItem(id = "nodue", title = "no due", todolist = a),
            TaskItem(id = "donefuture", title = "done future", todolist = a, due = today.plusDays(3), done = true),
        )
        val ids = tasks.forUpcoming(today).map { it.id }.toSet()
        assertEquals(setOf("future"), ids)
    }
}
