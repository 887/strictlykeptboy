package com.eight87.strictlykeptboy.ui.tasks

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-27 / D-28 — regression guards for the completion beat + undo snackbar.
 *
 * The beat timer is driven by [TasksViewState.beatScope]; passing a
 * [TestScope] lets us advance time without real-time delays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompletionBeatTest {

    private val today = TaskFixtures.today
    private val list = TaskFixtures.listA

    private fun makeTask(id: String, done: Boolean = false) = TaskItem(
        id = id,
        title = "Test task $id",
        todolist = list,
        done = done,
        due = today,
    )

    // -------------------------------------------------------------------------
    // D-27 — completion beat
    // -------------------------------------------------------------------------

    @Test fun `toggleDone adds task to animatingDoneIds immediately`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t1"))),
            beatScope = this,
        )
        state.toggleDone("t1", today = today)
        assertTrue(
            "Task must be in animatingDoneIds right after toggleDone",
            "t1" in state.state.value.animatingDoneIds,
        )
    }

    @Test fun `after beat window task leaves animatingDoneIds and enters beatCompletedIds`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val state = TasksViewState(
                TasksUiState(tasks = listOf(makeTask("t1"))),
                beatScope = scope,
            )
            state.toggleDone("t1", today = today, beatMs = 600L)
            // Still animating immediately after toggle.
            assertTrue("t1" in state.state.value.animatingDoneIds)
            // Advance past the beat window.
            advanceTimeBy(700L)
            assertFalse(
                "animatingDoneIds must be empty after beat window",
                "t1" in state.state.value.animatingDoneIds,
            )
            assertTrue(
                "beatCompletedIds must contain task after beat window",
                "t1" in state.state.value.beatCompletedIds,
            )
        }

    @Test fun `undoDone clears animatingDoneIds and beatCompletedIds and restores done=false`() =
        runTest {
            val state = TasksViewState(
                TasksUiState(tasks = listOf(makeTask("t1"))),
                beatScope = this,
            )
            state.toggleDone("t1", today = today)
            assertTrue("t1" in state.state.value.animatingDoneIds)
            // Undo before beat completes.
            state.undoDone("t1")
            val task = state.state.value.tasks.first { it.id == "t1" }
            assertFalse("done must be false after undoDone", task.done)
            assertNull("doneAt must be null after undoDone", task.doneAt)
            assertFalse("animatingDoneIds must not contain task after undoDone",
                "t1" in state.state.value.animatingDoneIds)
            assertFalse("beatCompletedIds must not contain task after undoDone",
                "t1" in state.state.value.beatCompletedIds)
        }

    @Test fun `un-marking (done→false via toggleDone) clears beat sets immediately`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t1", done = true))),
            beatScope = this,
        )
        // Simulate a task already in animatingDoneIds (mid-beat) by having
        // beatCompletedIds populated manually.
        state.set(
            state.state.value.copy(
                animatingDoneIds = setOf("t1"),
                beatCompletedIds = setOf("t1"),
            ),
        )
        // toggleDone on an already-done task = un-mark.
        state.toggleDone("t1", today = today)
        val task = state.state.value.tasks.first { it.id == "t1" }
        assertFalse("done must be false", task.done)
        assertFalse("animatingDoneIds cleared", "t1" in state.state.value.animatingDoneIds)
        assertFalse("beatCompletedIds cleared", "t1" in state.state.value.beatCompletedIds)
    }

    // -------------------------------------------------------------------------
    // D-28 — undo snackbar callback
    // -------------------------------------------------------------------------

    @Test fun `onShowUndoSnackbar invoked with taskId when marking done`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t-snack"))),
            beatScope = this,
        )
        var capturedId: String? = null
        state.onShowUndoSnackbar = { capturedId = it }
        state.toggleDone("t-snack", today = today)
        assertEquals("undo snackbar must receive the task id", "t-snack", capturedId)
    }

    @Test fun `onShowUndoSnackbar NOT invoked when un-marking`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t-unmark", done = true))),
            beatScope = this,
        )
        var called = false
        state.onShowUndoSnackbar = { called = true }
        state.toggleDone("t-unmark", today = today) // un-mark
        assertFalse("snackbar must NOT fire on un-mark", called)
    }

    // -------------------------------------------------------------------------
    // D-26 regression — Today view section splitting
    // -------------------------------------------------------------------------

    @Test fun `overdue and today tasks are distinct when computing forToday`() {
        // Use real today so isOverdue getter (which calls LocalDate.now()) agrees.
        val realToday = java.time.LocalDate.now()
        val overdueTask = TaskItem(
            id = "overdue",
            title = "Overdue",
            todolist = list,
            due = realToday.minusDays(2),
        )
        val todayTask = TaskItem(
            id = "today-task",
            title = "Today task",
            todolist = list,
            due = realToday,
        )
        val tasks = listOf(overdueTask, todayTask)
        val forToday = tasks.forToday(realToday)
        // Both should appear in Today.
        assertEquals(2, forToday.size)
        // Overdue flag on the right task.
        assertTrue(forToday.first { it.id == "overdue" }.isOverdue)
        assertFalse(forToday.first { it.id == "today-task" }.isOverdue)
    }
}
