package com.eight87.strictlykeptboy.ui.tasks

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-36 / D-37 regression guards.
 *
 * D-36 — Post-confirm touch-through fix. The guard state lives inside
 * [TaskRow] (Compose-local), which is not testable in JVM unit tests;
 * however, we can verify that:
 *  1. `toggleDone` + `addTask` (the state-layer calls that D-36's fix
 *     surrounds) still work correctly — i.e. the D-36 fix did NOT
 *     accidentally break the completion beat or the done-state toggle.
 *  2. Multiple rapid completions (burst-complete scenario) all land
 *     correctly in `animatingDoneIds`.
 *
 * D-37 — Quick-add in-memory reliability: `addTask` must append the
 * task to the live state so it appears in Today/All immediately. This
 * validates the in-memory half of the D-37 fix (the disk-write half
 * requires an integration test with a real repo).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class D36D37RegressionTest {

    private val list = TaskFixtures.listA
    private val today = TaskFixtures.today

    private fun makeTask(id: String) = TaskItem(
        id = id,
        title = "Task $id",
        todolist = list,
        due = today,
    )

    // -------------------------------------------------------------------------
    // D-36 — ensure the beat / toggle still work after the confirmJustDismissed
    // guard was introduced (regression-lock on D-27 not breaking).
    // -------------------------------------------------------------------------

    @Test fun `D36 toggleDone still marks done and adds to animatingDoneIds`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t1"), makeTask("t2"))),
            beatScope = TestScope(),
        )
        state.toggleDone("t1", today = today)
        val s = state.state.value
        assertTrue("t1 must be done", s.tasks.first { it.id == "t1" }.done)
        assertTrue("t1 must be in animatingDoneIds", "t1" in s.animatingDoneIds)
        assertFalse("t2 must not be done", s.tasks.first { it.id == "t2" }.done)
    }

    @Test fun `D36 burst complete three rapid toggleDone calls all land in animatingDoneIds`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("a"), makeTask("b"), makeTask("c"))),
            beatScope = TestScope(),
        )
        state.toggleDone("a", today = today)
        state.toggleDone("b", today = today)
        state.toggleDone("c", today = today)
        val s = state.state.value
        assertEquals(setOf("a", "b", "c"), s.animatingDoneIds)
    }

    @Test fun `D36 un-marking a beating task clears animatingDoneIds immediately`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t1"))),
            beatScope = TestScope(),
        )
        state.toggleDone("t1", today = today)
        assertTrue("must be animating after first toggle", "t1" in state.state.value.animatingDoneIds)
        // un-mark — should clear the beat and done state
        state.toggleDone("t1", today = today)
        assertFalse("must NOT be animating after un-mark", "t1" in state.state.value.animatingDoneIds)
        assertFalse("must NOT be done after un-mark", state.state.value.tasks.first { it.id == "t1" }.done)
    }

    // -------------------------------------------------------------------------
    // D-37 — in-memory add reliability: addTask must surface in state immediately.
    // -------------------------------------------------------------------------

    @Test fun `D37 addTask appends task to live state`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("existing"))),
            beatScope = TestScope(),
        )
        val newTask = makeTask("new")
        state.addTask(newTask)
        val tasks = state.state.value.tasks
        assertEquals(2, tasks.size)
        assertTrue("new task must be in state", tasks.any { it.id == "new" })
    }

    @Test fun `D37 addTask preserves existing tasks`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("a"), makeTask("b"))),
            beatScope = TestScope(),
        )
        state.addTask(makeTask("c"))
        val tasks = state.state.value.tasks
        assertEquals(3, tasks.size)
        assertTrue(tasks.any { it.id == "a" })
        assertTrue(tasks.any { it.id == "b" })
        assertTrue(tasks.any { it.id == "c" })
    }

    @Test fun `D37 materialize TodolistTarget uses the correct list`() {
        val groceries = TasksDemoSeed.groceries
        val req = TaskQuickAddRequest(
            title = "eggs",
            target = QuickAddTarget.Todolist(groceries),
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("eggs", item.title)
        assertEquals(groceries.id, item.todolist.id)
    }
}
