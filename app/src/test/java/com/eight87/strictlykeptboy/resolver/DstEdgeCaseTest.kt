package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.snapshot
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Round 2.24 / Phase E.1 — DST edge-case corpus.
 *
 * Locks the two policy decisions promoted in Phase E.3:
 *
 * - **D-2.24.g (locked)** Spring-forward (non-existent local time):
 *   `ZonedDateTime.of(localDateTime, zone)` snaps the wall clock forward
 *   to the next valid instant (e.g. America/New_York 2026-03-08T02:30
 *   becomes 03:30 EDT; Europe/Berlin 2026-03-29T02:30 becomes
 *   03:30 CEST). The java.time defaults already implement this; the
 *   policy lock here ensures we never introduce a hand-rolled rebuild
 *   path that diverges. Recurring rules driven by dmfs lib-recur use
 *   the same snap (verified by the daily-rule case below).
 *
 * - **D-2.24.h (locked)** Fall-back (ambiguous local time): the earlier
 *   offset wins (e.g. America/New_York 2026-11-01T01:30 resolves to
 *   `01:30 -04:00` — the pre-transition EDT instance — not `01:30 -05:00`
 *   EST). `ZonedDateTime.of(...)` with no `preferredOffset` argument
 *   gives this by default; tests pin the behaviour so a future
 *   refactor can't silently flip to "later offset wins".
 *
 * These tests are the verification baseline cited in `decisions.md` D.121.
 */
class DstEdgeCaseTest {

    private val renderer = Renderer()
    private val nyc: ZoneId = ZoneId.of("America/New_York")
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    // -------------------------------------------------------------------------
    // E.1 — Spring-forward: non-existent local time snaps to next valid instant.
    // -------------------------------------------------------------------------

    @Test fun springForwardNewYorkSnapsToNextValidInstant() = runTest {
        // 2026-03-08 02:30 NYC doesn't exist (US spring forward). The instant
        // built from this local clock must resolve to 03:30 -04:00 (EDT) —
        // 1 hour ahead, not silently invalid, not the pre-transition 02:30.
        val springForwardLocal = LocalDateTime.parse("2026-03-08T02:30:00")
        val materialised = springForwardLocal.atZone(nyc)

        assertEquals(LocalDateTime.parse("2026-03-08T03:30:00"), materialised.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), materialised.offset)

        // Render the same case end-to-end to prove the policy survives the
        // full Renderer pipeline (one-off → materialise → display tz copy).
        val out = renderEvent(
            startLocal = springForwardLocal,
            durationMinutes = 30,
            tz = nyc,
            renderDay = LocalDate.parse("2026-03-08"),
        )
        val rendered = out.days.single().bands.single().instance.effectiveStart
        assertEquals(LocalDateTime.parse("2026-03-08T03:30:00"), rendered.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), rendered.offset)
    }

    @Test fun springForwardBerlinSnapsToNextValidInstant() = runTest {
        // 2026-03-29 02:30 Berlin doesn't exist (EU spring forward). Same
        // snap-forward policy: lands on 03:30 +02:00 (CEST).
        val springForwardLocal = LocalDateTime.parse("2026-03-29T02:30:00")
        val materialised = springForwardLocal.atZone(berlin)

        assertEquals(LocalDateTime.parse("2026-03-29T03:30:00"), materialised.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(2), materialised.offset)

        val out = renderEvent(
            startLocal = springForwardLocal,
            durationMinutes = 60,
            tz = berlin,
            renderDay = LocalDate.parse("2026-03-29"),
        )
        val rendered = out.days.single().bands.single().instance.effectiveStart
        assertEquals(LocalDateTime.parse("2026-03-29T03:30:00"), rendered.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(2), rendered.offset)
    }

    // -------------------------------------------------------------------------
    // E.1 — Fall-back: ambiguous local time resolves to the earlier offset.
    // -------------------------------------------------------------------------

    @Test fun fallBackNewYorkPrefersEarlierOffset() = runTest {
        // 2026-11-01 01:30 NYC happens twice (once at -04:00, once at -05:00).
        // Policy D-2.24.h: prefer the earlier (pre-transition) -04:00 EDT.
        val ambiguousLocal = LocalDateTime.parse("2026-11-01T01:30:00")
        val materialised = ambiguousLocal.atZone(nyc)

        assertEquals(ZoneOffset.ofHours(-4), materialised.offset)
        // The pre-transition instant is exactly 30 minutes after 01:00 EDT.
        val expectedInstant = LocalDateTime.parse("2026-11-01T01:30:00")
            .atOffset(ZoneOffset.ofHours(-4))
            .toInstant()
        assertEquals(expectedInstant, materialised.toInstant())

        val out = renderEvent(
            startLocal = ambiguousLocal,
            durationMinutes = 30,
            tz = nyc,
            renderDay = LocalDate.parse("2026-11-01"),
        )
        val rendered = out.days.single().bands.single().instance.effectiveStart
        assertEquals(ZoneOffset.ofHours(-4), rendered.offset)
    }

    // -------------------------------------------------------------------------
    // E.1 — Recurring rule (FREQ=DAILY) crossing a DST boundary.
    // -------------------------------------------------------------------------

    @Test fun dailyRuleAcrossSpringForwardSnapsBoundaryDayResumesAfter() = runTest {
        // Daily 02:30 NYC starting 2026-03-07 for 4 days. Day 2 (the spring
        // forward) snaps to 03:30 EDT; day 3 + day 4 resume the rule's
        // declared 02:30 local time, now on EDT (-04:00) for the rest of
        // the year. This pins lib-recur's behaviour against D-2.24.g.
        val snap = snapshot(cal("c1", tz = nyc))
        val ruleStart = LocalDateTime.parse("2026-03-07T02:30:00").atZone(nyc)
        val rule = RecurrenceInput(
            rule = RuleRef("rule1"),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            title = "daily across DST",
            dtstart = ruleStart,
            duration = Duration.ofMinutes(30),
            rrule = "FREQ=DAILY;COUNT=4",
            tzId = nyc,
        )
        val sources = Renderer.Sources(
            events = emptyList(),
            rules = listOf(rule),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        val out = renderer.render(
            range = DateRange(LocalDate.parse("2026-03-07"), LocalDate.parse("2026-03-10")),
            viewMode = ViewMode.Day,
            snapshot = snap,
            sources = sources,
            renderTz = nyc,
            now = LocalDateTime.parse("2026-03-07T00:00:00").atZone(nyc),
        )
        val starts = out.days.flatMap { it.bands }.map { it.instance.effectiveStart }
        assertEquals(4, starts.size)
        // Day 1 — pre-DST EST.
        assertEquals(LocalDateTime.parse("2026-03-07T02:30:00"), starts[0].toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-5), starts[0].offset)
        // Day 2 — DST gap day, snapped forward to 03:30 EDT.
        assertEquals(LocalDateTime.parse("2026-03-08T03:30:00"), starts[1].toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), starts[1].offset)
        // Day 3 + 4 — back to the rule's declared 02:30 local, now on EDT.
        assertEquals(LocalDateTime.parse("2026-03-09T02:30:00"), starts[2].toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), starts[2].offset)
        assertEquals(LocalDateTime.parse("2026-03-10T02:30:00"), starts[3].toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), starts[3].offset)
    }

    // -------------------------------------------------------------------------
    // E.1 — Cross-tz displayTzId conversion when only one zone has switched.
    // -------------------------------------------------------------------------

    @Test fun displayTzConversionAcrossOneSidedDstShowsCorrectOffsetDelta() = runTest {
        // US springs forward 2026-03-08; Berlin springs forward 2026-03-29.
        // On 2026-03-15, NYC is on EDT (-04:00) but Berlin is still on CET
        // (+01:00) — a 5-hour delta (vs the "normal" 6-hour delta when both
        // zones share the same DST regime). A Berlin-pinned 14:00 event
        // rendered in NYC display must land at 09:00 NYC, not 08:00.
        val snap = snapshot(cal("c1", tz = berlin))
        val berlinLocal = LocalDateTime.parse("2026-03-15T14:00:00").atZone(berlin)
        val ev = EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            title = "berlin meeting",
            start = berlinLocal,
            end = berlinLocal.plusHours(1),
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
            range = DateRange(LocalDate.parse("2026-03-15"), LocalDate.parse("2026-03-15")),
            viewMode = ViewMode.Day,
            snapshot = snap,
            sources = sources,
            renderTz = berlin,
            now = zdt("2026-03-15T06:00:00"),
            displayTzId = nyc,
        )
        val rendered = out.days.single().bands.single().instance.effectiveStart
        assertEquals(nyc, rendered.zone)
        // 5-hour delta during the one-sided-DST window.
        assertEquals(LocalDateTime.parse("2026-03-15T09:00:00"), rendered.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), rendered.offset)
        // Sanity: a Berlin-display rendering of the same event a month later
        // (when both zones share DST) would show a 6-hour delta. Spot-check.
        val laterLocal = LocalDateTime.parse("2026-05-15T14:00:00").atZone(berlin)
        val nycLater = laterLocal.withZoneSameInstant(nyc)
        assertEquals(LocalDateTime.parse("2026-05-15T08:00:00"), nycLater.toLocalDateTime())
        assertTrue(
            "Spot-check: one-sided-DST delta differs from both-sided-DST delta",
            rendered.toLocalDateTime().hour != nycLater.toLocalDateTime().hour,
        )
    }

    // -------------------------------------------------------------------------
    // Internals.
    // -------------------------------------------------------------------------

    private suspend fun renderEvent(
        startLocal: LocalDateTime,
        durationMinutes: Long,
        tz: ZoneId,
        renderDay: LocalDate,
    ): RenderedSchedule {
        val snap = snapshot(cal("c1", tz = tz))
        val start = startLocal.atZone(tz)
        val end = start.plusMinutes(durationMinutes)
        val ev = EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            title = "dst-test",
            start = start,
            end = end,
            tzId = tz.id,
        )
        val sources = Renderer.Sources(
            events = listOf(ev),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
        return renderer.render(
            range = DateRange(renderDay, renderDay),
            viewMode = ViewMode.Day,
            snapshot = snap,
            sources = sources,
            renderTz = tz,
            now = renderDay.atStartOfDay(tz),
        )
    }
}
