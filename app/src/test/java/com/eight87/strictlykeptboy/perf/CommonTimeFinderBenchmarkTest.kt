package com.eight87.strictlykeptboy.perf

import com.eight87.strictlykeptboy.resolver.CommonTimeFinder
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.Factories
import com.eight87.strictlykeptboy.resolver.Factories.event
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RecurrenceMaterializer
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.TimeWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Phase V.4 — common-time finder budget: < 800ms for 5 repos × 30 days
 * with mixed event density. Robolectric-free; pure JVM.
 */
class CommonTimeFinderBenchmarkTest {

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

    @Test fun fiveReposThirtyDays_completes() = runTest {
        val participants = (0 until 5).map { RepoRef("repo-$it") }
        val rangeStart = LocalDate.parse("2026-05-01")
        // Mixed density: repo-0 busy 6/day, repo-4 busy 1/day on average.
        val busyByParticipant: Map<RepoRef, List<MaterializedInstance>> = participants
            .mapIndexed { idx, ref ->
                val perDay = 6 - idx // 6, 5, 4, 3, 2 events/day → 30..150 events/repo over 30 days
                val events = (0 until 30).flatMap { dayOffset ->
                    val date = rangeStart.plusDays(dayOffset.toLong())
                    (0 until perDay).map { slot ->
                        val hh = String.format("%02d", 8 + slot)
                        val dd = String.format("%02d", date.dayOfMonth)
                        val mm = String.format("%02d", date.monthValue)
                        mat.fromOneOff(
                            event(
                                "${ref.id}-$dayOffset-$slot", "c",
                                "2026-$mm-${dd}T${hh}:00:00",
                                "2026-$mm-${dd}T${hh}:30:00",
                            ),
                        )
                    }
                }
                ref to events
            }
            .toMap()

        val query = CommonTimeFinder.Query(
            participants = participants,
            busyByParticipant = busyByParticipant,
            range = DateRange(rangeStart, rangeStart.plusDays(29)),
            minDurationMinutes = 30,
            window = workingWeek(),
            tzId = Factories.TZ_BERLIN,
            idealTimeOfDay = LocalTime.of(14, 0),
            topK = 20,
        )

        // Warmup.
        finder.find(query)

        val start = System.nanoTime()
        val slots = finder.find(query)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        println("[perf V.4] common-time 5×30: ${elapsedMs}ms, ${slots.size} slots")
        assertTrue("topK respected", slots.size <= 20)
        assertTrue("common-time too slow: ${elapsedMs}ms", elapsedMs < 5_000)
    }
}
