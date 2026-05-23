package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.event
import com.eight87.strictlykeptboy.resolver.Factories.snapshot
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Round 2.1.C.4 — supersedence visibility reversal.
 *
 * Previously `Renderer.filterForViewMode` dropped any band whose
 * `supersededByCalendar` tag was non-null, and `ActiveSetEvaluator`
 * excluded the superseded calendar from the active set entirely. That
 * meant a vacation overlay silently erased the paused routines from the
 * schedule — the user couldn't tell the difference between "no events"
 * and "events paused by overlay".
 *
 * This test pins the new contract: superseded bands flow through with
 * `supersededByCalendar != null`, and the UI is then free to render
 * them paused (alpha 0.35 + strikethrough + leaf glyph).
 */
class RendererSupersedenceTest {

    private val renderer = Renderer()

    @Test fun supersededBands_areKeptAndTagged() = runTest {
        val snap = snapshot(
            cal("work"),
            cal("vacation", priority = 999, supersedes = listOf("work")),
        )
        // Round 2026-05-23 — supersedence now requires the suppressor
        // to have an instance covering this day. Feed a vacation event
        // so the supersedence path activates.
        val sources = Renderer.Sources(
            events = listOf(
                event("a", "work", "2026-05-11T10:00:00", "2026-05-11T11:00:00"),
                event("v", "vacation", "2026-05-11T00:00:00", "2026-05-12T00:00:00"),
            ),
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
        val workBand = out.days.single().bands
            .single { it.instance.calendar == CalendarRef("work") }
        assertNotNull("superseded band must remain in the render output", workBand.supersededByCalendar)
        assertEquals(CalendarRef("vacation"), workBand.supersededByCalendar)
    }

    @Test fun nonSupersededBands_areNotTagged() = runTest {
        val snap = snapshot(cal("c1"))
        val sources = Renderer.Sources(
            events = listOf(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T11:00:00")),
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
        val band = out.days.single().bands.single()
        assertNull(band.supersededByCalendar)
    }

    @Test fun accentColorSeed_pipedFromCalendarMeta() = runTest {
        val snap = snapshot(cal("c1", colorSeed = 12345))
        val sources = Renderer.Sources(
            events = listOf(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T11:00:00")),
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
        val band = out.days.single().bands.single()
        assertEquals(12345, band.accentColorSeed)
    }

    @Test fun kind_pipedFromCalendarMeta() = runTest {
        val snap = snapshot(cal("c1", kind = CalendarKind.Timebox))
        val sources = Renderer.Sources(
            events = listOf(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T11:00:00")),
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
        val band = out.days.single().bands.single()
        assertEquals(CalendarKind.Timebox, band.kind)
    }
}
