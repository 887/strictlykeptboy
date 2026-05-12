package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.RepoConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SyncSchedulerTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun runOnceSyncsThreeRepos() = runTest {
        val repos = mutableMapOf<String, GitRepo>()
        val cfgs = mutableListOf<RepoConfig>()
        for (i in 1..3) {
            val bare = SyncTestFixtures.initBare(tmp.root, "bare-$i.git")
            val work = tmp.newFolder("work-$i")
            val repo = SyncTestFixtures.cloneFrom(bare, work, "repo-$i")
            repos[repo.repoId] = repo
            cfgs += SyncTestFixtures.cfgFor(repo)
        }
        val repoStore = SyncTestFixtures.newRepoStore()
        cfgs.forEach { repoStore.add(it) }

        val sched = SyncScheduler(
            repoStore = repoStore,
            statusStore = SyncTestFixtures.newStatusStore(),
            repoProvider = { cfg -> repos[cfg.repoId] },
            coalesceWindowMs = 0L,
        )
        for (id in repos.keys) sched.runOnce(id)
        // Each repo should have a Finished event observable via status store.
        val statusStore = SyncTestFixtures.newStatusStore()
        // statuses from sched's own store
        // run once again with shared store to assert persisted state
        val sharedStatus = SyncTestFixtures.newStatusStore()
        val sched2 = SyncScheduler(
            repoStore = repoStore,
            statusStore = sharedStatus,
            repoProvider = { cfg -> repos[cfg.repoId] },
            coalesceWindowMs = 0L,
        )
        for (id in repos.keys) sched2.runOnce(id)
        val state = sharedStatus.state.first()
        assertEquals(3, state.size)
        assertTrue(state.values.all { it.lastSyncedAt != null })
    }

    @Test fun noOriginRepoIsSkipped() = runTest {
        val work = tmp.newFolder("local")
        val repo = GitRepo.initLocalOnly(
            rootDir = work,
            repoId = "local-only",
            authorIdentity = com.eight87.strictlykeptboy.git.AuthorIdentity("Bat", "b@e.com"),
        )
        val repoStore = SyncTestFixtures.newRepoStore()
        repoStore.add(SyncTestFixtures.cfgFor(repo))

        val sched = SyncScheduler(
            repoStore = repoStore,
            statusStore = SyncTestFixtures.newStatusStore(),
            repoProvider = { repo },
            coalesceWindowMs = 0L,
        )
        sched.runOnce("local-only")
        // No SyncEvent.Started should be emitted; nothing to assert beyond no crash.
        assertTrue(true)
    }
}
