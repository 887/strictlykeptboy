package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.cache.EventCommit
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Phase 2.2.E.1 — incremental reminder arming.
 *
 * The indexer's [com.eight87.strictlykeptboy.cache.Indexer.commits] flow
 * pulses after each rescan. [EventReminderArming.armForCommit] re-reads
 * the frontmatter, maps reminders, and hands a [EventReminderScheduler.ReminderInput]
 * to the caller-supplied `arm` lambda — exercised here with a fake so
 * the test stays JVM-only (no Android, no AlarmManager).
 */
class IncrementalReminderArmTest {

    private fun event(reminderOffset: String? = "-PT30M", id: String = "ev1"): Event {
        val now = OffsetDateTime.now(ZoneOffset.UTC).toString()
        return Event(
            header = EntityHeader(id = id, createdAt = now, updatedAt = now, author = "me"),
            title = "Dentist",
            start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).toString(),
            end = OffsetDateTime.now(ZoneOffset.UTC).plusHours(3).toString(),
            calendarId = "cal-a",
        )
    }

    /** Build a frontmatter with one `[[reminder]]` entry at [offset]. */
    private fun frontmatterWithReminder(offset: String): TomlTable {
        val t = TomlTable()
        val entry = TomlTable().apply {
            scalars["offset"] = TomlValue.Str(offset)
            scalars["kind"] = TomlValue.Str("pre_event")
        }
        t.aotables["reminder"] = mutableListOf(entry)
        return t
    }

    @Test fun foregroundCommitArmsAlarmForFrontmatterReminder() {
        val captured = mutableListOf<EventReminderScheduler.ReminderInput>()
        val commit = EventCommit(
            repoId = "repo-a",
            event = event(),
            sourcePath = Paths.get("/fake/events/2026/05/ev1.md"),
        )
        val armed = EventReminderArming.armForCommit(
            commit = commit,
            arm = { captured += it; listOf("alarm-1") },
            readFrontmatter = { frontmatterWithReminder("-PT30M") },
        )
        assertEquals(listOf("alarm-1"), armed)
        assertEquals(1, captured.size)
        assertEquals("repo-a", captured[0].repoId)
        assertEquals("ev1", captured[0].eventId)
        assertEquals(listOf("30m"), captured[0].leadTimes)
    }

    @Test fun eventWithoutRemindersDoesNotArm() {
        var armCalled = false
        val commit = EventCommit(
            repoId = "repo-a",
            event = event(),
            sourcePath = Paths.get("/fake/events/2026/05/ev1.md"),
        )
        val armed = EventReminderArming.armForCommit(
            commit = commit,
            arm = { armCalled = true; emptyList() },
            readFrontmatter = { TomlTable() },
        )
        assertTrue(armed.isEmpty())
        assertTrue("arm lambda must not be invoked when no leads", !armCalled)
    }

    @Test fun missingFrontmatterFileSilentlyNoOps() {
        var armCalled = false
        val commit = EventCommit(
            repoId = "repo-a",
            event = event(),
            sourcePath = Paths.get("/does/not/exist.md"),
        )
        val armed = EventReminderArming.armForCommit(
            commit = commit,
            arm = { armCalled = true; emptyList() },
            readFrontmatter = { null },
        )
        assertTrue(armed.isEmpty())
        assertTrue(!armCalled)
    }

    @Test fun privateFlagThreadsThroughInput() {
        val captured = mutableListOf<EventReminderScheduler.ReminderInput>()
        val priv = event().copy(private = true)
        val commit = EventCommit(
            repoId = "repo-a",
            event = priv,
            sourcePath = Paths.get("/fake/events/2026/05/ev1.md"),
        )
        EventReminderArming.armForCommit(
            commit = commit,
            arm = { captured += it; listOf("alarm-1") },
            readFrontmatter = { frontmatterWithReminder("-PT15M") },
        )
        assertEquals(1, captured.size)
        assertEquals(true, captured[0].privateEvent)
    }
}
