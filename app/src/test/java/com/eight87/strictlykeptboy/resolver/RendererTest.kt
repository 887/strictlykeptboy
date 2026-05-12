package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.event
import com.eight87.strictlykeptboy.resolver.Factories.rule
import com.eight87.strictlykeptboy.resolver.Factories.snapshot
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class RendererTest {

    private val renderer = Renderer()

    @Test fun dayView_singleDayRender() = runTest {
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
            renderTz = Factories.TZ_BERLIN,
            now = zdt("2026-05-11T08:00:00"),
        )
        assertEquals(1, out.days.size)
        assertEquals(1, out.days.single().bands.size)
        assertEquals(ViewMode.Day, out.viewMode)
    }

    @Test fun weekView_sevenDays() = runTest {
        val snap = snapshot(cal("c1"))
        val sources = Renderer.Sources(
            events = listOf(
                event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T11:00:00"),
                event("b", "c1", "2026-05-14T10:00:00", "2026-05-14T11:00:00"),
            ),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-17")),
            ViewMode.Week, snap, sources,
            now = zdt("2026-05-11T08:00:00"),
        )
        assertEquals(7, out.days.size)
        assertEquals(1, out.days[0].bands.size)
        assertEquals(0, out.days[1].bands.size)
        assertEquals(1, out.days[3].bands.size)
    }

    @Test fun recurringRule_materializesInRender() = runTest {
        val snap = snapshot(cal("c1"))
        val r = rule(
            "daily", "c1",
            dtstart = "2026-05-04T09:00:00",
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=DAILY",
        )
        val sources = Renderer.Sources(
            events = emptyList(),
            rules = listOf(r),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-13")),
            ViewMode.Week, snap, sources,
            now = zdt("2026-05-11T08:00:00"),
        )
        assertEquals(3, out.days.sumOf { it.bands.size })
    }

    @Test fun supersededBands_filteredFromRender() = runTest {
        val snap = snapshot(
            cal("work"),
            cal("vacation", priority = 999, supersedes = listOf("work")),
        )
        val sources = Renderer.Sources(
            events = listOf(event("a", "work", "2026-05-11T10:00:00", "2026-05-11T11:00:00")),
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
        // ActiveSetEvaluator drops the superseded calendar entirely, so its
        // events never enter the render.
        assertTrue(out.days.single().bands.isEmpty())
    }

    @Test fun offSchedule_taggedWhenBeyondBaselineCadence() = runTest {
        val snap = snapshot(cal("c1", baselineCadenceDays = 1))
        val r = rule(
            "daily", "c1",
            dtstart = "2026-05-01T09:00:00",
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=DAILY",
        )
        // One-off event far in the future from any rule dtstart
        val far = event("far", "c1", "2026-11-30T10:00:00", "2026-11-30T11:00:00")
        val sources = Renderer.Sources(
            events = listOf(far),
            rules = listOf(r),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            DateRange(LocalDate.parse("2026-11-30"), LocalDate.parse("2026-11-30")),
            ViewMode.Day, snap, sources,
            now = zdt("2026-05-11T08:00:00"),
        )
        val band = out.days.single().bands.first { it.instance.instanceId == "far" }
        assertTrue(band.offSchedule)
    }

    @Test fun offSchedule_notTaggedWhenCadenceNotSet() = runTest {
        val snap = snapshot(cal("c1"))
        val r = rule(
            "daily", "c1",
            dtstart = "2026-05-01T09:00:00",
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=DAILY",
        )
        val sources = Renderer.Sources(
            events = listOf(event("far", "c1", "2026-11-30T10:00:00", "2026-11-30T11:00:00")),
            rules = listOf(r),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            DateRange(LocalDate.parse("2026-11-30"), LocalDate.parse("2026-11-30")),
            ViewMode.Day, snap, sources,
            now = zdt("2026-05-11T08:00:00"),
        )
        val band = out.days.single().bands.first { it.instance.instanceId == "far" }
        assertFalse(band.offSchedule)
    }

    @Test fun densityBucket_reflectsBandCount() = runTest {
        val snap = snapshot(cal("c1"))
        val sources = Renderer.Sources(
            events = (1..5).map { event("e$it", "c1", "2026-05-11T${10 + it}:00:00", "2026-05-11T${10 + it}:30:00") },
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            ViewMode.Month, snap, sources,
            now = zdt("2026-05-11T08:00:00"),
        )
        assertEquals(2, out.days.single().densityBucket) // 5 events => bucket 2 (4-7)
    }
}
