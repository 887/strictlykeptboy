package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.ActiveSetEvaluator
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.TodolistMeta
import com.eight87.strictlykeptboy.resolver.TodolistRef
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

/**
 * Round 2.5.D.2 — only todolists from repos with `drawTasksFrom = true`
 * enter `TasksUiState.activeTodolistIds`.
 */
class PerRepoTaskFilterTest {

    private val now: ZonedDateTime = ZonedDateTime.parse("2026-05-14T10:00:00+02:00[Europe/Berlin]")

    private fun snapshot(): RepoSnapshot = RepoSnapshot(
        repos = listOf(
            RepoSnapshot.RepoEntry(RepoRef("a"), "sha-a"),
            RepoSnapshot.RepoEntry(RepoRef("b"), "sha-b"),
        ),
        calendars = emptyList(),
        todolists = listOf(
            TodolistMeta(ref = TodolistRef("td-a"), repo = RepoRef("a"), displayName = "td-a"),
            TodolistMeta(ref = TodolistRef("td-b"), repo = RepoRef("b"), displayName = "td-b"),
        ),
    )

    @Test fun onlyDrawingRepoTodolistsReachActiveSet() {
        val filtered = filterSnapshotForTasks(
            snapshot = snapshot(),
            drawTasksFromRepoIds = setOf("a"),
        )
        val ids = evaluateActiveTodolistIds(ActiveSetEvaluator(), filtered, now)
        assertEquals(setOf("td-a"), ids)
    }

    @Test fun bothReposDrawing() {
        val filtered = filterSnapshotForTasks(
            snapshot = snapshot(),
            drawTasksFromRepoIds = setOf("a", "b"),
        )
        val ids = evaluateActiveTodolistIds(ActiveSetEvaluator(), filtered, now)
        assertEquals(setOf("td-a", "td-b"), ids)
    }

    @Test fun emptyDrawSetIsIdentityFilter() {
        val filtered = filterSnapshotForTasks(
            snapshot = snapshot(),
            drawTasksFromRepoIds = emptySet(),
        )
        // Identity-passthrough: caller must guard against this themselves
        // when intent is "no tasks". Documented behaviour for back-compat.
        val ids = evaluateActiveTodolistIds(ActiveSetEvaluator(), filtered, now)
        assertEquals(setOf("td-a", "td-b"), ids)
    }
}
