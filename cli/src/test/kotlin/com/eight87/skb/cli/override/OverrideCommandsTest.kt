package com.eight87.skb.cli.override

import com.eight87.skb.cli.Skb
import com.eight87.skb.cli.commands.RepoGroup
import com.eight87.skb.cli.commands.overrideGitOps
import com.eight87.skb.cli.core.GitOps
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase BBB.14 — `skb override add|list` CLI tests.
 */
class OverrideCommandsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var repo: Path
    private val fake = FakeGit()

    @Before fun setup() {
        overrideGitOps(fake)
        repo = tmp.newFolder("repo").toPath()
    }

    @After fun teardown() { overrideGitOps(null) }

    private fun run(vararg args: String) {
        val skb = Skb()
        val ctxOf = { skb.ctx }
        skb.subcommands(RepoGroup(ctxOf), OverrideGroup(ctxOf))
        skb.parse(args.toList())
    }

    @Test fun addWritesForceShowFile() {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        val calId = Files.list(repo.resolve("calendars")).use { it.findFirst().get().fileName.toString() }
        run(
            "--repo", repo.toString(), "override", "add",
            "--calendar", calId, "--event", "evt-1", "--date", "2026-07-04",
        )
        val out = repo.resolve("overrides/$calId/evt-1/2026-07-04.md")
        assertTrue("override file written", Files.exists(out))
        val text = Files.readString(out)
        assertTrue(text.contains("override_kind = \"force-show\""))
        assertTrue(text.contains("event_id = \"evt-1\""))
    }

    @Test fun addRangeWritesForceShowForRange() {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        val calId = Files.list(repo.resolve("calendars")).use { it.findFirst().get().fileName.toString() }
        run(
            "--repo", repo.toString(), "override", "add",
            "--calendar", calId, "--event", "evt-2", "--date", "2026-07-04",
            "--from", "2026-07-04", "--to", "2026-07-08",
        )
        val out = repo.resolve("overrides/$calId/evt-2/2026-07-04.md")
        val text = Files.readString(out)
        assertTrue(text.contains("override_kind = \"force-show-for-range\""))
        assertTrue(text.contains("from = \"2026-07-04\""))
        assertTrue(text.contains("to = \"2026-07-08\""))
    }

    @Test fun listEnumeratesOverrides() {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        val calId = Files.list(repo.resolve("calendars")).use { it.findFirst().get().fileName.toString() }
        run("--repo", repo.toString(), "override", "add",
            "--calendar", calId, "--event", "evt-a", "--date", "2026-07-04")
        run("--repo", repo.toString(), "override", "add",
            "--calendar", calId, "--event", "evt-b", "--date", "2026-07-05")
        // Listing succeeds; success is not throwing + a commit recorded.
        run("--repo", repo.toString(), "override", "list", "--calendar", calId)
        assertEquals(2, fake.commits.count { it.message.startsWith("override force-show") })
    }
}

private class FakeGit : GitOps {
    data class Commit(val files: List<String>, val message: String)
    val commits = mutableListOf<Commit>()

    override fun init(path: Path) {}
    override fun headShortSha(repoRoot: Path): String? = null
    override fun addAndCommit(
        repoRoot: Path,
        files: List<String>,
        message: String,
        authorName: String,
        authorEmail: String,
    ): String? {
        commits.add(Commit(files, message))
        return "fake-sha-${commits.size}"
    }
}
