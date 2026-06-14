package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/**
 * D-40 regression guard — verifies that a Quick Add request with a due
 * date materializes into a TaskItem with the correct due date so it
 * appears in the Upcoming "Tomorrow" bucket.
 *
 * Also verifies that the no-date path still produces a standing-style
 * item with due = null (no Upcoming appearance).
 */
class D40DueDateQuickAddTest {

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)
    private val groceries = TasksDemoSeed.groceries

    // ─── materialize() due-date propagation ──────────────────────────────────

    @Test fun `todolist target with no due produces item with null due`() {
        val req = TaskQuickAddRequest(
            title = "Standing chore",
            target = QuickAddTarget.Todolist(groceries),
            due = null,
        )
        val item = TasksDemoSeed.materialize(req)
        assertNull("null due should produce standing task with no due date", item.due)
    }

    @Test fun `todolist target with today due produces item due today`() {
        val req = TaskQuickAddRequest(
            title = "Same-day errand",
            target = QuickAddTarget.Todolist(groceries),
            due = today,
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("item should be due today", today, item.due)
    }

    @Test fun `todolist target with tomorrow due produces item due tomorrow`() {
        val req = TaskQuickAddRequest(
            title = "Plan for tomorrow",
            target = QuickAddTarget.Todolist(groceries),
            due = tomorrow,
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("item should be due tomorrow", tomorrow, item.due)
    }

    // ─── Upcoming bucket: tomorrow-dated item should land in Tomorrow bucket ──

    @Test fun `tomorrow-dated quick-add task lands in upcoming Tomorrow bucket`() {
        val req = TaskQuickAddRequest(
            title = "Tomorrow task",
            target = QuickAddTarget.Todolist(groceries),
            due = tomorrow,
        )
        val item = TasksDemoSeed.materialize(req)

        val bucketed = listOf(item).forUpcomingBucketed(today)
        assertEquals("should have exactly one bucket", 1, bucketed.size)
        assertEquals("bucket should be Tomorrow", UpcomingBucket.Tomorrow, bucketed[0].first)
        assertEquals("bucket should contain the task", item.id, bucketed[0].second[0].id)
    }

    @Test fun `no-due quick-add task does NOT appear in upcoming`() {
        val req = TaskQuickAddRequest(
            title = "Standing chore",
            target = QuickAddTarget.Todolist(groceries),
            due = null,
        )
        val item = TasksDemoSeed.materialize(req)

        val bucketed = listOf(item).forUpcomingBucketed(today)
        assertEquals("no-due task should not appear in Upcoming", 0, bucketed.size)
    }

    // ─── Event targets still use implicit due from target type ───────────────

    @Test fun `TodayEvent target produces item due today`() {
        val req = TaskQuickAddRequest(
            title = "Today event",
            target = QuickAddTarget.TodayEvent,
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("TodayEvent should produce today-due task", today, item.due)
    }

    @Test fun `TomorrowEvent target produces item due tomorrow`() {
        val req = TaskQuickAddRequest(
            title = "Tomorrow event",
            target = QuickAddTarget.TomorrowEvent,
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("TomorrowEvent should produce tomorrow-due task", tomorrow, item.due)
    }
}
