package com.eight87.strictlykeptboy.git

import com.eight87.strictlykeptboy.sync.SyncTestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase ZZ.H — end-to-end invariants for the no-origin + multi-origin
 * extensions. Spec from main.md Phase ZZ.H:
 *
 *   "no-origin → add origin → add mirror-1 → push to both → simulate
 *    mirror-1 read-only → primary still pushes."
 *
 * Each step asserts the documented invariants (i / ii / iii) so the
 * test doubles as an executable spec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class MultiOriginEndToEndTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun noOriginGrowsIntoOriginPlusMirrorAndPrimaryShipsWhenMirrorReadOnly() = runTest {
        // Step 0: initLocalOnly — no-origin repo, file-on-disk repo-id, no foreground sync.
        val workDir = tmp.newFolder("work")
        val authorIdentity = AuthorIdentity("Bat", "bat@example.com")
        val repoId = "e2e-${System.nanoTime()}"
        val repo = GitRepo.initLocalOnly(
            rootDir = workDir,
            repoId = repoId,
            authorIdentity = authorIdentity,
        )
        // Invariant (i): no-origin repo has no remotes.
        assertTrue(repo.remotes.isEmpty())
        // Invariant (ii): primaryRemote == null <=> remotes.isEmpty().
        assertNull(repo.primaryRemote)
        // Invariant (i) corollary: writeRepoIdFile shipped the .strictlykeptboy/repo-id.
        assertEquals(repoId, GitRepo.readRepoIdFile(workDir))

        // Local commit before any remote — works fine in no-origin mode.
        File(workDir, "hello.md").writeText("hi\n")
        val commit1 = repo.commitAll("first")
        assertTrue("commit1=$commit1", commit1 is CommitResult.Success)

        // Network ops on a no-origin repo are well-behaved (no throw).
        assertTrue(repo.fetch() is FetchResult.NoRemotes)
        assertTrue(repo.pullRebase() is PullResult.NoRemotes)
        assertTrue(repo.push() is PushResult.NoRemotes)

        // Step 1: add origin (becomes primary because no-origin -> first remote).
        val originBare = SyncTestFixtures.initBare(tmp.root, "origin.git")
        val originBinding = RemoteBinding(
            name = RemoteName.ORIGIN,
            url = originBare.toURI().toString(),
            transport = Transport.File,
            authMethod = AuthMethod.None,
        )
        repo.addRemote(originBinding)
        assertEquals(1, repo.remotes.size)
        assertEquals(RemoteName.ORIGIN, repo.primaryRemote)
        // Invariant (iii): repoId stable across remote-set changes.
        assertEquals(repoId, repo.repoId)

        // First push to a fresh primary — git push -u origin main against extant history.
        val push1 = repo.push()
        assertTrue("push1=$push1", push1 is PushResult.Success)

        // Step 2: add mirror-1 (defaults to PushPolicy.Push for this test;
        // ZZ.E says additional defaults to PushLazy but the API caller chose
        // explicit Push here for the multi-fan-out semantics under test).
        val mirror1Bare = SyncTestFixtures.initBare(tmp.root, "mirror1.git")
        val mirror1 = RemoteBinding(
            name = RemoteName("mirror-1"),
            url = mirror1Bare.toURI().toString(),
            transport = Transport.File,
            authMethod = AuthMethod.None,
            pushPolicy = PushPolicy.Push,
        )
        repo.addRemote(mirror1)
        assertEquals(2, repo.remotes.size)
        assertEquals(RemoteName.ORIGIN, repo.primaryRemote) // unchanged

        // Push to both succeeds.
        File(workDir, "second.md").writeText("two\n")
        repo.commitAll("second")
        val pushBoth = repo.push(null) // fan-out
        assertTrue("pushBoth=$pushBoth", pushBoth is PushResult.Success)

        // Step 3: simulate mirror-1 read-only by replacing its bare with a missing dir.
        // Easiest: rename the bare to make subsequent pushes fail (network-level failure
        // is the closest available simulation of "no write access" without a real auth
        // surface in this fixture; the SyncError discriminator is the part under test).
        val brokenName = "${mirror1Bare.name}-broken"
        val moved = File(mirror1Bare.parentFile, brokenName)
        assertTrue("could not move mirror1 bare", mirror1Bare.renameTo(moved))

        File(workDir, "third.md").writeText("three\n")
        repo.commitAll("third")
        val pushAfterBreak = repo.push(null)

        // Primary still ships; mirror-1 is in the failed set → PartiallySuccess.
        assertTrue(
            "pushAfterBreak=$pushAfterBreak",
            pushAfterBreak is PushResult.PartiallySuccess,
        )
        val partial = pushAfterBreak as PushResult.PartiallySuccess
        assertTrue("origin in pushed", RemoteName.ORIGIN in partial.pushed)
        assertTrue("mirror-1 in failed", RemoteName("mirror-1") in partial.failed)
        assertFalse("origin not in failed", RemoteName.ORIGIN in partial.failed)

        // listRemotes() still returns the in-memory snapshot.
        assertEquals(2, repo.listRemotes().size)

        // Step 4: remove the broken mirror; primary stays primary.
        repo.removeRemote(RemoteName("mirror-1"))
        assertEquals(1, repo.remotes.size)
        assertEquals(RemoteName.ORIGIN, repo.primaryRemote)

        // Step 5: rename origin to "main-remote" — primary pointer follows.
        repo.renameRemote(RemoteName.ORIGIN, RemoteName("main-remote"))
        assertEquals(RemoteName("main-remote"), repo.primaryRemote)
        assertNotNull(repo.remotes.firstOrNull { it.name == RemoteName("main-remote") })

        // Removing the last remote returns the repo to no-origin shape (primary == null).
        repo.removeRemote(RemoteName("main-remote"))
        assertTrue(repo.remotes.isEmpty())
        assertNull(repo.primaryRemote)
    }
}
