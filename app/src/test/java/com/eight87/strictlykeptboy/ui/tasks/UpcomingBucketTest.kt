package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * D-29 — unit tests for [forUpcomingBucketed].
 *
 * Verifies:
 *  1. Tasks land in the correct bucket (Tomorrow / ThisWeek / NextWeek / Later).
 *  2. Empty buckets are omitted from the result.
 *  3. Ordering: Tomorrow → ThisWeek → NextWeek → Later (chronological).
 *  4. Tasks excluded by [forUpcoming] (done, no-due, today-due, overdue) are
 *     excluded from bucketing too.
 */
class UpcomingBucketTest {

    // Pin to a known Wednesday so ISO-week arithmetic is deterministic.
    // 2026-05-13 is a Wednesday.
    private val today = LocalDate.of(2026, 5, 13)

    // ISO week containing today: Mon 2026-05-11 .. Sun 2026-05-17
    private val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val tomorrow = today.plusDays(1)   // Thu 2026-05-14
    private val thisFriday = thisMonday.plusDays(4)  // Fri 2026-05-15
    private val nextMonday = thisMonday.plusWeeks(1) // Mon 2026-05-18
    private val nextFriday = nextMonday.plusDays(4)  // Fri 2026-05-22
    private val later = today.plusDays(30)           // well into next+ week

    private val list = TaskFixtures.listA

    private fun task(id: String, due: LocalDate, done: Boolean = false) =
        TaskItem(id = id, title = id, todolist = list, due = due, done = done)

    // -----------------------------------------------------------------------
    // Bucket assignment
    // -----------------------------------------------------------------------

    @Test fun tomorrow_task_lands_in_Tomorrow_bucket() {
        val bucketed = listOf(task("t1", tomorrow)).forUpcomingBucketed(today)
        assertEquals(1, bucketed.size)
        assertEquals(UpcomingBucket.Tomorrow, bucketed[0].first)
        assertEquals(listOf("t1"), bucketed[0].second.map { it.id })
    }

    @Test fun this_week_task_lands_in_ThisWeek_bucket() {
        // Thursday-in-same-week (tomorrow) is actually Tomorrow, use Friday.
        val bucketed = listOf(task("t1", thisFriday)).forUpcomingBucketed(today)
        assertEquals(1, bucketed.size)
        assertEquals(UpcomingBucket.ThisWeek, bucketed[0].first)
    }

    @Test fun next_week_task_lands_in_NextWeek_bucket() {
        val bucketed = listOf(task("t1", nextMonday)).forUpcomingBucketed(today)
        assertEquals(1, bucketed.size)
        assertEquals(UpcomingBucket.NextWeek, bucketed[0].first)
    }

    @Test fun later_task_lands_in_Later_bucket() {
        val bucketed = listOf(task("t1", later)).forUpcomingBucketed(today)
        assertEquals(1, bucketed.size)
        assertEquals(UpcomingBucket.Later, bucketed[0].first)
    }

    // -----------------------------------------------------------------------
    // Ordering and empty-bucket omission
    // -----------------------------------------------------------------------

    @Test fun bucket_order_is_chronological_Tomorrow_first() {
        val tasks = listOf(
            task("later", later),
            task("next", nextFriday),
            task("this", thisFriday),
            task("tom", tomorrow),
        )
        val buckets = tasks.forUpcomingBucketed(today).map { it.first }
        assertEquals(
            listOf(UpcomingBucket.Tomorrow, UpcomingBucket.ThisWeek, UpcomingBucket.NextWeek, UpcomingBucket.Later),
            buckets,
        )
    }

    @Test fun empty_buckets_are_omitted() {
        // Only a later task — Tomorrow, ThisWeek, NextWeek absent.
        val bucketed = listOf(task("t1", later)).forUpcomingBucketed(today)
        assertEquals(1, bucketed.size)
        assertEquals(UpcomingBucket.Later, bucketed[0].first)
    }

    @Test fun empty_input_returns_empty_list() {
        assertTrue(emptyList<TaskItem>().forUpcomingBucketed(today).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Exclusion rules inherited from forUpcoming
    // -----------------------------------------------------------------------

    @Test fun done_future_tasks_are_excluded() {
        val bucketed = listOf(task("t1", tomorrow, done = true)).forUpcomingBucketed(today)
        assertTrue(bucketed.isEmpty())
    }

    @Test fun today_due_excluded() {
        val bucketed = listOf(task("t1", today)).forUpcomingBucketed(today)
        assertTrue(bucketed.isEmpty())
    }

    @Test fun overdue_excluded() {
        val bucketed = listOf(task("t1", today.minusDays(1))).forUpcomingBucketed(today)
        assertTrue(bucketed.isEmpty())
    }

    @Test fun no_due_excluded() {
        val nodue = TaskItem(id = "nd", title = "no due", todolist = list)
        assertTrue(listOf(nodue).forUpcomingBucketed(today).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Multiple tasks in same bucket
    // -----------------------------------------------------------------------

    @Test fun multiple_tasks_in_same_bucket_are_all_included() {
        val tasks = listOf(
            task("a", tomorrow),
            task("b", tomorrow),
        )
        val bucketed = tasks.forUpcomingBucketed(today)
        assertEquals(1, bucketed.size)
        assertEquals(UpcomingBucket.Tomorrow, bucketed[0].first)
        assertEquals(setOf("a", "b"), bucketed[0].second.map { it.id }.toSet())
    }
}
