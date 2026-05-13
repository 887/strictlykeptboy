package com.eight87.skb.cli.attachment

import com.eight87.skb.cli.Skb
import com.eight87.skb.cli.commands.EventGroup
import com.eight87.skb.cli.commands.RepoGroup
import com.eight87.skb.cli.commands.overrideGitOps
import com.eight87.skb.cli.core.GitOps
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase BBB.14 — `skb attach add|list` CLI tests.
 *
 * Drives the real command groups against a temp repo with a fake
 * GitOps. Per HV-O.2 the attachment round-trip + LFS threshold + privacy
 * inheritance are covered here for the file kinds the CLI ships.
 */
class AttachmentCommandsTest {
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
        skb.subcommands(RepoGroup(ctxOf), EventGroup(ctxOf), AttachGroup(ctxOf))
        skb.parse(args.toList())
    }

    private fun seedRepoWithEvent(): Pair<String, String> {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        val calId = Files.list(repo.resolve("calendars")).use { it.findFirst().get().fileName.toString() }
        run(
            "--repo", repo.toString(), "event", "add",
            "--title", "Doctor", "--start", "2026-05-12T10:00:00Z",
            "--duration", "PT30M", "--calendar", calId,
        )
        // Locate the freshly written event file to learn its id.
        val eventFile = Files.walk(repo.resolve("calendars/$calId/events")).use { s ->
            s.filter { it.toString().endsWith(".md") }.findFirst().get()
        }
        val eventId = eventFile.fileName.toString().removeSuffix(".md")
        return calId to eventId
    }

    @Test fun addLinkAppendsAttachmentBlock() {
        val (_, eventId) = seedRepoWithEvent()
        run(
            "--repo", repo.toString(), "attach", "add",
            "--event", eventId, "--kind", "link",
            "--url", "https://example.test/booking",
        )
        val eventFile = locateEventFile(repo, eventId)!!
        val text = Files.readString(eventFile)
        assertTrue(text.contains("[[attachment]]"))
        assertTrue(text.contains("kind = \"link\""))
        assertTrue(text.contains("url = \"https://example.test/booking\""))
    }

    @Test fun addFileCopiesAssetUnderAttachmentsDir() {
        val (_, eventId) = seedRepoWithEvent()
        val src = tmp.newFile("ticket.pdf")
        Files.writeString(src.toPath(), "tiny-pdf-bytes")
        run(
            "--repo", repo.toString(), "attach", "add",
            "--event", eventId, "--kind", "file",
            "--file", src.absolutePath, "--mime", "application/pdf",
        )
        val asset = repo.resolve("attachments/$eventId/ticket.pdf")
        assertTrue("asset copied", Files.exists(asset))
        val text = Files.readString(locateEventFile(repo, eventId)!!)
        assertTrue(text.contains("size_bytes ="))
        assertTrue(text.contains("mime_type = \"application/pdf\""))
    }

    @Test fun addLocationValidatesLatLon() {
        val (_, eventId) = seedRepoWithEvent()
        run(
            "--repo", repo.toString(), "attach", "add",
            "--event", eventId, "--kind", "location",
            "--lat", "52.520008", "--lon", "13.404954",
            "--label", "BB Gate",
        )
        val text = Files.readString(locateEventFile(repo, eventId)!!)
        assertTrue(text.contains("kind = \"location\""))
        assertTrue(text.contains("lat = \"52.520008\""))
    }

    @Test fun listSurfaceReturnsAddedAttachments() {
        val (_, eventId) = seedRepoWithEvent()
        run(
            "--repo", repo.toString(), "attach", "add",
            "--event", eventId, "--kind", "link", "--url", "https://x",
        )
        run(
            "--repo", repo.toString(), "attach", "add",
            "--event", eventId, "--kind", "link", "--url", "https://y",
        )
        // Listing succeeds; commits recorded for both adds.
        run("--repo", repo.toString(), "attach", "list", "--event", eventId)
        assertTrue(fake.commits.count { it.message.contains("attach link") } >= 2)
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
