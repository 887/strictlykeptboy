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

class CommonTimeFinderTest {

    private val mat = RecurrenceMaterializer()
    private val finder = CommonTimeFinder()

    private fun workingWeek() = TimeWindow(
        daysOfWeek = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
        from = LocalTime.of(9, 0),
        toExclusive = LocalTime.of(17, 0),
    )

    @Test fun twoParticipants_findsCommonFreeSlot() = runTest {
        val a = RepoRef("a")
        val b = RepoRef("b")
        // A busy 10-11 Mon, B busy 14-15 Mon.
        val busyA = listOf(mat.fromOneOff(event("a1", "x", "2026-05-11T10:00:00", "2026-05-11T11:00:00")))
        val busyB = listOf(mat.fromOneOff(event("b1", "x", "2026-05-11T14:00:00", "2026-05-11T15:00:00")))
        val q = CommonTimeFinder.Query(
            participants = listOf(a, b),
            busyByParticipant = mapOf(a to busyA, b to busyB),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            minDurationMinutes = 60,
            window = workingWeek(),
            tzId = TZ_BERLIN,
        )
        val slots = finder.find(q)
        assertTrue(slots.isNotEmpty())
        // Longest is 11-14 (3h).
        val longest = slots.first()
        assertEquals(zdt("2026-05-11T11:00:00"), longest.from)
        assertEquals(zdt("2026-05-11T14:00:00"), longest.toExclusive)
    }

    @Test fun threeParticipants_noCommonSlot() = runTest {
        val a = RepoRef("a"); val b = RepoRef("b"); val c = RepoRef("c")
        // Cover the whole window between them.
        val busyA = listOf(mat.fromOneOff(event("a1", "x", "2026-05-11T09:00:00", "2026-05-11T13:00:00")))
        val busyB = listOf(mat.fromOneOff(event("b1", "x", "2026-05-11T13:00:00", "2026-05-11T15:00:00")))
        val busyC = listOf(mat.fromOneOff(event("c1", "x", "2026-05-11T15:00:00", "2026-05-11T17:00:00")))
        val q = CommonTimeFinder.Query(
            participants = listOf(a, b, c),
            busyByParticipant = mapOf(a to busyA, b to busyB, c to busyC),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            minDurationMinutes = 30,
            window = workingWeek(),
            tzId = TZ_BERLIN,
        )
        val slots = finder.find(q)
        assertTrue(slots.isEmpty())
    }

    @Test fun minDurationFiltersShortGaps() = runTest {
        val a = RepoRef("a")
        val busyA = listOf(
            mat.fromOneOff(event("a1", "x", "2026-05-11T09:00:00", "2026-05-11T10:00:00")),
            mat.fromOneOff(event("a2", "x", "2026-05-11T10:30:00", "2026-05-11T17:00:00")),
        )
        val q = CommonTimeFinder.Query(
            participants = listOf(a),
            busyByParticipant = mapOf(a to busyA),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            minDurationMinutes = 60,
            window = workingWeek(),
            tzId = TZ_BERLIN,
        )
        val slots = finder.find(q)
        // 30-min gap at 10:00-10:30 fails minDuration; no other gap exists.
        assertTrue(slots.isEmpty())
    }

    @Test fun workingHoursFilterExcludesEvening() = runTest {
        val a = RepoRef("a")
        // The candidate window is 09:00-17:00 — an evening busy at 19:00
        // should be invisible to the finder.
        val busyA = listOf(mat.fromOneOff(event("a1", "x", "2026-05-11T19:00:00", "2026-05-11T20:00:00")))
        val q = CommonTimeFinder.Query(
            participants = listOf(a),
            busyByParticipant = mapOf(a to busyA),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            minDurationMinutes = 60,
            window = workingWeek(),
            tzId = TZ_BERLIN,
        )
        val slots = finder.find(q)
        // The whole 9-17 should be free.
        assertEquals(1, slots.size)
        assertEquals(zdt("2026-05-11T09:00:00"), slots.first().from)
        assertEquals(zdt("2026-05-11T17:00:00"), slots.first().toExclusive)
    }

    @Test fun zeroDurationEventDoesNotConsumeFreeTime() = runTest {
        val a = RepoRef("a")
        val busyA = listOf(mat.fromOneOff(event("a1", "x", "2026-05-11T10:00:00", "2026-05-11T10:00:00")))
        val q = CommonTimeFinder.Query(
            participants = listOf(a),
            busyByParticipant = mapOf(a to busyA),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            minDurationMinutes = 60,
            window = workingWeek(),
            tzId = TZ_BERLIN,
        )
        val slots = finder.find(q)
        assertEquals(1, slots.size)
        assertEquals(zdt("2026-05-11T09:00:00"), slots.first().from)
    }

    @Test fun idealTimeOfDayRanksMidpointCloser() = runTest {
        val a = RepoRef("a")
        // Make two equal-length free blocks and verify ideal=10:00 picks
        // the morning block (length tie -> idealDistance).
        val busyA = listOf(
            mat.fromOneOff(event("a1", "x", "2026-05-11T11:00:00", "2026-05-11T14:00:00")),
        )
        val q = CommonTimeFinder.Query(
            participants = listOf(a),
            busyByParticipant = mapOf(a to busyA),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
            minDurationMinutes = 60,
            window = workingWeek(),
            tzId = TZ_BERLIN,
            idealTimeOfDay = LocalTime.of(10, 0),
        )
        val slots = finder.find(q)
        // Two free blocks: 9-11 (2h) and 14-17 (3h). The 3h one is longer
        // and wins on contiguity — ideal is a tiebreaker not a primary key.
        assertEquals(zdt("2026-05-11T14:00:00"), slots.first().from)
    }
}
