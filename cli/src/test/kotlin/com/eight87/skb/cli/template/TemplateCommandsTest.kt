package com.eight87.skb.cli.template

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
 * Phase AAA / CLI-U — `skb template list|apply|reset` end-to-end tests.
 *
 * Drives the real [TemplateGroup] against a temp repo with a fake
 * GitOps. Asset loading is mocked via [overrideAssetLoader] so the test
 * doesn't depend on the `:app` module's asset tree being on the CLI's
 * test classpath.
 */
class TemplateCommandsTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var repo: Path
    private val fake = FakeGit()

    /** A tiny inline template — exercises parsing + scaffolding without
     *  depending on the full lifestyle TOML being shipped to the test
     *  classpath. */
    private val miniTemplate = """
        schema_version = 1
        template_id = "mini-template"
        display_name = "Mini Template"
        category = "test"

        [[entry]]
        id = "alpha"
        title = "alpha"
        neutral_title = "Alpha entry"
        duration_minutes = 5
        tags = ["test"]

        [[entry]]
        id = "beta"
        title = "beta"
        neutral_title = "Beta entry"
        duration_minutes = 10
        privacy_flag = true
        tags = ["test", "private"]
        [[entry.subbeat]]
        label = "first"
        duration_seconds = 60
        [[entry.subbeat]]
        label = "second"
        duration_seconds = 60
    """.trimIndent()

    @Before fun setup() {
        overrideGitOps(fake)
        repo = tmp.newFolder("repo").toPath()
        overrideAssetLoader { tid ->
            if (tid == "mini-template") miniTemplate.toByteArray() else null
        }
    }

    @After fun teardown() {
        overrideGitOps(null)
        overrideAssetLoader(null)
    }

    private fun run(vararg args: String) {
        val skb = Skb()
        val ctxOf = { skb.ctx }
        skb.subcommands(RepoGroup(ctxOf), TemplateGroup(ctxOf))
        skb.parse(args.toList())
    }

    @Test fun parseTemplateEntriesExtractsKnownFields() {
        val (tid, entries) = parseTemplateEntries(miniTemplate)
        assertEquals("mini-template", tid)
        assertEquals(2, entries.size)
        assertEquals("alpha", entries[0].id)
        assertEquals("Alpha entry", entries[0].neutralTitle)
        assertEquals(5, entries[0].durationMinutes)
        assertEquals("beta", entries[1].id)
        assertTrue("beta privacy_flag", entries[1].privacyFlag)
        assertTrue("beta tags includes private", "private" in entries[1].tags)
    }

    @Test fun applyMaterializesEventsWithTemplateSlotTags() {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        run(
            "--repo", repo.toString(), "template", "apply", "mini-template",
            "--at", "2026-05-12T09:00:00Z",
        )
        val eventFiles = Files.walk(repo.resolve("calendars")).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .filter { !it.toString().endsWith("calendar.toml") }
                .toList()
        }
        assertEquals(2, eventFiles.size)
        val texts = eventFiles.map { Files.readString(it) }
        assertTrue(
            "expected an event tagged with template_slot:mini-template/alpha",
            texts.any { it.contains("template_slot:mini-template/alpha") },
        )
        assertTrue(
            "expected origin:cli tag",
            texts.all { it.contains("template_origin:cli") },
        )
    }

    @Test fun applyIsIdempotent() {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        run("--repo", repo.toString(), "template", "apply", "mini-template",
            "--at", "2026-05-12T09:00:00Z")
        // Second apply MUST NOT create duplicate events for the same slot.
        run("--repo", repo.toString(), "template", "apply", "mini-template",
            "--at", "2026-05-12T09:00:00Z")
        val eventFiles = Files.walk(repo.resolve("calendars")).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .filter { !it.toString().endsWith("calendar.toml") }
                .toList()
        }
        assertEquals("2 entries × 1 (idempotent)", 2, eventFiles.size)
    }

    @Test fun resetDeletesCliOriginEntries() {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        run("--repo", repo.toString(), "template", "apply", "mini-template",
            "--at", "2026-05-12T09:00:00Z")
        run("--repo", repo.toString(), "template", "reset", "mini-template")
        val eventFiles = Files.walk(repo.resolve("calendars")).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .filter { !it.toString().endsWith("calendar.toml") }
                .toList()
        }
        assertEquals(0, eventFiles.size)
    }

    @Test fun dryRunApplyWritesNoFiles() {
        run("repo", "init", repo.toString(), "--default-calendar", "Personal")
        run(
            "--repo", repo.toString(), "--dry-run",
            "template", "apply", "mini-template",
            "--at", "2026-05-12T09:00:00Z",
        )
        val eventFiles = Files.walk(repo.resolve("calendars")).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .filter { !it.toString().endsWith("calendar.toml") }
                .count()
        }
        assertEquals(0L, eventFiles)
    }
}

private class FakeGit : GitOps {
    data class Commit(val files: List<String>, val message: String)
    val commits = mutableListOf<Commit>()
    val inits = mutableListOf<Path>()

    override fun init(path: Path) { inits.add(path) }

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

    override fun headShortSha(repoRoot: Path): String? = commits.lastOrNull()?.let { "fake-sha-${commits.size}" }
}
