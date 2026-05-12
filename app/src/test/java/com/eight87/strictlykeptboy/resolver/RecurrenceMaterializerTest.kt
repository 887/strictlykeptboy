package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.rule
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class RecurrenceMaterializerTest {

    private val mat = RecurrenceMaterializer()

    @Test fun dailyRule_expandsAcrossRange() = runTest {
        val r = rule(
            "standup",
            "work",
            dtstart = "2026-05-04T09:00:00",
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=DAILY",
        )
        val out = mat.expand(
            r,
            exceptions = emptyList(),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-15")),
        )
        assertEquals(5, out.size)
        assertEquals(LocalDate.parse("2026-05-11"), out.first().effectiveStart.toLocalDate())
        assertEquals(LocalDate.parse("2026-05-15"), out.last().effectiveStart.toLocalDate())
    }

    @Test fun weeklyByDay_filtersWeekdays() = runTest {
        val r = rule(
            "standup",
            "work",
            dtstart = "2026-05-04T09:00:00", // Mon
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=WEEKLY;BYDAY=MO,WE,FR",
        )
        val out = mat.expand(
            r,
            exceptions = emptyList(),
            range = DateRange(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-17")),
        )
        // 2 weeks * 3 = 6 instances
        assertEquals(6, out.size)
        val daysOfWeek = out.map { it.effectiveStart.dayOfWeek.value }.toSet()
        assertEquals(setOf(1, 3, 5), daysOfWeek)
    }

    @Test fun monthlyBySetPos_lastFridayOfMonth() = runTest {
        val r = rule(
            "retro",
            "work",
            dtstart = "2026-01-30T15:00:00", // last Fri of Jan
            duration = Duration.ofHours(1),
            rrule = "FREQ=MONTHLY;BYDAY=FR;BYSETPOS=-1",
        )
        val out = mat.expand(
            r,
            exceptions = emptyList(),
            range = DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31")),
        )
        assertEquals(3, out.size)
        // Jan 30, Feb 27, Mar 27 (verify all are Fridays).
        out.forEach { assertEquals(5, it.effectiveStart.dayOfWeek.value) }
    }

    @Test fun count_terminatesAtCountRegardlessOfRange() = runTest {
        val r = rule(
            "limited",
            "work",
            dtstart = "2026-01-01T08:00:00",
            duration = Duration.ofMinutes(30),
            rrule = "FREQ=DAILY;COUNT=10",
        )
        val out = mat.expand(
            r,
            exceptions = emptyList(),
            range = DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
        )
        assertEquals(10, out.size)
    }

    @Test fun until_terminatesAtUntil() = runTest {
        val r = rule(
            "bounded",
            "work",
            dtstart = "2026-01-01T08:00:00",
            duration = Duration.ofMinutes(30),
            rrule = "FREQ=DAILY;UNTIL=20260105T000000Z",
        )
        val out = mat.expand(
            r,
            exceptions = emptyList(),
            range = DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
        )
        assertTrue(out.size in 4..5)
    }

    @Test fun cancelException_removesInstance() = runTest {
        val r = rule(
            "daily",
            "work",
            dtstart = "2026-05-04T09:00:00",
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=DAILY",
        )
        val ex = ExceptionInput(
            ruleId = RuleRef("daily"),
            instanceDate = LocalDate.parse("2026-05-13"),
            mode = "cancel",
        )
        val out = mat.expand(
            r,
            exceptions = listOf(ex),
            range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-15")),
        )
        assertEquals(4, out.size)
        assertTrue(out.none { it.effectiveStart.toLocalDate() == LocalDate.parse("2026-05-13") })
    }

    @Test fun overrideException_shiftsInstance() = runTest {
        val r = rule(
            "daily",
            "work",
            dtstart = "2026-05-04T09:00:00",
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=DAILY",
        )
        val moved = ExceptionInput(
            ruleId = RuleRef("daily"),
            instanceDate = LocalDate.parse("2026-05-12"),
            mode = "override",
            overrideStart = zdt("2026-05-12T10:00:00"),
            overrideEnd = zdt("2026-05-12T10:30:00"),
            overrideTitle = "moved",
        )
        val out = mat.expand(
            r,
            exceptions = listOf(moved),
            range = DateRange(LocalDate.parse("2026-05-12"), LocalDate.parse("2026-05-12")),
        )
        assertEquals(1, out.size)
        val inst = out.single()
        assertEquals(10, inst.effectiveStart.hour)
        assertEquals(9, inst.originalStart.hour)
        assertEquals("moved", inst.title)
    }

    @Test fun countWinsOverUntil() = runTest {
        // Both COUNT and UNTIL provided — COUNT should win, no exception thrown.
        val r = rule(
            "conflicting",
            "work",
            dtstart = "2026-01-01T08:00:00",
            duration = Duration.ofMinutes(30),
            rrule = "FREQ=DAILY;COUNT=3;UNTIL=20260201T000000Z",
        )
        val out = mat.expand(
            r,
            exceptions = emptyList(),
            range = DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
        )
        assertEquals(3, out.size)
    }

    @Test fun intervalEveryOtherDay() = runTest {
        val r = rule(
            "biweekly",
            "work",
            dtstart = "2026-05-04T09:00:00",
            duration = Duration.ofMinutes(15),
            rrule = "FREQ=DAILY;INTERVAL=2",
        )
        val out = mat.expand(
            r,
            exceptions = emptyList(),
            range = DateRange(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-10")),
        )
        assertEquals(4, out.size) // 5/4, 5/6, 5/8, 5/10
    }
}
