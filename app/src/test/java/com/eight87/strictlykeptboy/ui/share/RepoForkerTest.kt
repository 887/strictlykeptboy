package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.store.ReferencesManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

private fun writeText(p: java.nio.file.Path, text: String) {
    Files.write(p, text.toByteArray(StandardCharsets.UTF_8))
}

private fun readText(p: java.nio.file.Path): String =
    String(Files.readAllBytes(p), StandardCharsets.UTF_8)

/**
 * Phase SS.5 — fork mechanic + reference back-link write.
 *
 * Pure-JVM (TemporaryFolder + java.nio.file). No Android deps, no
 * Robolectric — the underlying [RepoForker] is filesystem-only.
 */
class RepoForkerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun seedSourceRepo(): java.nio.file.Path {
        val root = tmp.newFolder("source").toPath()
        Files.createDirectories(root.resolve(".strictlykeptboy"))
        Files.createDirectories(root.resolve("calendars"))
        writeText(
            root.resolve(".strictlykeptboy/repo-id"),
            "01900000-0000-7000-8000-000000000111\n",
        )
        writeText(
            root.resolve(".strictlykeptboy/repo.toml"),
            """
            +++
            schema_version = 1
            id = "01900000-0000-7000-8000-000000000111"
            kind = "repo_meta"
            name = "shared-cal"
            +++
            """.trimIndent(),
        )
        writeText(
            root.resolve(".strictlykeptboy/repo-fingerprint"),
            "abcdef0123456789\n",
        )
        writeText(
            root.resolve("calendars/sample.toml"),
            "schema_version = 1\nid = \"cal-1\"\n",
        )
        return root
    }

    @Test fun fork_copies_tree_and_resets_repo_id() {
        val src = seedSourceRepo()
        val dst = tmp.newFolder("dest").toPath()
        // make dst empty (newFolder creates an empty dir already)
        val outcome = RepoForker.fork(
            sourceRepoRoot = src,
            destinationRepoRoot = dst,
            newRepoId = "01900000-0000-7000-8000-000000000222",
            sourceRepoId = "01900000-0000-7000-8000-000000000111",
            sourceLabel = "shared-cal",
            sourceUrls = listOf("https://example.com/shared-cal.git"),
            sourceFingerprint = "abcdef0123456789",
        )
        val ok = outcome as RepoForker.ForkOutcome.Ok
        assertEquals("01900000-0000-7000-8000-000000000222", ok.result.newRepoId)
        // repo-id was reset
        val newId = readText(dst.resolve(".strictlykeptboy/repo-id")).trim()
        assertEquals("01900000-0000-7000-8000-000000000222", newId)
        // repo.toml `id` field was rewritten
        val tomlText = readText(dst.resolve(".strictlykeptboy/repo.toml"))
        assertTrue(
            "repo.toml should contain rewritten id field: $tomlText",
            tomlText.contains("id = \"01900000-0000-7000-8000-000000000222\""),
        )
        assertFalse(
            "stale source id should be gone",
            tomlText.contains("id = \"01900000-0000-7000-8000-000000000111\""),
        )
        // fingerprint cache was invalidated
        assertFalse(Files.exists(dst.resolve(".strictlykeptboy/repo-fingerprint")))
        // sample asset copied
        assertTrue(Files.isRegularFile(dst.resolve("calendars/sample.toml")))
    }

    @Test fun fork_writes_back_reference_entry() {
        val src = seedSourceRepo()
        val dst = tmp.newFolder("dest2").toPath()
        RepoForker.fork(
            sourceRepoRoot = src,
            destinationRepoRoot = dst,
            newRepoId = "01900000-0000-7000-8000-000000000333",
            sourceRepoId = "01900000-0000-7000-8000-000000000111",
            sourceLabel = "shared-cal",
            sourceUrls = listOf("https://example.com/shared-cal.git"),
            sourceFingerprint = "abcdef0123456789",
        )
        val manifest = ReferencesManifest.read(dst)
        assertEquals(1, manifest.entries.size)
        val entry = manifest.entries.single()
        assertEquals("01900000-0000-7000-8000-000000000111", entry.repoId)
        assertEquals("shared-cal", entry.displayName)
        assertEquals(listOf("https://example.com/shared-cal.git"), entry.urls)
        assertEquals("abcdef0123456789", entry.writeBackTarget)
    }

    @Test fun fork_refuses_non_empty_destination() {
        val src = seedSourceRepo()
        val dst = tmp.newFolder("dest3").toPath()
        writeText(dst.resolve("squatter.txt"), "occupied")
        val outcome = RepoForker.fork(
            sourceRepoRoot = src,
            destinationRepoRoot = dst,
            newRepoId = "nid",
            sourceRepoId = "sid",
            sourceLabel = "x",
            sourceUrls = listOf("https://x"),
        )
        assertTrue(outcome is RepoForker.ForkOutcome.Failed)
    }

    @Test fun fork_refuses_missing_source() {
        val src = tmp.root.toPath().resolve("does-not-exist")
        val dst = tmp.newFolder("dest4").toPath()
        val outcome = RepoForker.fork(
            sourceRepoRoot = src,
            destinationRepoRoot = dst,
            newRepoId = "nid",
            sourceRepoId = "sid",
            sourceLabel = "x",
            sourceUrls = listOf("https://x"),
        )
        assertTrue(outcome is RepoForker.ForkOutcome.Failed)
    }

    @Test fun fork_with_no_source_fingerprint_skips_write_back_target() {
        val src = seedSourceRepo()
        val dst = tmp.newFolder("dest5").toPath()
        RepoForker.fork(
            sourceRepoRoot = src,
            destinationRepoRoot = dst,
            newRepoId = "nid",
            sourceRepoId = "01900000-0000-7000-8000-000000000111",
            sourceLabel = "shared-cal",
            sourceUrls = listOf("https://example.com/shared-cal.git"),
            sourceFingerprint = null,
        )
        val manifest = ReferencesManifest.read(dst)
        val entry = manifest.entries.single()
        assertNull(entry.writeBackTarget)
        assertNotNull(entry)
    }
}
