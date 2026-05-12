package com.eight87.strictlykeptboy.cache

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.cache.entities.TaskRow
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception as StoreException
import com.eight87.strictlykeptboy.store.JournalEntry
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.RepoBootstrap
import com.eight87.strictlykeptboy.store.StandingTask
import com.eight87.strictlykeptboy.store.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IndexerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: CacheDatabase
    private lateinit var indexer: Indexer

    private val baseHeader = EntityHeader(
        id = "h-0",
        createdAt = "2026-05-01T10:00:00+00:00",
        updatedAt = "2026-05-01T10:00:00+00:00",
        author = "author-x",
    )

    @Before fun setUp() {
        db = CacheDatabase.openInMemoryWithDriver(
            ApplicationProvider.getApplicationContext(),
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
        indexer = Indexer(db)
    }

    @After fun tearDown() { db.close() }

    private suspend fun scaffoldRepo(label: String): Triple<File, String, String> {
        val root = tmp.newFolder(label)
        val boot = RepoBootstrap.scaffold(
            rootDir = root,
            spec = RepoBootstrap.Spec(
                repoName = label,
                tzId = "Europe/Berlin",
                identity = AuthorIdentity("Bat", "bat@example.com"),
                seedCalendars = listOf("Personal"),
                seedTodolists = listOf("Chores"),
            ),
        )
        return Triple(root, boot.calendarIds.values.first(), boot.todolistIds.values.first())
    }

    private suspend fun openGit(root: File, repoId: String): GitRepo {
        org.eclipse.jgit.api.Git.init().setDirectory(root).setInitialBranch("main").call().close()
        val repo = GitRepo.open(
            rootDir = root,
            repoId = repoId,
            remotes = emptyList(),
            primaryRemote = null,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )
        repo.commitAll("initial")
        return repo
    }

    @Test fun fullScanInsertsRowsForEveryEntityType() = runTest {
        val (root, calId, listId) = scaffoldRepo("a")
        val git = openGit(root, "repo-a")
        val entities = listOf(
            Event(baseHeader.copy(id = "ev1"), title = "Run", calendarId = calId,
                start = "2026-06-01T08:00:00+00:00", end = "2026-06-01T09:00:00+00:00",
                body = "morning run notes"),
            Event(baseHeader.copy(id = "ev2"), title = "Meeting", calendarId = calId,
                start = "2026-06-02T15:00:00+00:00", end = "2026-06-02T16:00:00+00:00"),
            Task(baseHeader.copy(id = "tk1"), title = "Bread", todolistId = listId,
                due = "2026-06-01T18:00:00+00:00"),
            StandingTask(baseHeader.copy(id = "st1"), title = "Sharpen knives",
                todolistId = listId),
            RecurrenceRule(baseHeader.copy(id = "rr1"), title = "Standup",
                dtstart = "2026-01-05T09:30:00", duration = "PT15M", tzId = "Europe/Berlin",
                rrule = "FREQ=WEEKLY;BYDAY=MO", calendarId = calId),
            StoreException(baseHeader.copy(id = "ex1"), ruleId = "rr1",
                instanceDate = "2026-05-11", mode = "cancel", calendarId = calId),
            JournalEntry(baseHeader.copy(id = "j1"), day = "2026-04-09", body = "thoughts"),
        )
        EntityWriter.writeBatch(root, entities)
        git.commitAll("seed entities")

        val stats = indexer.fullScan("repo-a", git)
        assertTrue("touched ${stats.touched}", stats.touched >= 7)

        val events = db.events().byCalendar("repo-a", calId).first()
        assertEquals(2, events.size)
        val tasks = db.tasks().byTodolist("repo-a", listId, includeDone = true).first()
        assertEquals(1, tasks.size)
        val standing = db.standingTasks().byTodolist("repo-a", listId, includeDone = true).first()
        assertEquals(1, standing.size)
        val rules = db.recurrenceRules().byCalendar("repo-a", calId).first()
        assertEquals(1, rules.size)
        val exceptions = db.exceptions().byRule("repo-a", "rr1").first()
        assertEquals(1, exceptions.size)
        val state = db.repoState().get("repo-a")
        assertNotNull(state)
        assertNotNull(state!!.lastIndexedHeadSha)
        assertEquals(Indexer.CURRENT_SCHEMA_VERSION, state.schemaVersion)
    }

    @Test fun byDateRangeFiltersBoundaries() = runTest {
        val (root, calId, _) = scaffoldRepo("b")
        val git = openGit(root, "repo-b")
        val entities = listOf(
            Event(baseHeader.copy(id = "ev-jan"), title = "January", calendarId = calId,
                start = "2026-01-15T08:00:00+00:00", end = "2026-01-15T09:00:00+00:00"),
            Event(baseHeader.copy(id = "ev-jun"), title = "June", calendarId = calId,
                start = "2026-06-15T08:00:00+00:00", end = "2026-06-15T09:00:00+00:00"),
            Event(baseHeader.copy(id = "ev-dec"), title = "December", calendarId = calId,
                start = "2026-12-15T08:00:00+00:00", end = "2026-12-15T09:00:00+00:00"),
        )
        EntityWriter.writeBatch(root, entities)
        git.commitAll("seed")
        indexer.fullScan("repo-b", git)

        val mayToAug = db.events().byDateRange(
            "repo-b",
            EntityMapping.parseEpochMs("2026-05-01T00:00:00+00:00"),
            EntityMapping.parseEpochMs("2026-08-01T00:00:00+00:00"),
        ).first()
        assertEquals(listOf("ev-jun"), mayToAug.map(EventRow::id))
    }

    @Test fun incrementalScanTouchesOnlyChangedFiles() = runTest {
        val (root, calId, _) = scaffoldRepo("c")
        val git = openGit(root, "repo-c")
        val initial = (1..5).map { i ->
            Event(baseHeader.copy(id = "ev$i"), title = "T$i", calendarId = calId,
                start = "2026-03-0${i}T10:00:00+00:00", end = "2026-03-0${i}T11:00:00+00:00",
                body = "body$i")
        }
        EntityWriter.writeBatch(root, initial)
        git.commitAll("seed")
        indexer.fullScan("repo-c", git)
        val all = db.events().listAll("repo-c")
        assertEquals(5, all.size)
        val originalTitleEv1 = all.first { it.id == "ev1" }.title

        // Modify ev1 + ev2; leave the rest alone.
        EntityWriter.write(root, initial[0].copy(title = "T1-modified"))
        EntityWriter.write(root, initial[1].copy(title = "T2-modified"))
        git.commitAll("modify 2 events")

        val stats = indexer.incrementalScan("repo-c", git)
        assertEquals(2, stats.touched)
        val refreshed = db.events().listAll("repo-c").associateBy { it.id }
        assertEquals("T1-modified", refreshed.getValue("ev1").title)
        assertEquals("T2-modified", refreshed.getValue("ev2").title)
        assertEquals("T3", refreshed.getValue("ev3").title)
        assertTrue(originalTitleEv1 != refreshed.getValue("ev1").title)
    }

    @Test fun incrementalScanDeletesRowForRemovedFile() = runTest {
        val (root, calId, _) = scaffoldRepo("d")
        val git = openGit(root, "repo-d")
        val entities = listOf(
            Event(baseHeader.copy(id = "evA"), title = "A", calendarId = calId,
                start = "2026-03-01T10:00:00+00:00", end = "2026-03-01T11:00:00+00:00"),
            Event(baseHeader.copy(id = "evB"), title = "B", calendarId = calId,
                start = "2026-03-02T10:00:00+00:00", end = "2026-03-02T11:00:00+00:00"),
        )
        EntityWriter.writeBatch(root, entities)
        git.commitAll("seed")
        indexer.fullScan("repo-d", git)

        EntityWriter.delete(root, entities[0])
        git.commitAll("delete A")
        val stats = indexer.incrementalScan("repo-d", git)
        assertTrue(stats.deleted >= 1)
        val remaining = db.events().listAll("repo-d")
        assertEquals(listOf("evB"), remaining.map(EventRow::id))
    }

    @Test fun schemaMismatchTriggersRebuild() = runTest {
        val (root, calId, _) = scaffoldRepo("e")
        val git = openGit(root, "repo-e")
        EntityWriter.write(root, Event(baseHeader.copy(id = "ev-only"), title = "Only", calendarId = calId,
            start = "2026-03-01T10:00:00+00:00", end = "2026-03-01T11:00:00+00:00"))
        git.commitAll("seed")
        indexer.fullScan("repo-e", git)

        // Poison the schema version + insert a stale row that should be wiped.
        db.repoState().upsert(
            com.eight87.strictlykeptboy.cache.entities.RepoStateRow(
                repoId = "repo-e",
                lastIndexedHeadSha = git.headSha()?.name,
                schemaVersion = -999,
                updatedAtEpochMs = 0L,
            ),
        )

        indexer.incrementalScan("repo-e", git)
        val state = db.repoState().get("repo-e")!!
        assertEquals(Indexer.CURRENT_SCHEMA_VERSION, state.schemaVersion)
        assertEquals(1, db.events().listAll("repo-e").size)
    }

    @Test fun parseFailureLandsInIndexErrors() = runTest {
        val (root, calId, _) = scaffoldRepo("f")
        val git = openGit(root, "repo-f")
        // Drop a malformed entity file in the events bucket.
        val bad = root.toPath().resolve("calendars/$calId/events/2026/03/broken.md")
        java.nio.file.Files.createDirectories(bad.parent)
        java.nio.file.Files.write(bad, "no frontmatter\n".toByteArray())
        git.commitAll("malformed")

        indexer.fullScan("repo-f", git)
        val errors = db.indexErrors().listForRepo("repo-f")
        assertTrue("expected at least one parse error: $errors", errors.isNotEmpty())
        assertTrue(errors.any { it.sourcePath.endsWith("broken.md") })
    }

    @Test fun ftsSearchFindsBodyKeyword() = runTest {
        val (root, calId, _) = scaffoldRepo("g")
        val git = openGit(root, "repo-g")
        val entities = listOf(
            Event(baseHeader.copy(id = "ev-pizza"), title = "Lunch", calendarId = calId,
                start = "2026-03-01T12:00:00+00:00", end = "2026-03-01T13:00:00+00:00",
                body = "margherita pizza at the new place"),
            Event(baseHeader.copy(id = "ev-walk"), title = "Walk", calendarId = calId,
                start = "2026-03-02T12:00:00+00:00", end = "2026-03-02T13:00:00+00:00",
                body = "around the lake"),
            Event(baseHeader.copy(id = "ev-bike"), title = "Bike", calendarId = calId,
                start = "2026-03-03T12:00:00+00:00", end = "2026-03-03T13:00:00+00:00",
                body = "long ride"),
        )
        EntityWriter.writeBatch(root, entities)
        git.commitAll("seed")
        indexer.fullScan("repo-g", git)

        val hits = db.fts().searchEventIds("repo-g", "pizza")
        assertEquals(listOf("ev-pizza"), hits)
    }

    @Test fun taskRoundTripWithTags() = runTest {
        val (root, _, listId) = scaffoldRepo("h")
        val git = openGit(root, "repo-h")
        val t = Task(baseHeader.copy(id = "tk-tag"), title = "Tagged", todolistId = listId,
            due = "2026-04-01T10:00:00+00:00",
            tags = listOf("home", "errand"))
        EntityWriter.write(root, t)
        git.commitAll("seed")
        indexer.fullScan("repo-h", git)
        val rows = db.tasks().listAll("repo-h")
        assertEquals(1, rows.size)
        val row: TaskRow = rows.first()
        assertTrue(row.tagsJson.contains("home"))
        assertTrue(row.tagsJson.contains("errand"))
    }
}
