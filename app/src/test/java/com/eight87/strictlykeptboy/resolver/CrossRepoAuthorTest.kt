package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.event
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/**
 * Round 2.1.L.2 — cross-repo author chip pipeline.
 *
 * The unified schedule view renders the union of all enabled calendars
 * across all repos. An event authored by a `dom-persona` (PersonRef
 * carrying the persona id) and committed into repo-A's dom-overlay
 * calendar must surface its `author` on the rendered [DayBand] even
 * when the user's active write-target context is repo-B — that's the
 * signal the UI keys off to paint a `ForeignEventSourceChip` with the
 * author chip + repo dot (D-2.1.e).
 *
 * Pre-2.1.C the band-level `author` plumbing was missing; this test
 * pins that the field now round-trips event → MaterializedInstance →
 * DayBand even across repo boundaries.
 */
class CrossRepoAuthorTest {

    private val renderer = Renderer()

    @Test fun authorOnForeignRepoEvent_isCarriedThroughToDayBand() = runTest {
        // Two calendars from two distinct repos. repoB is the user's
        // active write-target; repoA owns the dom-overlay calendar.
        val snap = RepoSnapshot(
            repos = listOf(
                RepoSnapshot.RepoEntry(RepoRef("repoA"), "sha-A"),
                RepoSnapshot.RepoEntry(RepoRef("repoB"), "sha-B"),
            ),
            calendars = listOf(
                cal(id = "dom-overlay", repo = "repoA"),
                cal(id = "work",        repo = "repoB"),
            ),
            todolists = emptyList(),
        )
        val domEvent = event(
            id = "e-dom-1",
            cal = "dom-overlay",
            start = "2026-05-11T10:00:00",
            end = "2026-05-11T11:00:00",
            repo = "repoA",
            author = PersonRef("dom-persona"),
        )
        val workEvent = event(
            id = "e-work-1",
            cal = "work",
            start = "2026-05-11T12:00:00",
            end = "2026-05-11T13:00:00",
            repo = "repoB",
        )
        val sources = Renderer.Sources(
            events = listOf(domEvent, workEvent),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            ViewMode.Day, snap, sources,
            now = zdt("2026-05-11T08:00:00"),
        )

        // Both bands must render (unified view across repos).
        val bands = out.days.single().bands
        assertEquals(2, bands.size)

        // The dom-overlay band, sourced from repoA, must still carry
        // its `author = dom-persona` on the band's MaterializedInstance.
        val domBand = bands.single { it.instance.calendar == CalendarRef("dom-overlay") }
        assertNotNull(
            "cross-repo dom-persona event must surface author through to DayBand",
            domBand.instance.author,
        )
        assertEquals(PersonRef("dom-persona"), domBand.instance.author)
        assertEquals(RepoRef("repoA"), domBand.instance.repo)

        // The local work event has no author and must not invent one.
        val workBand = bands.single { it.instance.calendar == CalendarRef("work") }
        assertEquals(null, workBand.instance.author)
        assertEquals(RepoRef("repoB"), workBand.instance.repo)
    }
}
