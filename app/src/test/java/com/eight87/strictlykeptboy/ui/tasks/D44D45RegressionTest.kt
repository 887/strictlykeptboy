package com.eight87.strictlykeptboy.ui.tasks

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * D-44 — Undo must reverse the disk write (onTaskMarkedDone with done=false)
 *         and set a pending override so reactive snapshot refreshes don't
 *         clobber the in-memory undo.
 *
 * D-45 — Quick-add tasks must survive a reactive set() rebuild via
 *         _inMemoryTasks, and their todolist IDs must be included in
 *         activeTodolistIds so visibleTasks() does not filter them out.
 */
class D44D45RegressionTest {

    private val today = TaskFixtures.today
    private val list = TaskFixtures.listA

    private fun makeTask(id: String, done: Boolean = false) = TaskItem(
        id = id,
        title = "Task $id",
        todolist = list,
        done = done,
        due = today,
    )

    // ─── D-44: undo invokes onTaskMarkedDone with done=false ─────────────────

    @Test fun `undoDone invokes onTaskMarkedDone callback with done=false`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t1"))),
            beatScope = this,
        )
        var capturedItem: TaskItem? = null
        state.onTaskMarkedDone = { capturedItem = it }

        state.toggleDone("t1", today = today)
        state.undoDone("t1")

        assertNotNull("onTaskMarkedDone must be called after undoDone", capturedItem)
        assertFalse("item passed to callback must have done=false", capturedItem!!.done)
        assertNull("item passed to callback must have doneAt=null", capturedItem!!.doneAt)
    }

    @Test fun `undoDone sets pendingDoneOverride so next set() re-applies done=false`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t1"))),
            beatScope = this,
        )
        state.toggleDone("t1", today = today)
        state.undoDone("t1")

        val overrides = state.pendingDoneOverridesSnapshot()
        assertTrue("pendingDoneOverrides must contain taskId after undoDone",
            overrides.containsKey("t1"))
        assertEquals("pending override must be false (undo → not-done)",
            false, overrides["t1"])
    }

    @Test fun `pendingDoneOverride false forces task to not-done on simulated set() rebuild`() =
        runTest {
            val state = TasksViewState(
                TasksUiState(tasks = listOf(makeTask("t1"))),
                beatScope = this,
            )
            state.toggleDone("t1", today = today)
            state.undoDone("t1")

            // Simulate what the reactive collector does: rebuild from Room (done=true stale)
            // then apply pending overrides.
            val staleFromRoom = listOf(makeTask("t1", done = true))
            val overrides = state.pendingDoneOverridesSnapshot()
            val merged = staleFromRoom.map { t ->
                val forced = overrides[t.id]
                when {
                    forced == null -> t
                    forced == t.done -> { state.clearDoneOverride(t.id); t }
                    else -> t.copy(done = forced, doneAt = if (forced) t.doneAt else null)
                }
            }

            val result = merged.first { it.id == "t1" }
            assertFalse("stale done=true from Room must be overridden to false", result.done)
            assertNull("doneAt must be null after override", result.doneAt)
        }

    @Test fun `clearDoneOverride removes the entry`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t1"))),
            beatScope = this,
        )
        state.toggleDone("t1", today = today)
        state.undoDone("t1")

        state.clearDoneOverride("t1")

        val overrides = state.pendingDoneOverridesSnapshot()
        assertFalse("pendingDoneOverrides must be empty after clearDoneOverride",
            overrides.containsKey("t1"))
    }

    // ─── D-45: in-memory quick-add tasks survive set() rebuild ───────────────

    @Test fun `addTask appears in inMemoryTasksSnapshot`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = emptyList()),
            beatScope = this,
        )
        val newTask = TaskItem(id = "user-123", title = "D45 quick add", todolist = list)
        state.addTask(newTask)

        val snapshot = state.inMemoryTasksSnapshot()
        assertTrue("inMemoryTasksSnapshot must contain the quick-added task",
            snapshot.any { it.id == "user-123" })
    }

    @Test fun `inMemoryTasks survive a set() rebuild`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = listOf(makeTask("t-existing"))),
            beatScope = this,
        )
        val newTask = TaskItem(id = "user-456", title = "Quick add survives rebuild", todolist = list)
        state.addTask(newTask)

        // Simulate what the reactive collector does — rebuild state from Room
        // (which only has t-existing, not the quick-add).
        val fromRoom = listOf(makeTask("t-existing"))
        val inMemoryIds = fromRoom.mapTo(mutableSetOf()) { it.id }
        val merged = fromRoom + state.inMemoryTasksSnapshot().filter { it.id !in inMemoryIds }

        assertTrue("quick-add task must survive the set() merge",
            merged.any { it.id == "user-456" })
    }

    @Test fun `inMemoryTasksSnapshot returns distinct entries after multiple adds`() = runTest {
        val state = TasksViewState(
            TasksUiState(tasks = emptyList()),
            beatScope = this,
        )
        val taskA = TaskItem(id = "user-a", title = "A", todolist = list)
        val taskB = TaskItem(id = "user-b", title = "B", todolist = list)
        state.addTask(taskA)
        state.addTask(taskB)

        val snapshot = state.inMemoryTasksSnapshot()
        assertEquals("both quick-add tasks must be in snapshot", 2, snapshot.size)
        assertTrue(snapshot.any { it.id == "user-a" })
        assertTrue(snapshot.any { it.id == "user-b" })
    }
}
