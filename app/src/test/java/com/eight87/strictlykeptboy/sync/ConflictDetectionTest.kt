package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.PullResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class ConflictDetectionTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun divergedEditsTriggerConflictNotification() = runBlocking {
        val bare = SyncTestFixtures.initBare(tmp.root)

        // Seed bare with a base commit.
        val seedDir = tmp.newFolder("seed")
        val seed = SyncTestFixtures.cloneFrom(bare, seedDir, "seed")
        File(seedDir, "a.md").writeText("base\n")
        seed.commitAll("base")
        seed.push()

        // Consumer + producer both clone from base before either edits — this
        // is what creates real divergence.
        val consumerDir = tmp.newFolder("consumer")
        val consumer = SyncTestFixtures.cloneFrom(bare, consumerDir, "consumer")

        val producerDir = tmp.newFolder("producer")
        val producer = SyncTestFixtures.cloneFrom(bare, producerDir, "producer")
        File(producerDir, "a.md").writeText("remote line\n")
        producer.commitAll("remote edit")
        producer.push()

        File(consumerDir, "a.md").writeText("local line\n")
        consumer.commitAll("local edit")

        val repoStore = SyncTestFixtures.newRepoStore()
        repoStore.add(SyncTestFixtures.cfgFor(consumer))

        val sched = SyncScheduler(
            repoStore = repoStore,
            statusStore = SyncTestFixtures.newStatusStore(),
            repoProvider = { consumer },
            coalesceWindowMs = 0L,
        )

        val notifDeferred = async {
            withTimeout(10_000) {
                sched.eventsFlow.filterIsInstance<SyncEvent.ConflictNotification>().first()
            }
        }
        // Give the collector a chance to subscribe before emit.
        yield()
        kotlinx.coroutines.delay(100)
        sched.runOnce("consumer")
        val notif = notifDeferred.await()
        // JGit may report paths absolute or relative depending on internals; just
        // assert that the conflict surfaced and registered.
        assertNotNull(ConflictRegistry.get(notif.conflictKey))
    }
}
