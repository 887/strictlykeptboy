package com.eight87.strictlykeptboy.git

import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
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
 * Phase B.9 — full network-path tests against a real `git init --bare`
 * filesystem repo. File-transport sidesteps SSH / HTTPS auth so we can
 * exercise fetch / pullRebase / push code paths directly.
 *
 * Each test: create a bare repo + one or two working clones, run the actual
 * git ops, assert the resulting state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class GitRepoBareFixtureTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun initBare(): File {
        val bare = tmp.newFolder("bare.git")
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        return bare
    }

    private fun bareBinding(bare: File): RemoteBinding = RemoteBinding(
        name = RemoteName.ORIGIN,
        url = bare.toURI().toString(),
        transport = Transport.File,
        authMethod = AuthMethod.None,
    )

    private suspend fun newWorkingRepoCloned(bare: File, label: String): GitRepo =
        GitRepo.clone(
            rootDir = tmp.newFolder(label),
            repoId = "test-$label",
            primaryBinding = bareBinding(bare),
            authorIdentity = AuthorIdentity("Bat ($label)", "$label@example.com"),
        )

    @Test fun pushFromInitFanOutToBare() = runTest {
        val bare = initBare()
        val workDir = tmp.newFolder("work")
        val repo = GitRepo.init(
            rootDir = workDir,
            repoId = "test-init",
            remotes = listOf(bareBinding(bare)),
            primaryRemote = RemoteName.ORIGIN,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )
        File(workDir, "hello.md").writeText("hi\n")
        repo.commitAll("first")

        val pushResult = repo.push()
        assertEquals(PushResult.Success, pushResult)

        // The bare repo now has the commit.
        Git.open(bare).use { git ->
            val log = git.log().setMaxCount(10).call().toList()
            assertEquals(1, log.size)
        }
    }

    @Test fun cloneFetchPullRebase() = runTest {
        val bare = initBare()

        // Seed: producer commits + pushes a file.
        val producer = run {
            val dir = tmp.newFolder("producer")
            val r = GitRepo.init(
                rootDir = dir,
                repoId = "producer",
                remotes = listOf(bareBinding(bare)),
                primaryRemote = RemoteName.ORIGIN,
                authorIdentity = AuthorIdentity("Producer", "p@example.com"),
            )
            File(dir, "seed.md").writeText("seed\n")
            r.commitAll("seed")
            r.push()
            r
        }

        // Consumer clones, pulls, fetches.
        val consumer = newWorkingRepoCloned(bare, "consumer")
        val initialLog = consumer.log()
        assertEquals(1, initialLog.size)

        // Producer adds another commit + pushes.
        File(producer.rootDir, "second.md").writeText("second\n")
        producer.commitAll("second")
        producer.push()

        // Consumer pullRebase — should see the new commit.
        val pull = consumer.pullRebase()
        assertTrue("expected FastForwarded but got $pull", pull is PullResult.FastForwarded)
        assertEquals(2, consumer.log().size)
    }

    @Test fun diffSinceLastIndexedAcrossPull() = runTest {
        val bare = initBare()
        val producer = run {
            val dir = tmp.newFolder("p")
            val r = GitRepo.init(
                rootDir = dir,
                repoId = "p",
                remotes = listOf(bareBinding(bare)),
                primaryRemote = RemoteName.ORIGIN,
                authorIdentity = AuthorIdentity("P", "p@e.com"),
            )
            File(dir, "a.md").writeText("a\n"); r.commitAll("a"); r.push()
            r
        }
        val consumer = newWorkingRepoCloned(bare, "c")
        val firstHead = consumer.headSha()!!

        // Producer adds three files.
        listOf("b.md", "c.md", "d.md").forEach { name ->
            File(producer.rootDir, name).writeText("$name\n")
        }
        producer.commitAll("more")
        producer.push()

        consumer.pullRebase()
        val changes = consumer.diffSinceLastIndexed(firstHead)
        assertEquals(setOf("b.md", "c.md", "d.md"), changes.map { it.path }.toSet())
        assertTrue(changes.all { it.kind == ChangeKind.Added })
    }

    @Test fun pushFailsAndReturnsResultWhenBareIsMissing() = runTest {
        // No bare repo created — fake URL.
        val badBare = File(tmp.root, "does-not-exist.git")
        val workDir = tmp.newFolder("work")
        val repo = GitRepo.init(
            rootDir = workDir,
            repoId = "bad",
            remotes = listOf(
                RemoteBinding(
                    name = RemoteName.ORIGIN,
                    url = badBare.toURI().toString(),
                    transport = Transport.File,
                    authMethod = AuthMethod.None,
                ),
            ),
            primaryRemote = RemoteName.ORIGIN,
            authorIdentity = AuthorIdentity("Bat", "b@e.com"),
        )
        File(workDir, "x.md").writeText("x\n")
        repo.commitAll("x")

        val pushResult = repo.push()
        assertTrue(
            "expected Failed or Rejected, got $pushResult",
            pushResult is PushResult.Failed || pushResult is PushResult.Rejected,
        )
    }

    @Test fun statusReflectsLocalChanges() = runTest {
        val bare = initBare()
        val repo = newWorkingRepoCloned(bare, "w")
        // Empty clone: clean.
        assertTrue(repo.status().isClean)

        File(repo.rootDir, "fresh.md").writeText("fresh\n")
        val status = repo.status()
        assertTrue("fresh.md is untracked", "fresh.md" in status.untracked)
        assertTrue(!status.isClean)
    }

    @Test fun localOnlyCommitsCountForNoOrigin() = runTest {
        val workDir = tmp.newFolder("local")
        val repo = GitRepo.initLocalOnly(
            rootDir = workDir,
            repoId = "local",
            authorIdentity = AuthorIdentity("Bat", "bat@e.com"),
        )
        File(workDir, "a.md").writeText("a\n"); repo.commitAll("a")
        File(workDir, "b.md").writeText("b\n"); repo.commitAll("b")
        File(workDir, "c.md").writeText("c\n"); repo.commitAll("c")

        // All 3 commits are local-only for no-origin repos.
        assertEquals(3, repo.status().localOnlyCommits)
    }
}
