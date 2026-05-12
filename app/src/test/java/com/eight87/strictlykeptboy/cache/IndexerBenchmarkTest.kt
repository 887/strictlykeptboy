package com.eight87.strictlykeptboy.cache

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.RepoBootstrap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

/**
 * Phase D.6 — illustrative benchmark. Thresholds are deliberately
 * loose because Robolectric is slower than a real device; the on-device
 * targets are < 500ms cold full-scan and < 50ms warm query on the
 * 1000-entry corpus per the plan. This test asserts neither is
 * pathologically slow under Robolectric (≤ 30s scan / ≤ 1s query).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IndexerBenchmarkTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: CacheDatabase

    @Before fun setUp() {
        db = CacheDatabase.openInMemoryWithDriver(
            ApplicationProvider.getApplicationContext(),
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun thousandEntryFullScanCompletes() = runTest {
        val root = tmp.newFolder("bench")
        val boot = RepoBootstrap.scaffold(
            rootDir = root,
            spec = RepoBootstrap.Spec(
                repoName = "bench",
                tzId = "UTC",
                identity = AuthorIdentity("Bat", "bat@example.com"),
                seedCalendars = listOf("Bench"),
            ),
        )
        val calId = boot.calendarIds.values.first()
        val header = EntityHeader(
            id = "x",
            createdAt = "2026-01-01T00:00:00+00:00",
            updatedAt = "2026-01-01T00:00:00+00:00",
            author = "bench",
        )
        val n = 1000
        val entities = (0 until n).map { i ->
            val mm = String.format("%02d", (i % 12) + 1)
            val dd = String.format("%02d", (i % 28) + 1)
            val hh = String.format("%02d", i % 24)
            Event(header.copy(id = "ev-$i"), title = "Event $i", calendarId = calId,
                start = "2026-$mm-${dd}T$hh:00:00+00:00",
                end = "2026-$mm-${dd}T$hh:30:00+00:00",
                body = "body $i")
        }
        EntityWriter.writeBatch(root, entities)
        org.eclipse.jgit.api.Git.init().setDirectory(root).setInitialBranch("main").call().close()
        val git = GitRepo.open(
            rootDir = root,
            repoId = "bench",
            remotes = emptyList(),
            primaryRemote = null,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )
        git.commitAll("seed $n events")

        val indexer = Indexer(db)
        val coldStart = System.nanoTime()
        indexer.fullScan("bench", git)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000
        assertTrue("cold scan too slow: ${coldMs}ms", coldMs < 30_000)
        assertEquals(n, db.events().listAll("bench").size)

        val warmStart = System.nanoTime()
        db.events().byDateRange("bench",
            EntityMapping.parseEpochMs("2026-06-01T00:00:00+00:00"),
            EntityMapping.parseEpochMs("2026-06-30T00:00:00+00:00")).first()
        val warmMs = (System.nanoTime() - warmStart) / 1_000_000
        assertTrue("warm query too slow: ${warmMs}ms", warmMs < 1_000)
    }
}
