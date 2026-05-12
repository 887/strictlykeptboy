package com.eight87.strictlykeptboy.sync

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SyncCoalescingTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun fiveRapidRequestsCollapseToOne() = runBlocking {
        val bare = SyncTestFixtures.initBare(tmp.root)
        val work = tmp.newFolder("work")
        val repo = SyncTestFixtures.cloneFrom(bare, work, "coalesced")
        val repoStore = SyncTestFixtures.newRepoStore()
        repoStore.add(SyncTestFixtures.cfgFor(repo))

        val callCount = AtomicInteger(0)
        val sched = SyncScheduler(
            repoStore = repoStore,
            statusStore = SyncTestFixtures.newStatusStore(),
            repoProvider = { cfg ->
                callCount.incrementAndGet()
                repo
            },
            coalesceWindowMs = 200L,
        )
        // Fire 5 in rapid succession well within the 200ms window.
        repeat(5) { sched.requestSync("coalesced") }
        // Wait for the coalesce window + a hair for the pass to run.
        kotlinx.coroutines.delay(800)

        assertEquals(1, callCount.get())
    }
}
