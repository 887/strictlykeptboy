package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.LocalDate

/**
 * Round 2.22 / Phase B — pure-math + entity-transform tests for the
 * drag-to-reschedule flow. Covers D-2.22.b semantics:
 *   - snap-to-grid (15 minute default + custom grids)
 *   - single-instance move preserves duration
 *   - commit-message format
 *   - recurring "this instance only" → Exception(mode = "move")
 *   - recurring "this and future" → split: capped UNTIL + fresh rule
 *   - recurring "entire series" → rewrite dtstart
 *
 * No Compose; no IO. Failing here breaks the drop-handler regardless
 * of gesture wiring.
 */
class DragRescheduleTest {

    private val nowIso = "2026-05-16T12:00:00Z"
    private val author = "tester"

    private fun event(start: String, end: String, title: String = "Coffee"): Event = Event(
        header = EntityHeader(
            id = "01900000-0000-7000-8000-000000000001",
            schemaVersion = 1,
            createdAt = nowIso,
            updatedAt = nowIso,
            author = author,
        ),
        title = title,
        start = start,
        end = end,
        calendarId = "personal",
    )

    private fun rule(
        rrule: String = "FREQ=WEEKLY;BYDAY=MO",
        dtstart: String = "2026-05-04T09:00:00",
        duration: String = "PT30M",
    ): RecurrenceRule = RecurrenceRule(
        header = EntityHeader(
            id = "01900000-0000-7000-8000-000000000010",
            schemaVersion = 1,
            createdAt = nowIso,
            updatedAt = nowIso,
            author = author,
        ),
        title = "Standup",
        dtstart = dtstart,
        duration = duration,
        tzId = "Europe/Berlin",
        rrule = rrule,
        calendarId = "work",
    )

    // ---- snapToGrid ------------------------------------------------------

    @Test fun `snapToGrid rounds to nearest 15 minutes by default`() {
        val input = OffsetDateTime.parse("2026-05-16T10:07:00Z")
        val out = DragReschedule.snapToGrid(input)
        assertEquals(OffsetDateTime.parse("2026-05-16T10:00:00Z"), out)
    }

    @Test fun `snapToGrid rounds up past midpoint`() {
        val input = OffsetDateTime.parse("2026-05-16T10:08:00Z")
        val out = DragReschedule.snapToGrid(input)
        assertEquals(OffsetDateTime.parse("2026-05-16T10:15:00Z"), out)
    }

    @Test fun `snapToGrid honors custom grid minutes`() {
        val input = OffsetDateTime.parse("2026-05-16T10:23:00Z")
        val out = DragReschedule.snapToGrid(input, gridMinutes = 30)
        assertEquals(OffsetDateTime.parse("2026-05-16T10:30:00Z"), out)
    }

    @Test fun `snapToGrid rejects non-positive grid`() {
        val input = OffsetDateTime.parse("2026-05-16T10:00:00Z")
        try {
            DragReschedule.snapToGrid(input, gridMinutes = 0)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("gridMinutes") == true)
        }
    }

    // ---- moveSingleEvent -------------------------------------------------

    @Test fun `moveSingleEvent preserves duration`() {
        val e = event(start = "2026-05-16T09:00:00Z", end = "2026-05-16T09:45:00Z")
        val moved = DragReschedule.moveSingleEvent(
            e, OffsetDateTime.parse("2026-05-16T14:00:00Z"), nowIso,
        )
        assertEquals("2026-05-16T14:00Z", moved.start)
        assertEquals("2026-05-16T14:45Z", moved.end)
    }

    @Test fun `moveSingleEvent bumps updatedAt`() {
        val e = event(start = "2026-05-16T09:00:00Z", end = "2026-05-16T09:45:00Z")
            .let { it.copy(header = it.header.copy(updatedAt = "2020-01-01T00:00:00Z")) }
        val moved = DragReschedule.moveSingleEvent(
            e, OffsetDateTime.parse("2026-05-16T14:00:00Z"), nowIso,
        )
        assertEquals(nowIso, moved.header.updatedAt)
    }

    // ---- commitMessageFor ------------------------------------------------

    @Test fun `commit message format matches D-2-22-b`() {
        val msg = DragReschedule.commitMessageFor(
            title = "Coffee",
            oldStart = "2026-05-16T09:00:00Z",
            newStart = "2026-05-16T14:00:00Z",
        )
        assertEquals(
            "move event \"Coffee\" from 2026-05-16T09:00:00Z to 2026-05-16T14:00:00Z",
            msg,
        )
    }

    @Test fun `commit message replaces double quotes in title`() {
        val msg = DragReschedule.commitMessageFor(
            title = "Coffee with \"Sam\"",
            oldStart = "2026-05-16T09:00:00Z",
            newStart = "2026-05-16T14:00:00Z",
        )
        assertTrue(!msg.contains("\"Sam\""))
        assertTrue(msg.contains("'Sam'"))
    }

    // ---- moveRecurringInstance ------------------------------------------

    @Test fun `recurring this-instance-only yields Exception mode=move`() {
        val r = rule(duration = "PT30M")
        val ex = DragReschedule.moveRecurringInstance(
            rule = r,
            originalDate = LocalDate.parse("2026-05-18"),
            newStart = OffsetDateTime.parse("2026-05-18T11:00:00Z"),
            author = author,
            nowIso = nowIso,
        )
        assertEquals("move", ex.mode)
        assertEquals(r.id, ex.ruleId)
        assertEquals("2026-05-18", ex.instanceDate)
        assertEquals("2026-05-18T11:00Z", ex.overrideStart)
        assertEquals("2026-05-18T11:30Z", ex.overrideEnd)
        assertEquals(r.calendarId, ex.calendarId)
    }

    // ---- rewriteRuleDtstart ---------------------------------------------

    @Test fun `entire-series move rewrites dtstart and preserves rrule`() {
        val r = rule(rrule = "FREQ=WEEKLY;BYDAY=MO", dtstart = "2026-05-04T09:00:00")
        val out = DragReschedule.rewriteRuleDtstart(
            r, OffsetDateTime.parse("2026-05-18T11:00:00Z"), nowIso,
        )
        assertEquals("2026-05-18T11:00:00", out.dtstart)
        assertEquals(r.rrule, out.rrule)
        assertEquals(r.duration, out.duration)
        assertEquals(r.tzId, out.tzId)
        assertEquals(r.id, out.id) // same rule, only dtstart changed
        assertEquals(nowIso, out.header.updatedAt)
    }

    // ---- splitRecurringRule ---------------------------------------------

    @Test fun `this-and-future caps original with UNTIL and rules a fresh id`() {
        val r = rule(rrule = "FREQ=WEEKLY;BYDAY=MO", dtstart = "2026-05-04T09:00:00")
        val (capped, fresh) = DragReschedule.splitRecurringRule(
            rule = r,
            newStart = OffsetDateTime.parse("2026-05-18T11:00:00Z"),
            author = author,
            nowIso = nowIso,
        )
        // Capped retains the same id, gets UNTIL added in UTC, day-before-drop.
        assertEquals(r.id, capped.id)
        assertTrue(
            "expected UNTIL clause in $capped.rrule",
            capped.rrule.contains("UNTIL=20260517T235959Z"),
        )
        // Fresh rule is brand new (different id), starts at the drop instant.
        assertNotEquals(r.id, fresh.id)
        assertEquals("2026-05-18T11:00:00", fresh.dtstart)
        assertTrue(
            "fresh rule's rrule should not carry UNTIL: ${fresh.rrule}",
            !fresh.rrule.contains("UNTIL=", ignoreCase = true),
        )
        // Both rules preserve duration, tz, calendar.
        assertEquals(r.duration, fresh.duration)
        assertEquals(r.tzId, fresh.tzId)
        assertEquals(r.calendarId, fresh.calendarId)
    }

    @Test fun `split replaces existing UNTIL rather than appending duplicate`() {
        val r = rule(rrule = "FREQ=WEEKLY;BYDAY=MO;UNTIL=20271231T235959Z")
        val (capped, _) = DragReschedule.splitRecurringRule(
            rule = r,
            newStart = OffsetDateTime.parse("2026-05-18T11:00:00Z"),
            author = author,
            nowIso = nowIso,
        )
        val untilCount = Regex("UNTIL=", RegexOption.IGNORE_CASE).findAll(capped.rrule).count()
        assertEquals(1, untilCount)
        assertTrue(capped.rrule.contains("UNTIL=20260517T235959Z"))
    }
}
