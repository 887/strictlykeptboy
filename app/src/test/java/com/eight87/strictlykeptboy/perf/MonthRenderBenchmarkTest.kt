package com.eight87.strictlykeptboy.perf

import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.Factories
import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.event
import com.eight87.strictlykeptboy.resolver.Factories.rule
import com.eight87.strictlykeptboy.resolver.Factories.snapshot
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.ViewMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * Phase V.3 — month render budget: < 200ms on-device for 200 events
 * spread across a calendar month, plus 5 recurrence rules. Robolectric
 * is slower than the device; we record + log but only fail on a
 * pathologically slow run (> 5s) to guard against regressions.
 */
class MonthRenderBenchmarkTest {

    private val renderer = Renderer()

    @Test fun monthWith200EventsAnd5Rules_completes() = runTest {
        val snap = snapshot(cal("c1"))
        val events = (0 until 200).map { i ->
            val day = (i % 28) + 1
            val hour = (i % 12) + 7 // 07-18 inclusive
            val dd = String.format("%02d", day)
            val hh = String.format("%02d", hour)
            event("ev-$i", "c1", "2026-05-${dd}T${hh}:00:00", "2026-05-${dd}T${hh}:30:00")
        }
        val rules = listOf(
            rule("morning-standup", "c1",
                dtstart = "2026-05-04T09:00:00",
                duration = Duration.ofMinutes(15),
                rrule = "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR"),
            rule("weekly-review", "c1",
                dtstart = "2026-05-01T17:00:00",
                duration = Duration.ofMinutes(60),
                rrule = "FREQ=WEEKLY;BYDAY=FR"),
            rule("monthly-checkin", "c1",
                dtstart = "2026-05-15T14:00:00",
                duration = Duration.ofMinutes(45),
                rrule = "FREQ=MONTHLY"),
            rule("biweekly-1on1", "c1",
                dtstart = "2026-05-05T11:00:00",
                duration = Duration.ofMinutes(30),
                rrule = "FREQ=WEEKLY;INTERVAL=2"),
            rule("daily-lunch", "c1",
                dtstart = "2026-05-01T12:00:00",
                duration = Duration.ofMinutes(60),
                rrule = "FREQ=DAILY"),
        )
        val sources = Renderer.Sources(
            events = events,
            rules = rules,
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )

        // Warmup pass — Kotlin / java.time / dmfs RRULE machinery is JIT-cold.
        renderer.render(
            DateRange(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")),
            ViewMode.Month, snap, sources,
            renderTz = Factories.TZ_BERLIN,
            now = zdt("2026-05-15T08:00:00"),
        )

        val start = System.nanoTime()
        val out = renderer.render(
            DateRange(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")),
            ViewMode.Month, snap, sources,
            renderTz = Factories.TZ_BERLIN,
            now = zdt("2026-05-15T08:00:00"),
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        println("[perf V.3] month-render 200 events + 5 rules: ${elapsedMs}ms")
        assertTrue("days.size", out.days.size == 31)
        // On-device target is < 200ms. Robolectric is slower; the JVM
        // budget is generous — only fail on pathological slowness.
        assertTrue("monthly render too slow: ${elapsedMs}ms", elapsedMs < 5_000)
    }
}
