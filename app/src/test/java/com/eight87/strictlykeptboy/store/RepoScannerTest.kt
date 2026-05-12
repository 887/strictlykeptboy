package com.eight87.strictlykeptboy.store

import com.eight87.strictlykeptboy.git.AuthorIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class RepoScannerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val header = EntityHeader(
        id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001",
        createdAt = "2026-05-09T18:30:00+02:00",
        updatedAt = "2026-05-09T18:30:00+02:00",
        author = "01900000-0000-7000-8000-aaaaaaaaaaaa",
    )

    @Test fun scanFindsManyEntityTypes() = runTest {
        val root = tmp.newFolder("repo")
        val boot = RepoBootstrap.scaffold(
            rootDir = root,
            spec = RepoBootstrap.Spec(
                repoName = "demo",
                tzId = "Europe/Berlin",
                identity = AuthorIdentity("Alex", "alex@example.com"),
                seedCalendars = listOf("Personal"),
                seedTodolists = listOf("Chores"),
            ),
        )
        val calId = boot.calendarIds.values.first()
        val listId = boot.todolistIds.values.first()

        // Write a spread of entities.
        val entities = listOf(
            Event(header.copy(id = "ev-${System.nanoTime()}-1"), title = "A",
                start = "2026-03-15T10:00:00+02:00", end = "2026-03-15T11:00:00+02:00",
                calendarId = calId),
            Event(header.copy(id = "ev-${System.nanoTime()}-2"), title = "B",
                start = "2026-04-01T09:00:00+02:00", end = "2026-04-01T10:00:00+02:00",
                calendarId = calId),
            RecurrenceRule(header.copy(id = "rec-${System.nanoTime()}"), title = "Standup",
                dtstart = "2026-01-05T09:30:00", duration = "PT15M", tzId = "Europe/Berlin",
                rrule = "FREQ=WEEKLY;BYDAY=MO", calendarId = calId),
            Task(header.copy(id = "tk-${System.nanoTime()}"), title = "Milk",
                todolistId = listId, due = "2026-05-09T18:30:00+02:00"),
            StandingTask(header.copy(id = "st-${System.nanoTime()}"), title = "Sharpen",
                todolistId = listId),
            Exception(header.copy(id = "ex-${System.nanoTime()}"), ruleId = "rule-X",
                instanceDate = "2026-05-11", mode = "cancel", calendarId = calId),
        )
        EntityWriter.writeBatch(root, entities)

        // Drop a malformed file into the events bucket.
        val bad = root.toPath().resolve("calendars/$calId/events/2026/03/broken.md")
        Files.write(bad, "not even a fence".toByteArray(StandardCharsets.UTF_8))

        val results = RepoScanner.scanAll(root)
        val successes = results.filterIsInstance<ParseResult.Success>()
        val failures = results.filterIsInstance<ParseResult.Failed>()
        assertTrue("found at least the 6 entities + meta", successes.size >= 6)
        assertEquals(1, failures.size)
        assertTrue(failures.first().sourcePath.toString().endsWith("broken.md"))

        // Make sure each type was decoded.
        val byKind = successes.groupBy { it.entity.kind }
        assertTrue(byKind[EntityKind.Event] != null)
        assertTrue(byKind[EntityKind.Task] != null)
        assertTrue(byKind[EntityKind.StandingTask] != null)
        assertTrue(byKind[EntityKind.Recurrence] != null)
        assertTrue(byKind[EntityKind.Exception] != null)
        assertTrue(byKind[EntityKind.Identity] != null)
    }

    @Test fun scanSkipsHiddenAndAttachments() = runTest {
        val root = tmp.newFolder("repo2")
        RepoBootstrap.scaffold(
            rootDir = root,
            spec = RepoBootstrap.Spec(
                repoName = "demo",
                tzId = "Europe/Berlin",
                identity = AuthorIdentity("Alex", "alex@example.com"),
            ),
        )
        // Drop a .git/HEAD that should be ignored.
        val gitDir = File(root, ".git")
        gitDir.mkdirs()
        File(gitDir, "HEAD").writeText("ref: refs/heads/main\n")
        // attachments/ entries should be ignored.
        val attach = File(root, "attachments/aa")
        attach.mkdirs()
        File(attach, "blob.png").writeText("PNGfake")

        val results = RepoScanner.scanAll(root)
        assertTrue(results.none { it.sourcePath.toString().contains(".git/") })
        assertTrue(results.none { it.sourcePath.toString().contains("attachments/") })
    }
}
