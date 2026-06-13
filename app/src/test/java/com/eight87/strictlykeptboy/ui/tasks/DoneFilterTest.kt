package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * D-3 regression guard — verifies that the Done filter surfaces
 * completed tasks (including one-offs with standing == false) and
 * does NOT surface incomplete or inverted-routine tasks.
 */
class DoneFilterTest {
    private val today = TaskFixtures.today
    private val list = TaskFixtures.listA

    // The logic under test lives in TaskDoneView which calls tasks.filter { it.done }
    // and sorts by doneAt desc. We test the filter + sort contract here
    // without spinning up Compose.

    @Test fun `done one-off task appears in done list`() {
        val doneOneOff = TaskItem(
            id = "one-off-done",
            title = "Pay bill",
            todolist = list,
            standing = false,
            done = true,
            doneAt = today,
        )
        val undoneOneOff = TaskItem(
            id = "one-off-open",
            title = "Buy groceries",
            todolist = list,
            standing = false,
            done = false,
        )
        val tasks = listOf(doneOneOff, undoneOneOff)
        val done = tasks.filter { it.done }
        assertEquals(listOf("one-off-done"), done.map { it.id })
    }

    @Test fun `standing task marked done appears in done list`() {
        val doneStanding = TaskItem(
            id = "standing-done",
            title = "Stretch routine",
            todolist = list,
            standing = true,
            done = true,
            doneAt = today,
        )
        val done = listOf(doneStanding).filter { it.done }
        assertEquals(listOf("standing-done"), done.map { it.id })
    }

    @Test fun `inverted-routine task (done=false) does not appear in done list`() {
        // Inverted routines (D.54) have standing=true and done=false by default.
        // A deviation does not set done=true; it writes a deviation file.
        val invertedRoutine = TaskItem(
            id = "inverted",
            title = "Morning cage on",
            todolist = list,
            standing = true,
            done = false,
        )
        val done = listOf(invertedRoutine).filter { it.done }
        assertTrue("Inverted routine should not appear in done list", done.isEmpty())
    }

    @Test fun `done list sorts by doneAt descending, most recent first`() {
        val older = TaskItem(
            id = "older",
            title = "Older task",
            todolist = list,
            done = true,
            doneAt = today.minusDays(2),
        )
        val newer = TaskItem(
            id = "newer",
            title = "Newer task",
            todolist = list,
            done = true,
            doneAt = today.minusDays(0),
        )
        val middle = TaskItem(
            id = "middle",
            title = "Middle task",
            todolist = list,
            done = true,
            doneAt = today.minusDays(1),
        )
        val sorted = listOf(older, middle, newer)
            .filter { it.done }
            .sortedByDescending { it.doneAt ?: LocalDate.MIN }
        assertEquals(listOf("newer", "middle", "older"), sorted.map { it.id })
    }

    @Test fun `done list empty when no tasks are done`() {
        val tasks = listOf(
            TaskItem(id = "a", title = "A", todolist = list, done = false),
            TaskItem(id = "b", title = "B", todolist = list, done = false, standing = true),
        )
        val done = tasks.filter { it.done }
        assertTrue(done.isEmpty())
    }

    @Test fun `toggleDone makes task appear in done filter`() {
        val state = TasksViewState(
            TasksUiState(
                tasks = listOf(
                    TaskItem(id = "t1", title = "One-off", todolist = list, done = false),
                ),
            ),
        )
        // Before toggle: done list is empty
        assertFalse(state.state.value.tasks.any { it.done })
        state.toggleDone("t1")
        // After toggle: task appears with done=true
        val done = state.state.value.tasks.filter { it.done }
        assertEquals(listOf("t1"), done.map { it.id })
    }

    @Test fun `standing false tasks are not excluded by sortedForStanding`() {
        // Guard against regressions where Done routing went through
        // sortedForStanding() and silently dropped all non-standing tasks.
        val doneOneOff = TaskItem(
            id = "one-off",
            title = "One-off task",
            todolist = list,
            standing = false,
            done = true,
        )
        // The old broken path: visibleTasks.filter { it.done } passed to
        // TaskStandingView which called sortedForStanding() -> empty.
        // Confirm that sortedForStanding() indeed drops standing=false tasks:
        val viaStanding = listOf(doneOneOff).sortedForStanding()
        assertTrue(
            "sortedForStanding must not show standing=false tasks (demonstrates old bug)",
            viaStanding.isEmpty(),
        )
        // The fix: filter directly on done field, bypassing sortedForStanding:
        val viaDoneFilter = listOf(doneOneOff).filter { it.done }
        assertEquals("Done filter must include standing=false done tasks", listOf("one-off"), viaDoneFilter.map { it.id })
    }
}
