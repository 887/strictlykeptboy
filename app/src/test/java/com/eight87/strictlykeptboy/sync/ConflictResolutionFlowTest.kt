package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.PullResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ConflictResolutionFlowTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun keepMineResolvesConflictAndReturnsRebased() = runTest {
        val (consumer, _) = setupConflict()
        val pull = consumer.pullRebase()
        assertTrue("expected Conflicted, got $pull", pull is PullResult.Conflicted)
        val c = pull as PullResult.Conflicted
        val result = resolveAllAndContinue(consumer, c.handle, c.conflictedPaths, ConflictChoice.KeepMine)
        assertTrue(
            "expected Rebased or FastForwarded, got $result",
            result is PullResult.Rebased || result is PullResult.FastForwarded,
        )
        assertEquals("local line", File(consumer.rootDir, "a.md").readText().trim())
    }

    @Test fun keepTheirsAdoptsRemoteContent() = runTest {
        val (consumer, _) = setupConflict()
        val pull = consumer.pullRebase() as PullResult.Conflicted
        val result = resolveAllAndContinue(consumer, pull.handle, pull.conflictedPaths, ConflictChoice.KeepTheirs)
        assertTrue(
            "expected Rebased or FastForwarded, got $result",
            result is PullResult.Rebased || result is PullResult.FastForwarded,
        )
        assertEquals("remote line", File(consumer.rootDir, "a.md").readText().trim())
    }

    @Test fun abortRestoresClean() = runTest {
        val (consumer, _) = setupConflict()
        val pull = consumer.pullRebase() as PullResult.Conflicted
        val out = consumer.abortRebase(pull.handle)
        assertEquals(PullResult.UpToDate, out)
    }

    @Test fun readConflictedFileExtractsBothSides() = runTest {
        val (consumer, _) = setupConflict()
        val pull = consumer.pullRebase() as PullResult.Conflicted
        val parsed = readConflictedFile(consumer.rootDir, "a.md")
        assertTrue("ours should contain local-side text, got: ${parsed.oursContent}", "local line" in parsed.oursContent || "remote line" in parsed.oursContent)
        // The two sides should differ (real conflict).
        assertTrue(parsed.oursContent != parsed.theirsContent)
        consumer.abortRebase(pull.handle)
    }

    private suspend fun setupConflict(): Pair<GitRepo, File> {
        val bare = SyncTestFixtures.initBare(tmp.root)
        val seedDir = tmp.newFolder("seed")
        val seed = SyncTestFixtures.cloneFrom(bare, seedDir, "seed")
        File(seedDir, "a.md").writeText("base\n")
        seed.commitAll("base")
        seed.push()

        // Both producer + consumer must be cloned from the same base commit so
        // their later edits create true divergence.
        val consumerDir = tmp.newFolder("consumer")
        val consumer = SyncTestFixtures.cloneFrom(bare, consumerDir, "consumer")

        val producerDir = tmp.newFolder("producer")
        val producer = SyncTestFixtures.cloneFrom(bare, producerDir, "producer")
        File(producerDir, "a.md").writeText("remote line\n")
        producer.commitAll("remote edit")
        producer.push()

        File(consumerDir, "a.md").writeText("local line\n")
        consumer.commitAll("local edit")
        return consumer to consumerDir
    }
}
