package com.eight87.strictlykeptboy.ui.repos

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.resolver.ActiveSetEvaluator
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.TodolistMeta
import com.eight87.strictlykeptboy.resolver.TodolistRef
import com.eight87.strictlykeptboy.ui.schedule.applyRepoOverlayFilter
import com.eight87.strictlykeptboy.ui.tasks.evaluateActiveTodolistIds
import com.eight87.strictlykeptboy.ui.tasks.filterSnapshotForTasks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

/**
 * Round 2.5.D.4 — full three-repo scenario from docs/plans/round-2-5.md.
 *
 *  - sub-repo:        showOnSchedule = true,  drawTasksFrom = true
 *  - dom-repo:        showOnSchedule = true,  drawTasksFrom = false
 *  - shared-fun-repo: showOnSchedule = true,  drawTasksFrom = true
 *
 * Assertions:
 *  - Schedule renders all three's events / calendars (no filter applied).
 *  - Tasks come from sub + shared-fun only (dom is filtered out).
 */
class ThreeRepoIntegrationTest {

    private val now: ZonedDateTime = ZonedDateTime.parse("2026-05-14T10:00:00+02:00[Europe/Berlin]")

    private fun repo(id: String, show: Boolean, draw: Boolean) = RepoConfig(
        repoId = id,
        displayName = id,
        rootDir = "/tmp/$id",
        authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        showOnSchedule = show,
        drawTasksFrom = draw,
    )

    private fun snapshot(): RepoSnapshot = RepoSnapshot(
        repos = listOf(
            RepoSnapshot.RepoEntry(RepoRef("sub"), "sha-sub"),
            RepoSnapshot.RepoEntry(RepoRef("dom"), "sha-dom"),
            RepoSnapshot.RepoEntry(RepoRef("fun"), "sha-fun"),
        ),
        calendars = listOf(
            CalendarMeta(CalendarRef("cal-sub"), RepoRef("sub"), "cal-sub", 500),
            CalendarMeta(CalendarRef("cal-dom"), RepoRef("dom"), "cal-dom", 500),
            CalendarMeta(CalendarRef("cal-fun"), RepoRef("fun"), "cal-fun", 500),
        ),
        todolists = listOf(
            TodolistMeta(TodolistRef("td-sub"), RepoRef("sub"), "td-sub"),
            TodolistMeta(TodolistRef("td-dom"), RepoRef("dom"), "td-dom"),
            TodolistMeta(TodolistRef("td-fun"), RepoRef("fun"), "td-fun"),
        ),
    )

    private fun emptySources(): Renderer.Sources = Renderer.Sources(
        events = emptyList(),
        rules = emptyList(),
        exceptionsByRule = emptyMap(),
        deviations = emptyList(),
        overrides = emptyList(),
    )

    @Test fun scheduleSeesAllThreeReposEvents() {
        val configs = listOf(
            repo("sub", show = true, draw = true),
            repo("dom", show = true, draw = false),
            repo("fun", show = true, draw = true),
        )
        val (snap, _) = applyRepoOverlayFilter(snapshot(), emptySources(), configs)
        assertEquals(
            setOf("sub", "dom", "fun"),
            snap.repos.map { it.ref.id }.toSet(),
        )
        assertEquals(
            setOf("cal-sub", "cal-dom", "cal-fun"),
            snap.calendars.map { it.ref.id }.toSet(),
        )
    }

    @Test fun tasksComeFromSubAndFunOnly() {
        val configs = listOf(
            repo("sub", show = true, draw = true),
            repo("dom", show = true, draw = false),
            repo("fun", show = true, draw = true),
        )
        val drawIds = configs.filter { it.drawTasksFrom }.map { it.repoId }.toSet()
        val filtered = filterSnapshotForTasks(snapshot(), drawIds)
        val activeIds = evaluateActiveTodolistIds(ActiveSetEvaluator(), filtered, now)
        assertEquals(setOf("td-sub", "td-fun"), activeIds)
    }

    @Test fun vacationModeFunRepoHidden() {
        // User toggles fun-repo's showOnSchedule = false while on a no-fun
        // vacation; dom-repo's events stay visible.
        val configs = listOf(
            repo("sub", show = true, draw = true),
            repo("dom", show = true, draw = false),
            repo("fun", show = false, draw = true),
        )
        val (snap, _) = applyRepoOverlayFilter(snapshot(), emptySources(), configs)
        assertEquals(setOf("sub", "dom"), snap.repos.map { it.ref.id }.toSet())
    }
}
