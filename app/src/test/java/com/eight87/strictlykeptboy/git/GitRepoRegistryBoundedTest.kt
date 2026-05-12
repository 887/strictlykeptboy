package com.eight87.strictlykeptboy.git

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase V — registry-bounding regression. Validates the new LRU cap
 * at [GitRepoRegistry.MAX_ENTRIES]: putting > MAX_ENTRIES distinct
 * repos forces eviction of the oldest entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class GitRepoRegistryBoundedTest {

    @get:Rule val tmp = TemporaryFolder()

    @After fun cleanup() { GitRepoRegistry.clear() }

    @Test fun exceedingMaxEntries_evictsEldest() = runTest {
        GitRepoRegistry.clear()
        val opened = (0 until GitRepoRegistry.MAX_ENTRIES + 5).map { i ->
            val dir = tmp.newFolder("r$i")
            org.eclipse.jgit.api.Git.init().setDirectory(dir).setInitialBranch("main").call().close()
            val repo = GitRepo.open(
                rootDir = dir,
                repoId = "repo-$i",
                remotes = emptyList(),
                primaryRemote = null,
                authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
            )
            GitRepoRegistry.put(repo)
            repo
        }
        // First 5 should be evicted; size capped at MAX_ENTRIES.
        assertTrue("size: ${GitRepoRegistry.size()}", GitRepoRegistry.size() <= GitRepoRegistry.MAX_ENTRIES)
        // The most recently inserted is still cached.
        val lastId = "repo-${opened.lastIndex}"
        assertTrue(GitRepoRegistry.get(lastId) === opened.last())
        // The very first one was evicted.
        assertNull(GitRepoRegistry.get("repo-0"))
        // cleanup
        opened.forEach { it.close() }
    }
}
