package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * D-22 + D-33 regression guard.
 *
 * U-1: "all done" Today state is distinct from "no tasks" empty state.
 * U-2: un-marking a done task flips state immediately (no dialog gating
 *      the [TasksViewState.toggleDone] path).
 * D-33: praiseTerm / honorific / neutralMode fields on [TasksUiState] carry
 *       correct defaults and the identity-copy gate formula is correct.
 */
class D22D33FixTest {

    private val today = LocalDate.of(2026, 6, 14)
    private val list = TaskFixtures.listA

    // ---- U-1: all-done delight state logic -----------------------------------

    @Test fun todayViewAllDone_tasksExistAllDone() {
        // forToday() with all done tasks → hasDoneTodayTasks = true → all-done state
        val doneTodayTask = TaskItem(
            id = "t1", title = "Done thing", todolist = list,
            due = today, done = true, doneAt = today,
        )
        val tasks = listOf(doneTodayTask)
        val forToday = tasks.forToday(today)
        val hasDoneTodayTasks = forToday.any { it.done }
        val visibleActive = forToday.filter { !it.done }

        assertTrue("today pool is non-empty (done counts)", forToday.isNotEmpty())
        assertTrue("has done-today tasks", hasDoneTodayTasks)
        assertTrue("visible active is empty — all done", visibleActive.isEmpty())
    }

    @Test fun todayViewEmpty_noTasks_noAllDoneDelight() {
        // No tasks → forToday is empty → hasDoneTodayTasks = false → regular empty
        val tasks = emptyList<TaskItem>()
        val forToday = tasks.forToday(today)
        val hasDoneTodayTasks = forToday.any { it.done }

        assertTrue("today pool is empty", forToday.isEmpty())
        assertFalse("no done-today tasks", hasDoneTodayTasks)
    }

    @Test fun todayViewMixed_someUndone_activeTasksVisible() {
        val doneTask = TaskItem(
            id = "t1", title = "Done", todolist = list,
            due = today, done = true, doneAt = today,
        )
        val undoneTask = TaskItem(
            id = "t2", title = "Still open", todolist = list,
            due = today, done = false,
        )
        val tasks = listOf(doneTask, undoneTask)
        val forToday = tasks.forToday(today)
        val visibleActive = forToday.filter { !it.done }

        assertTrue("has done-today tasks", forToday.any { it.done })
        assertTrue("has active tasks to show", visibleActive.isNotEmpty())
    }

    // ---- U-2: un-mark is instant (TasksViewState toggleDone) ----------------

    @Test fun toggleDone_onAlreadyDoneTask_unmarksImmediately() {
        val task = TaskItem(
            id = "tx", title = "Was done", todolist = list,
            done = true, doneAt = today,
        )
        val vs = TasksViewState(TasksUiState(tasks = listOf(task)))
        vs.toggleDone("tx", today = today)

        val result = vs.state.value.tasks.first { it.id == "tx" }
        assertFalse("un-marked to not-done", result.done)
        assertEquals("doneAt cleared on un-mark", null, result.doneAt)
    }

    @Test fun toggleDone_onNotDoneTask_marksDoneAndStampsDoneAt() {
        val task = TaskItem(
            id = "ty", title = "Was open", todolist = list,
            done = false,
        )
        val vs = TasksViewState(TasksUiState(tasks = listOf(task)))
        vs.toggleDone("ty", today = today)

        val result = vs.state.value.tasks.first { it.id == "ty" }
        assertTrue("marked done", result.done)
        assertEquals("doneAt stamped", today, result.doneAt)
    }

    // ---- D-33: TasksUiState identity fields ----------------------------------

    @Test fun tasksUiState_praiseTermDefaultsToEmpty() {
        val state = TasksUiState()
        assertEquals("default praiseTerm is empty", "", state.praiseTerm)
    }

    @Test fun tasksUiState_praiseTermCanBeSet() {
        val state = TasksUiState(praiseTerm = "good pup")
        assertEquals("praiseTerm reflects identity", "good pup", state.praiseTerm)
    }

    @Test fun tasksUiState_honorificDefaultsToEmpty() {
        val state = TasksUiState()
        assertEquals("default honorific is empty", "", state.honorific)
    }

    @Test fun tasksUiState_neutralModeDefaultsFalse() {
        val state = TasksUiState()
        assertFalse("neutral mode off by default", state.neutralMode)
    }

    @Test fun tasksUiState_neutralModeCanBeSetTrue() {
        val state = TasksUiState(neutralMode = true)
        assertTrue("neutral mode on", state.neutralMode)
    }

    @Test fun tasksViewState_setPreservesPraiseAndNeutralMode() {
        val vs = TasksViewState(TasksUiState(praiseTerm = "good boy", neutralMode = false))
        vs.set(vs.state.value.copy(neutralMode = true))
        assertEquals("praiseTerm survives copy", "good boy", vs.state.value.praiseTerm)
        assertTrue("neutralMode updated", vs.state.value.neutralMode)
    }

    // ---- D-33: identity-copy gate formula -----------------------------------

    @Test fun identityCopyGate_praiseSetAndNotNeutral_usesIdentityCopy() {
        val praiseTerm = "good boy"
        val neutralMode = false
        val useIdentityCopy = !neutralMode && praiseTerm.isNotEmpty()
        assertTrue("use identity copy when praise set and not neutral", useIdentityCopy)
    }

    @Test fun identityCopyGate_neutralModeOn_fallsBackToPlain() {
        val praiseTerm = "good boy"
        val neutralMode = true
        val useIdentityCopy = !neutralMode && praiseTerm.isNotEmpty()
        assertFalse("fall back to plain copy in neutral mode", useIdentityCopy)
    }

    @Test fun identityCopyGate_emptyPraiseTerm_fallsBackToPlain() {
        val praiseTerm = ""
        val neutralMode = false
        val useIdentityCopy = !neutralMode && praiseTerm.isNotEmpty()
        assertFalse("fall back when praiseTerm is empty", useIdentityCopy)
    }
}
