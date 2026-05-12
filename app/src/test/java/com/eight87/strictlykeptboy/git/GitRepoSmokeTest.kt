package com.eight87.strictlykeptboy.git

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

/**
 * Phase B.9 — smoke test for the no-origin / local-only path. Validates:
 * - JGit loads under Robolectric on Android API 26 + 36 (driven by SkbApp.onCreate)
 * - initLocalOnly produces a working repo
 * - commit + log roundtrip
 * - diffSinceLastIndexed picks up new paths
 * - fetch / push on no-origin return NoRemotes (Phase ZZ.A.3 contract)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class GitRepoSmokeTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun localOnlyInitCommitLogRoundtrip() = runTest {
        val repoDir = tmp.newFolder("repo")
        val repo = GitRepo.initLocalOnly(
            rootDir = repoDir,
            repoId = "test-repo-id",
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )

        File(repoDir, "hello.md").writeText("hello world\n")
        val commit = repo.commitAll("first commit")

        assertTrue("commit succeeded: $commit", commit is CommitResult.Success)
        val log = repo.log()
        assertEquals(1, log.size)
        assertEquals("first commit", log[0].message.trim())
    }

    @Test fun localOnlyReturnsNoRemotesForNetworkOps() = runTest {
        val repoDir = tmp.newFolder("repo")
        val repo = GitRepo.initLocalOnly(
            rootDir = repoDir,
            repoId = "test-repo-id",
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )

        assertEquals(FetchResult.NoRemotes, repo.fetch())
        assertEquals(PullResult.NoRemotes, repo.pullRebase())
        assertEquals(PushResult.NoRemotes, repo.push())
    }

    @Test fun diffSinceLastIndexedPicksUpNewPaths() = runTest {
        val repoDir = tmp.newFolder("repo")
        val repo = GitRepo.initLocalOnly(
            rootDir = repoDir,
            repoId = "test-repo-id",
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )

        File(repoDir, "a.md").writeText("a\n")
        repo.commitAll("add a")
        val firstHead = repo.headSha()!!

        File(repoDir, "b.md").writeText("b\n")
        repo.commitAll("add b")

        val changes = repo.diffSinceLastIndexed(firstHead)
        assertEquals(setOf("b.md"), changes.map { it.path }.toSet())
        assertEquals(ChangeKind.Added, changes.single().kind)
    }
}
