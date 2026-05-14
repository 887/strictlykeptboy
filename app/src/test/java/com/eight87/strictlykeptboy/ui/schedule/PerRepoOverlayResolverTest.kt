package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.TodolistMeta
import com.eight87.strictlykeptboy.resolver.TodolistRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.5.D.1 — given repo-A (showOnSchedule = true) + repo-B
 * (showOnSchedule = false), the filter retains only repo-A's bands.
 */
class PerRepoOverlayResolverTest {

    private fun repoCfg(id: String, show: Boolean): RepoConfig = RepoConfig(
        repoId = id,
        displayName = id,
        rootDir = "/tmp/$id",
        authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        showOnSchedule = show,
        drawTasksFrom = true,
    )

    private fun snapshot(): RepoSnapshot = RepoSnapshot(
        repos = listOf(
            RepoSnapshot.RepoEntry(RepoRef("a"), "sha-a"),
            RepoSnapshot.RepoEntry(RepoRef("b"), "sha-b"),
        ),
        calendars = listOf(
            CalendarMeta(
                ref = CalendarRef("cal-a"),
                repo = RepoRef("a"),
                displayName = "cal-a",
                priority = 500,
            ),
            CalendarMeta(
                ref = CalendarRef("cal-b"),
                repo = RepoRef("b"),
                displayName = "cal-b",
                priority = 500,
            ),
        ),
        todolists = listOf(
            TodolistMeta(
                ref = TodolistRef("td-a"),
                repo = RepoRef("a"),
                displayName = "td-a",
            ),
            TodolistMeta(
                ref = TodolistRef("td-b"),
                repo = RepoRef("b"),
                displayName = "td-b",
            ),
        ),
    )

    private fun emptySources(): Renderer.Sources = Renderer.Sources(
        events = emptyList(),
        rules = emptyList(),
        exceptionsByRule = emptyMap(),
        deviations = emptyList(),
        overrides = emptyList(),
    )

    @Test fun onlyShownRepoSurvivesFilter() {
        val configs = listOf(repoCfg("a", show = true), repoCfg("b", show = false))
        val (snap, _) = applyRepoOverlayFilter(snapshot(), emptySources(), configs)
        assertEquals(listOf("a"), snap.repos.map { it.ref.id })
        assertEquals(listOf("cal-a"), snap.calendars.map { it.ref.id })
        assertEquals(listOf("td-a"), snap.todolists.map { it.ref.id })
    }

    @Test fun emptyConfigsLeavesSnapshotUntouched() {
        val (snap, _) = applyRepoOverlayFilter(snapshot(), emptySources(), emptyList())
        assertEquals(2, snap.repos.size)
        assertEquals(2, snap.calendars.size)
    }

    @Test fun allReposShownIsNoOp() {
        val configs = listOf(repoCfg("a", show = true), repoCfg("b", show = true))
        val (snap, _) = applyRepoOverlayFilter(snapshot(), emptySources(), configs)
        assertEquals(2, snap.repos.size)
        assertEquals(2, snap.calendars.size)
    }

    @Test fun allReposHiddenYieldsEmpty() {
        val configs = listOf(repoCfg("a", show = false), repoCfg("b", show = false))
        val (snap, _) = applyRepoOverlayFilter(snapshot(), emptySources(), configs)
        assertTrue(snap.repos.isEmpty())
        assertTrue(snap.calendars.isEmpty())
        assertTrue(snap.todolists.isEmpty())
    }
}
