package com.eight87.skb.cli.reminder

import com.eight87.skb.cli.Skb
import com.eight87.skb.cli.attachment.locateEventFile
import com.eight87.skb.cli.commands.EventGroup
import com.eight87.skb.cli.commands.RepoGroup
import com.eight87.skb.cli.commands.overrideGitOps
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.GitOps
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase BBB.14 — `skb reminder add|rm|list` CLI tests.
 */
class ReminderCommandsTest {
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
        skb.subcommands(RepoGroup(ctxOf), EventGroup(ctxOf), ReminderGroup(ctxOf))
        skb.parse(args.toList())
    }

    private fun seedEvent(): String {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        val calId = Files.list(repo.resolve("calendars")).use { it.findFirst().get().fileName.toString() }
        run(
            "--repo", repo.toString(), "event", "add",
            "--title", "Doctor", "--start", "2026-05-12T10:00:00Z",
            "--duration", "PT30M", "--calendar", calId,
        )
        val ef = Files.walk(repo.resolve("calendars/$calId/events")).use { s ->
            s.filter { it.toString().endsWith(".md") }.findFirst().get()
        }
        return ef.fileName.toString().removeSuffix(".md")
    }

    @Test fun addInsertsReminderBlock() {
        val id = seedEvent()
        run(
            "--repo", repo.toString(), "reminder", "add",
            "--event", id, "--offset", "-PT30M", "--kind", "pre_event",
        )
        val text = Files.readString(locateEventFile(repo, id)!!)
        assertTrue(text.contains("[[reminder]]"))
        assertTrue(text.contains("offset = \"-PT30M\""))
        assertTrue(text.contains("kind = \"pre_event\""))
    }

    @Test fun rmDropsMatchingBlock() {
        val id = seedEvent()
        run("--repo", repo.toString(), "reminder", "add",
            "--event", id, "--offset", "-PT30M", "--kind", "pre_event")
        run("--repo", repo.toString(), "reminder", "add",
            "--event", id, "--offset", "0", "--kind", "at_start")
        run("--repo", repo.toString(), "reminder", "rm",
            "--event", id, "--offset", "-PT30M", "--kind", "pre_event")
        val text = Files.readString(locateEventFile(repo, id)!!)
        assertFalse("pre_event block removed", text.contains("offset = \"-PT30M\""))
        assertTrue("at_start block survives", text.contains("offset = \"0\""))
    }

    @Test fun rejectsBadOffset() {
        val id = seedEvent()
        var threw = false
        try {
            run(
                "--repo", repo.toString(), "reminder", "add",
                "--event", id, "--offset", "five-minutes", "--kind", "pre_event",
            )
        } catch (e: CliError) {
            threw = true
        }
        assertTrue("malformed offset is rejected", threw)
    }

    @Test fun rejectsUnknownKind() {
        val id = seedEvent()
        var threw = false
        try {
            run(
                "--repo", repo.toString(), "reminder", "add",
                "--event", id, "--offset", "0", "--kind", "vibes_check",
            )
        } catch (e: CliError) {
            threw = true
        }
        assertTrue("unknown kind is rejected", threw)
    }

    @Test fun listSurfaceCountsBlocks() {
        val id = seedEvent()
        run("--repo", repo.toString(), "reminder", "add",
            "--event", id, "--offset", "-PT1H", "--kind", "pre_event")
        run("--repo", repo.toString(), "reminder", "add",
            "--event", id, "--offset", "0", "--kind", "at_start")
        run("--repo", repo.toString(), "reminder", "list", "--event", id)
        assertTrue(fake.commits.count { it.message.contains("reminder add") } >= 2)
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
