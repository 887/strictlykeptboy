package com.eight87.strictlykeptboy.composition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.cache.entities.StandingTaskRow
import com.eight87.strictlykeptboy.cache.entities.TaskRow
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.1.A.3 — unit coverage for the indexer→snapshot bridge with
 * two seeded repos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IndexerSnapshotPublisherTest {

    private lateinit var ctx: Context
    private lateinit var db: CacheDatabase
    private lateinit var repoStore: RepoStore

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = CacheDatabase.openInMemoryWithDriver(
            ctx,
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
        val prefs = ctx.getSharedPreferences("snap_pub_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        repoStore = RepoStore.openForTest(prefs)
    }

    @After fun tearDown() { db.close() }

    private fun cfg(id: String) = RepoConfig(
        repoId = id,
        displayName = "repo $id",
        rootDir = "/tmp/$id",
        authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
    )

    private fun event(repoId: String, id: String, calendarId: String) = EventRow(
        repoId = repoId, id = id, calendarId = calendarId,
        startEpochMs = 1_700_000_000_000L, endEpochMs = 1_700_003_600_000L,
        allDay = false, title = id, body = "", tagsJson = "[]",
        location = null, emoji = null, busy = true, priorityOverride = null,
        externalUid = null, privateFlag = false, sourcePath = "calendars/$calendarId/events/$id.md",
    )

    private fun task(repoId: String, id: String, todolistId: String) = TaskRow(
        repoId = repoId, id = id, todolistId = todolistId,
        title = id, body = "", dueEpochMs = null, done = false, doneAtEpochMs = null,
        priority = null, tagsJson = "[]",
        sourcePath = "todolists/$todolistId/tasks/$id.md",
    )

    private fun standing(repoId: String, id: String, todolistId: String) = StandingTaskRow(
        repoId = repoId, id = id, todolistId = todolistId,
        title = id, body = "", done = false, pinned = false, priority = null,
        tagsJson = "[]", sourcePath = "todolists/$todolistId/standing/$id.md",
    )

    @Test fun unionOfCalendarsAndTodolistsAcrossTwoRepos() = runBlocking {
        repoStore.add(cfg("repo-a"))
        repoStore.add(cfg("repo-b"))

        db.events().upsertAll(listOf(
            event("repo-a", "ev-a1", "cal-a"),
            event("repo-a", "ev-a2", "cal-shared"),
            event("repo-b", "ev-b1", "cal-b"),
            event("repo-b", "ev-b2", "cal-shared"),
        ))
        db.tasks().upsertAll(listOf(
            task("repo-a", "tk-a1", "list-a"),
            task("repo-b", "tk-b1", "list-b"),
        ))
        db.standingTasks().upsertAll(listOf(
            standing("repo-a", "st-a1", "list-a"),
            standing("repo-b", "st-b1", "list-shared"),
        ))

        val pubScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val publisher = IndexerSnapshotPublisher(
            db = db, repoStore = repoStore, scope = pubScope,
        )

        // Wait for the first non-empty snapshot.
        val snap = withTimeout(5_000) {
            publisher.state.first { it.calendars.isNotEmpty() }
        }

        val repoIds = snap.repos.map { it.ref.id }.toSet()
        assertEquals(setOf("repo-a", "repo-b"), repoIds)

        val calIds = snap.calendars.map { it.ref.id }.toSet()
        assertEquals(setOf("cal-a", "cal-b", "cal-shared"), calIds)

        // The shared id appears once per repo (different RepoRef).
        val sharedCals = snap.calendars.filter { it.ref.id == "cal-shared" }
        assertEquals(setOf("repo-a", "repo-b"), sharedCals.map { it.repo.id }.toSet())

        val todoIds = snap.todolists.map { it.ref.id }.toSet()
        assertEquals(setOf("list-a", "list-b", "list-shared"), todoIds)

        assertTrue(snap.contentHash.isNotBlank())
        pubScope.cancel()
    }

    @Test fun emptyRepoSetEmitsEmptySnapshot() = runBlocking {
        val pubScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val publisher = IndexerSnapshotPublisher(
            db = db, repoStore = repoStore, scope = pubScope,
        )
        val snap = withTimeout(2_000) { publisher.state.first() }
        assertEquals(0, snap.repos.size)
        assertEquals(0, snap.calendars.size)
        assertEquals(0, snap.todolists.size)
        pubScope.cancel()
    }
}
