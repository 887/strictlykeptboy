package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 2.1.D.1 — verifies that [visibleTasks] drops tasks belonging to
 * inactive todolists by default and keeps them when [TasksUiState.showInactive]
 * is set, plus that the chip-filter [hiddenTodolistIds] still applies.
 */
class ActiveTodolistFilterTest {
    private val a = TaskFixtures.listA
    private val b = TaskFixtures.listB

    private val tasks = listOf(
        TaskItem(id = "1", title = "from-a", todolist = a),
        TaskItem(id = "2", title = "from-b", todolist = b),
    )

    @Test fun inactive_dropped_by_default() {
        val s = TasksUiState(
            tasks = tasks,
            todolists = listOf(a, b),
            activeTodolistIds = setOf("a"),
        )
        assertEquals(listOf("1"), s.visibleTasks().map { it.id })
    }

    @Test fun show_inactive_keeps_them() {
        val s = TasksUiState(
            tasks = tasks,
            todolists = listOf(a, b),
            activeTodolistIds = setOf("a"),
            showInactive = true,
        )
        assertEquals(setOf("1", "2"), s.visibleTasks().map { it.id }.toSet())
    }

    @Test fun unknown_active_means_show_all() {
        // Empty activeTodolistIds = "evaluator hasn't run yet" — we don't
        // want a boot flash of empty tasks, so everything stays visible.
        val s = TasksUiState(
            tasks = tasks,
            todolists = listOf(a, b),
            activeTodolistIds = emptySet(),
        )
        assertEquals(setOf("1", "2"), s.visibleTasks().map { it.id }.toSet())
    }

    @Test fun hidden_chip_excludes_list() {
        val s = TasksUiState(
            tasks = tasks,
            todolists = listOf(a, b),
            activeTodolistIds = setOf("a", "b"),
            hiddenTodolistIds = setOf("b"),
        )
        assertEquals(listOf("1"), s.visibleTasks().map { it.id })
    }
}
