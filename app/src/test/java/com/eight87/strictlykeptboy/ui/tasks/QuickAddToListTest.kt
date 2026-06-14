package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D-23 — unit tests verifying that a [TaskQuickAddRequest] targeting a
 * [QuickAddTarget.Todolist] materializes into a [TaskItem] on that list.
 *
 * Tests the [TasksDemoSeed.materialize] path (the same code wired in
 * MainActivity's [ShellCallbacks.onWriteTask]) to ensure:
 *  1. Todolist target → task appears on the correct list with no due date.
 *  2. TodayEvent target → task uses errands fallback + due = today.
 *  3. TomorrowEvent target → task uses errands fallback + due = tomorrow.
 */
class QuickAddToListTest {

    private val groceries = TasksDemoSeed.groceries
    private val errands = TasksDemoSeed.errands

    @Test fun todolist_target_creates_task_on_that_list() {
        val req = TaskQuickAddRequest(
            title = "pick up parcel",
            target = QuickAddTarget.Todolist(groceries),
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("pick up parcel", item.title)
        assertEquals(groceries.id, item.todolist.id)
        assertNull("todolist task should have no due date", item.due)
        assertEquals(false, item.done)
    }

    @Test fun today_event_target_creates_task_due_today() {
        val req = TaskQuickAddRequest(
            title = "call dentist",
            target = QuickAddTarget.TodayEvent,
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("call dentist", item.title)
        // Falls back to errands list for event targets.
        assertEquals(errands.id, item.todolist.id)
        assertEquals(java.time.LocalDate.now(), item.due)
    }

    @Test fun tomorrow_event_target_creates_task_due_tomorrow() {
        val req = TaskQuickAddRequest(
            title = "return library books",
            target = QuickAddTarget.TomorrowEvent,
        )
        val item = TasksDemoSeed.materialize(req)
        assertEquals("return library books", item.title)
        assertEquals(errands.id, item.todolist.id)
        assertEquals(java.time.LocalDate.now().plusDays(1), item.due)
    }

    @Test fun quick_add_task_id_is_prefixed_with_user() {
        val req = TaskQuickAddRequest("task", QuickAddTarget.Todolist(groceries))
        val item = TasksDemoSeed.materialize(req)
        // IDs are "user-<millis>" — verify they carry the user prefix.
        assert(item.id.startsWith("user-")) { "Expected id to start with 'user-' but was '${item.id}'" }
    }
}
