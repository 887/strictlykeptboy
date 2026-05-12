package com.eight87.strictlykeptboy.perf

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.cache.Indexer
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.RepoBootstrap
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase V.2 — sync small repo budget: < 2s end-to-end on-device for a
 * 50-entity repo. Robolectric overhead bloats numbers; we measure +
 * record, only fail on pathological slowness.
 *
 * End-to-end here = `Indexer.fullScan` + `GitRepo.fetch` + `pullRebase`
 * against a bare-file fixture (no network).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncSmallRepoBenchmarkTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: CacheDatabase

    @Before fun setUp() {
        db = CacheDatabase.openInMemoryWithDriver(
            ApplicationProvider.getApplicationContext(),
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
    }

    @After fun tearDown() { db.close() }

    private fun initBare(): File {
        val bare = tmp.newFolder("bare.git")
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        return bare
    }

    private fun bareBinding(bare: File) = RemoteBinding(
        name = RemoteName.ORIGIN,
        url = bare.toURI().toString(),
        transport = Transport.File,
        authMethod = AuthMethod.None,
    )

    @Test fun fiftyEntities_fullScanFetchPullRebase_completes() = runTest {
        val bare = initBare()
        val workDir = tmp.newFolder("work")
        val boot = RepoBootstrap.scaffold(
            rootDir = workDir,
            spec = RepoBootstrap.Spec(
                repoName = "perf",
                tzId = "UTC",
                identity = AuthorIdentity("Bat", "bat@example.com"),
                seedCalendars = listOf("Perf"),
            ),
        )
        val calId = boot.calendarIds.values.first()
        val header = EntityHeader(
            id = "x",
            createdAt = "2026-01-01T00:00:00+00:00",
            updatedAt = "2026-01-01T00:00:00+00:00",
            author = "perf",
        )
        val n = 50
        val entities = (0 until n).map { i ->
            val mm = String.format("%02d", (i % 12) + 1)
            val dd = String.format("%02d", (i % 28) + 1)
            val hh = String.format("%02d", i % 24)
            Event(
                header.copy(id = "ev-$i"), title = "Event $i", calendarId = calId,
                start = "2026-$mm-${dd}T$hh:00:00+00:00",
                end = "2026-$mm-${dd}T$hh:30:00+00:00",
                body = "body $i",
            )
        }
        EntityWriter.writeBatch(workDir, entities)
        val repo = GitRepo.init(
            rootDir = workDir,
            repoId = "perf",
            remotes = listOf(bareBinding(bare)),
            primaryRemote = RemoteName.ORIGIN,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )
        repo.commitAll("seed $n entities")
        repo.push()

        val indexer = Indexer(db)
        val start = System.nanoTime()
        indexer.fullScan("perf", repo)
        repo.fetch()
        repo.pullRebase()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        println("[perf V.2] sync small repo (50 entities, fullScan+fetch+pullRebase): ${elapsedMs}ms")
        assertEquals(n, db.events().listAll("perf").size)
        assertTrue("sync small repo too slow: ${elapsedMs}ms", elapsedMs < 30_000)
    }
}
