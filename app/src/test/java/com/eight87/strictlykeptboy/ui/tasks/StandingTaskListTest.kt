package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingTaskListTest {
    private val today = TaskFixtures.today
    private val a = TaskFixtures.listA
    private val b = TaskFixtures.listB

    @Test fun only_no_deadline_tasks_appear() {
        val all = listOf(
            TaskItem(id = "s1", title = "standing-a", todolist = a, standing = true, priority = 3),
            TaskItem(id = "s2", title = "standing-b", todolist = b, standing = true, priority = 7),
            TaskItem(id = "d1", title = "dated", todolist = a, due = today),
            TaskItem(id = "weird", title = "standing-with-due", todolist = a, standing = true, due = today),
        )
        val out = all.sortedForStanding().map { it.id }
        assertEquals(listOf("s2", "s1"), out) // priority desc
        assertFalse(out.contains("d1"))
        assertFalse(out.contains("weird"))
    }

    @Test fun pin_moves_to_today_view() {
        val state = TasksViewState(
            TasksUiState(
                tasks = listOf(
                    TaskItem(id = "s1", title = "standing", todolist = a, standing = true),
                ),
            ),
        )
        // Before: not in today
        assertTrue(state.state.value.tasks.first().forToday().isEmpty())
        state.pinStanding("s1", true)
        val tasks = state.state.value.tasks
        val out = tasks.forToday(today).map { it.id }
        assertEquals(listOf("s1"), out)
    }
}

private fun TaskItem.forToday(today: java.time.LocalDate = java.time.LocalDate.now()): List<TaskItem> =
    listOf(this).forToday(today)
