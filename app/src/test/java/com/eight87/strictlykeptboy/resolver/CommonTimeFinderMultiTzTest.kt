package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.TZ_BERLIN
import com.eight87.strictlykeptboy.resolver.Factories.event
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Round 2.24 / D.5 — multi-tz CommonTimeFinder coverage.
 *
 * - Two participants in different zones with overlapping local
 *   hours → intersection found correctly in UTC and returned in
 *   the viewer's tz.
 * - DST-edge: NY participant during the spring-forward window
 *   → sane intersection (relies on `ZonedDateTime` DST rules).
 * - Empty [CommonTimeFinder.Query.participantTz] map → behaviour
 *   identical to pre-D.1 (regression guard).
 */
class CommonTimeFinderMultiTzTest {

    private val mat = RecurrenceMaterializer()
    private val finder = CommonTimeFinder()

    private val NY: ZoneId = ZoneId.of("America/New_York")

    // Wide-but-finite envelope. `dayWindow` treats `toExclusive == from`
    // as zero-length, so we use 06:00 → 23:30 to give the candidate
    // a real interior on the viewer's calendar grid.
    private fun wideEnvelope() = TimeWindow(
        daysOfWeek = DayOfWeek.values().toSet(),
        from = LocalTime.of(6, 0),
        toExclusive = LocalTime.of(23, 30),
    )

    @Test fun twoParticipantsInDifferentZones_findsUtcIntersection() = runTest {
        val a = RepoRef("berlin-sub")
        val b = RepoRef("nyc-dom")
        // Both events are stored in TZ_BERLIN (factory default) but we
        // declare participant b lives in NY. Each participant is busy
        // 00:00–09:00 *local* (interpreted in their declared zone) on
        // Saturday 2026-05-09, and busy again 20:00–24:00 local.
        // Result: free 09:00–20:00 in each participant's local frame.
        // In UTC: Berlin 09:00–20:00 CEST = 07:00–18:00Z.
        //         NY 09:00–20:00 EDT = 13:00–24:00Z.
        // Intersection = 13:00–18:00Z = 15:00–20:00 Berlin / 09:00–14:00 NY.
        val busyA = listOf(
            mat.fromOneOff(event("a1", "x", "2026-05-09T00:00:00", "2026-05-09T09:00:00")),
            mat.fromOneOff(event("a2", "x", "2026-05-09T20:00:00", "2026-05-10T00:00:00")),
        )
        val busyB = listOf(
            mat.fromOneOff(event("b1", "x", "2026-05-09T00:00:00", "2026-05-09T09:00:00")),
            mat.fromOneOff(event("b2", "x", "2026-05-09T20:00:00", "2026-05-10T00:00:00")),
        )
        val q = CommonTimeFinder.Query(
            participants = listOf(a, b),
            busyByParticipant = mapOf(a to busyA, b to busyB),
            range = DateRange(LocalDate.parse("2026-05-09"), LocalDate.parse("2026-05-09")),
            minDurationMinutes = 60,
            window = wideEnvelope(),
            tzId = TZ_BERLIN,
            participantTz = mapOf(a to TZ_BERLIN, b to NY),
        )
        val slots = finder.find(q)
        assertTrue("Expected non-empty intersection", slots.isNotEmpty())
        val longest = slots.first()
        // Berlin viewer: 15:00–20:00 on 2026-05-09.
        assertEquals(zdt("2026-05-09T15:00:00"), longest.from)
        assertEquals(zdt("2026-05-09T20:00:00"), longest.toExclusive)
    }

    @Test fun dstSpringForward_nyParticipant_producesSaneIntersection() = runTest {
        // 2026-03-08 is US spring-forward (02:00 → 03:00 EST→EDT).
        // Berlin DST does not shift on the same date (Berlin shifts
        // 2026-03-29). Berlin participant busy 00:00–10:00 + 20:00–24:00
        // local; NY participant busy 00:00–08:00 + 19:00–24:00 local.
        // The finder should produce a non-empty common free window
        // crossing the DST instant without throwing.
        val a = RepoRef("berlin")
        val b = RepoRef("nyc")
        val busyA = listOf(
            mat.fromOneOff(event("a1", "x", "2026-03-08T00:00:00", "2026-03-08T10:00:00")),
            mat.fromOneOff(event("a2", "x", "2026-03-08T20:00:00", "2026-03-09T00:00:00")),
        )
        val busyB = listOf(
            mat.fromOneOff(event("b1", "x", "2026-03-08T00:00:00", "2026-03-08T08:00:00")),
            mat.fromOneOff(event("b2", "x", "2026-03-08T19:00:00", "2026-03-09T00:00:00")),
        )
        val q = CommonTimeFinder.Query(
            participants = listOf(a, b),
            busyByParticipant = mapOf(a to busyA, b to busyB),
            range = DateRange(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-08")),
            minDurationMinutes = 30,
            window = wideEnvelope(),
            tzId = TZ_BERLIN,
            participantTz = mapOf(a to TZ_BERLIN, b to NY),
        )
        val slots = finder.find(q)
        assertTrue("Expected DST-aware intersection", slots.isNotEmpty())
        // Every returned slot has from < toExclusive (no inverted
        // intervals from the DST gap).
        slots.forEach { s ->
            assertFalse(s.from.isAfter(s.toExclusive))
            assertFalse(s.from.isEqual(s.toExclusive))
        }
    }

    @Test fun emptyParticipantTzMap_matchesPreD1Behaviour() = runTest {
        // Regression guard: identical query with + without
        // participantTz (when no zones provided) must produce
        // identical slot lists.
        val a = RepoRef("a")
        val b = RepoRef("b")
        val busyA = listOf(mat.fromOneOff(event("a1", "x", "2026-05-11T10:00:00", "2026-05-11T11:00:00")))
        val busyB = listOf(mat.fromOneOff(event("b1", "x", "2026-05-11T14:00:00", "2026-05-11T15:00:00")))
        val baseQ = CommonTimeFinder.Query(
            participants = listOf(a, b),
            busyByParticipant = mapOf(a to busyA, b to busyB),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            minDurationMinutes = 60,
            window = TimeWindow(
                daysOfWeek = setOf(DayOfWeek.MONDAY),
                from = LocalTime.of(9, 0),
                toExclusive = LocalTime.of(17, 0),
            ),
            tzId = TZ_BERLIN,
        )
        val withoutMap = finder.find(baseQ)
        val withEmptyMap = finder.find(baseQ.copy(participantTz = emptyMap()))
        assertEquals(withoutMap.size, withEmptyMap.size)
        withoutMap.zip(withEmptyMap).forEach { (l, r) ->
            assertEquals(l.from, r.from)
            assertEquals(l.toExclusive, r.toExclusive)
        }
    }
}
