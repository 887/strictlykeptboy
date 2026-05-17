package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.snapshot
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.24 / Phase B.4 — `Renderer.render(displayTzId = ...)` tests.
 *
 * Covers D-2.24.a + D-2.24.c: a Berlin-pinned event rendered in a NYC
 * display zone shifts by the offset delta; an event whose source zone
 * matches the display zone renders at its source clock time.
 */
class RendererDisplayTzTest {

    private val renderer = Renderer()
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val nyc: ZoneId = ZoneId.of("America/New_York")

    @Test fun displayTzShiftsPinnedEventByOffsetDelta() = runTest {
        val snap = snapshot(cal("c1"))
        // Berlin 14:00 on 2026-05-11 = NYC 08:00 (CEST -> EDT = 6h delta).
        val berlinStart = LocalDateTime.parse("2026-05-11T14:00:00").atZone(berlin)
        val berlinEnd = berlinStart.plusHours(1)
        val ev = EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            title = "berlin meeting",
            start = berlinStart,
            end = berlinEnd,
            tzId = "Europe/Berlin",
        )
        val sources = Renderer.Sources(
            events = listOf(ev),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            viewMode = ViewMode.Day,
            snapshot = snap,
            sources = sources,
            renderTz = berlin,
            now = zdt("2026-05-11T06:00:00"),
            displayTzId = nyc,
        )
        val band = out.days.single().bands.single()
        val rendered = band.instance.effectiveStart
        assertEquals(nyc, rendered.zone)
        // Same absolute instant.
        assertEquals(berlinStart.toInstant(), rendered.toInstant())
        // Local clock shifted: 14:00 Berlin -> 08:00 NYC.
        assertEquals(LocalDateTime.parse("2026-05-11T08:00:00"), rendered.toLocalDateTime())
        // sourceTzId pin survived.
        assertEquals("Europe/Berlin", band.instance.sourceTzId)
    }

    @Test fun displayTzMatchingSourceKeepsSourceClockTime() = runTest {
        val snap = snapshot(cal("c1"))
        val berlinStart: ZonedDateTime =
            LocalDateTime.parse("2026-05-11T09:30:00").atZone(berlin)
        val berlinEnd = berlinStart.plusMinutes(45)
        val ev = EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            title = "berlin meeting",
            start = berlinStart,
            end = berlinEnd,
            tzId = "Europe/Berlin",
        )
        val sources = Renderer.Sources(
            events = listOf(ev),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            viewMode = ViewMode.Day,
            snapshot = snap,
            sources = sources,
            renderTz = berlin,
            now = zdt("2026-05-11T06:00:00"),
            displayTzId = berlin,
        )
        val rendered = out.days.single().bands.single().instance.effectiveStart
        assertEquals(berlin, rendered.zone)
        assertEquals(LocalDateTime.parse("2026-05-11T09:30:00"), rendered.toLocalDateTime())
    }

    @Test fun displayTzNullPreservesExistingRenderTzSemantics() = runTest {
        val snap = snapshot(cal("c1"))
        val berlinStart = LocalDateTime.parse("2026-05-11T09:30:00").atZone(berlin)
        val ev = EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            title = "berlin meeting",
            start = berlinStart,
            end = berlinStart.plusHours(1),
            tzId = "Europe/Berlin",
        )
        val sources = Renderer.Sources(
            events = listOf(ev),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            viewMode = ViewMode.Day,
            snapshot = snap,
            sources = sources,
            renderTz = berlin,
            now = zdt("2026-05-11T06:00:00"),
            // displayTzId defaults to null
        )
        val rendered = out.days.single().bands.single().instance.effectiveStart
        // Unchanged from caller-supplied start.
        assertEquals(berlinStart, rendered)
    }
}
