package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.RecurrenceRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Round 2.22 / Phase B UI follow-up — write-side verification of
 * `DragRescheduleController` against a tmp filesystem. No Compose,
 * no JGit (GitRepoRegistry returns null for unknown repoId so the
 * commit step is a no-op — that's intentional; the math + write side
 * is what this test pins).
 */
class DragRescheduleControllerTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

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

    private fun rule(): RecurrenceRule = RecurrenceRule(
        header = EntityHeader(
            id = "01900000-0000-7000-8000-00000000aaaa",
            schemaVersion = 1,
            createdAt = nowIso,
            updatedAt = nowIso,
            author = author,
        ),
        title = "Standup",
        dtstart = "2026-05-04T09:00:00",
        duration = "PT30M",
        tzId = "Europe/Berlin",
        rrule = "FREQ=WEEKLY;BYDAY=MO",
        calendarId = "work",
    )

    @Test
    fun `handleSingleDrop rewrites event file with new start preserving duration`() = runBlocking {
        val root = tmp.newFolder()
        val originalStart = "2026-05-16T10:00:00Z"
        val originalEnd = "2026-05-16T10:30:00Z"
        val evt = event(originalStart, originalEnd)
        // Seed the event file at canonical path.
        val rel = EntityWriter.write(root, evt)
        val seeded = root.toPath().resolve(rel).toFile()
        assertTrue("seeded event file must exist", seeded.exists())

        val newStart = OffsetDateTime.parse("2026-05-16T11:00:00Z")
        val moved = DragRescheduleController.handleSingleDrop(
            rootDir = root,
            repoId = "no-such-repo",
            event = evt,
            newStart = newStart,
            nowIso = "2026-05-16T13:00:00Z",
        )
        assertNotNull("moved event returned", moved)
        // The file at canonical path should be rewritten.
        val movedFile = root.toPath()
            .resolve(EntityWriter.pathFor(moved!!))
            .toFile()
        assertTrue("rewritten event file must exist", movedFile.exists())
        val text = movedFile.readText()
        assertTrue("file contains the new start", text.contains("2026-05-16T11:00"))
        assertTrue("file contains the duration-preserved end", text.contains("2026-05-16T11:30"))
    }

    @Test
    fun `handleSingleDrop returns null when newStart equals oldStart`() = runBlocking {
        val root = tmp.newFolder()
        // Use the canonical OffsetDateTime#toString form so the no-op
        // check (string equality on start) trips.
        val canonical = OffsetDateTime.parse("2026-05-16T10:00:00Z").toString()
        val evt = event(canonical, OffsetDateTime.parse("2026-05-16T10:30:00Z").toString())
        val same = OffsetDateTime.parse(canonical)
        val moved = DragRescheduleController.handleSingleDrop(
            rootDir = root,
            repoId = "no-such-repo",
            event = evt,
            newStart = same,
            nowIso = nowIso,
        )
        assertNull("no-op drop returns null", moved)
    }

    @Test
    fun `handleRecurringDrop THIS_ONE writes an exceptions file`() = runBlocking {
        val root = tmp.newFolder()
        val r = rule()
        val newStart = OffsetDateTime.parse("2026-05-18T10:00:00Z")
        val msg = DragRescheduleController.handleRecurringDrop(
            rootDir = root,
            repoId = "no-such-repo",
            rule = r,
            originalDate = LocalDate.of(2026, 5, 18),
            newStart = newStart,
            choice = DragRescheduleController.RecurringChoice.THIS_ONE,
            author = author,
            nowIso = nowIso,
        )
        // commitMessageFor format anchor.
        assertTrue(msg.startsWith("move event \"Standup\" from "))
        // Exception file lands under calendars/work/exceptions/<rule-id>/<date>.md
        val exDir = java.io.File(root, "calendars/work/exceptions/${r.id}")
        assertTrue("exceptions dir created", exDir.isDirectory)
        val files = exDir.listFiles()?.toList().orEmpty()
        assertFalse("at least one exception file written", files.isEmpty())
        val f = files.first { it.name.endsWith(".md") }
        val text = f.readText()
        assertTrue("exception is mode=move", text.contains("mode") && text.contains("move"))
    }

    @Test
    fun `handleRecurringDrop WHOLE_SERIES rewrites the rule file`() = runBlocking {
        val root = tmp.newFolder()
        val r = rule()
        // Seed the rule file.
        EntityWriter.write(root, r)
        val newStart = OffsetDateTime.parse("2026-05-18T08:00:00Z")
        DragRescheduleController.handleRecurringDrop(
            rootDir = root,
            repoId = "no-such-repo",
            rule = r,
            originalDate = LocalDate.of(2026, 5, 18),
            newStart = newStart,
            choice = DragRescheduleController.RecurringChoice.WHOLE_SERIES,
            author = author,
            nowIso = nowIso,
        )
        val ruleFile = root.toPath().resolve(EntityWriter.pathFor(r)).toFile()
        assertTrue(ruleFile.exists())
        val text = ruleFile.readText()
        // dtstart rewritten to the new instant's local datetime.
        assertTrue("dtstart updated", text.contains("2026-05-18T08:00"))
    }

    @Test
    fun `handleRecurringDrop THIS_AND_FUTURE caps original and writes a fresh rule`() = runBlocking {
        val root = tmp.newFolder()
        val r = rule()
        EntityWriter.write(root, r)
        val newStart = OffsetDateTime.parse("2026-05-25T09:00:00Z")
        DragRescheduleController.handleRecurringDrop(
            rootDir = root,
            repoId = "no-such-repo",
            rule = r,
            originalDate = LocalDate.of(2026, 5, 25),
            newStart = newStart,
            choice = DragRescheduleController.RecurringChoice.THIS_AND_FUTURE,
            author = author,
            nowIso = nowIso,
        )
        val cappedFile = root.toPath().resolve(EntityWriter.pathFor(r)).toFile()
        val cappedText = cappedFile.readText()
        assertTrue("capped rule has UNTIL", cappedText.contains("UNTIL="))
        // Count recurrences/*.md files — should be 2 now (capped + fresh).
        val recDir = java.io.File(root, "calendars/work/recurrences")
        val rules = recDir.listFiles()?.filter { it.name.endsWith(".md") }.orEmpty()
        assertTrue("two rule files present", rules.size >= 2)
    }
}
