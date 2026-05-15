package com.eight87.strictlykeptboy.backup

import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * Round 2.17 Phase F.5 — round-trip test for [BackupArchiver].
 *
 * Pre-seeds a parent dir with two repos, a `.skb-root` marker that
 * MUST be skipped, a JGit-shaped pack `.tmp` lockfile that MUST be
 * skipped, and a `CLAUDE.md -> AGENTS.md` symlink that MUST round-trip
 * as `LF_SYMLINK`. Exports to an in-memory buffer, then re-reads the
 * tar.gz and asserts:
 *
 *  - first entry is `manifest.toml` (so a partial read shows it),
 *  - the manifest TOML parses + carries the right field values,
 *  - every regular file survives byte-identically,
 *  - the symlink round-trips as a symlink entry with the expected target,
 *  - `.skb-root` and the pack `.tmp` lockfile are absent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BackupArchiverTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun roundTripsTwoReposIncludingSymlink() = runTest {
        val parent = tmp.newFolder("strictlykeptboy")

        // Skb root marker — must NOT appear in the archive.
        File(parent, ".skb-root").writeText(
            """
            skb_root_schema = 1
            created_at = "2026-05-15T00:00:00Z"
            device_name = "test"
            """.trimIndent(),
        )

        // Repo A — calendar with one event file + a .git dir.
        val repoA = File(parent, "repo-a").apply { mkdirs() }
        File(repoA, "AGENTS.md").writeText("# repo-a agents\n")
        // CLAUDE.md → AGENTS.md symlink. Skipped (and the assertion
        // relaxed) on platforms / sandboxes where symlinks aren't
        // creatable.
        val symlinkOk = runCatching {
            Files.createSymbolicLink(
                File(repoA, "CLAUDE.md").toPath(),
                File("AGENTS.md").toPath(),
            )
        }.isSuccess
        val calendarsDir = File(repoA, "calendars/cal-1/events/2026/05").apply { mkdirs() }
        File(calendarsDir, "evt-1.md").writeText("+++\ntitle = \"hello\"\n+++\nbody\n")
        // JGit pack .tmp lockfile — must NOT appear.
        val packDir = File(repoA, ".git/objects/pack").apply { mkdirs() }
        File(packDir, ".tmp-lock-123").writeText("lockfile garbage")
        // A real .git file that SHOULD survive.
        File(repoA, ".git").also { it.mkdirs() }
        File(repoA, ".git/HEAD").writeText("ref: refs/heads/main\n")

        // Repo B — todolist with one task file.
        val repoB = File(parent, "repo-b").apply { mkdirs() }
        File(repoB, "AGENTS.md").writeText("# repo-b agents\n")
        val tasksDir = File(repoB, "todolists/tl-1/tasks/2026/05").apply { mkdirs() }
        File(tasksDir, "task-1.md").writeText("+++\ntitle = \"task\"\n+++\nbody-b\n")

        // Export.
        val buf = ByteArrayOutputStream()
        val manifest = BackupArchiver.export(
            parent = parent,
            out = buf,
            skbVersion = "0.1.0-test",
            parentLabel = "test-label",
            deviceName = "test-device",
        )
        assertEquals(1, manifest.schemaVersion)
        assertEquals(2, manifest.repoCount)
        assertTrue("bytesWritten>0", manifest.bytesWritten > 0)

        // Read back.
        val entries = mutableListOf<TarArchiveEntry>()
        val contents = mutableMapOf<String, ByteArray>()
        val linkTargets = mutableMapOf<String, String>()
        TarArchiveInputStream(GzipCompressorInputStream(buf.toByteArray().inputStream())).use { tar ->
            var e = tar.nextEntry
            while (e != null) {
                entries.add(e)
                if (e.isSymbolicLink) {
                    linkTargets[e.name] = e.linkName
                } else if (!e.isDirectory) {
                    contents[e.name] = tar.readBytes()
                }
                e = tar.nextEntry
            }
        }

        // First entry must be the manifest.
        assertEquals("manifest.toml", entries.first().name)
        val manifestText = contents["manifest.toml"]!!.toString(Charsets.UTF_8)
        assertTrue(manifestText, manifestText.contains("schema_version = 1"))
        assertTrue(manifestText, manifestText.contains("repo_count = 2"))
        assertTrue(manifestText, manifestText.contains("parent_label = \"test-label\""))
        assertTrue(manifestText, manifestText.contains("device_name = \"test-device\""))
        assertTrue(manifestText, manifestText.contains("skb_version = \"0.1.0-test\""))

        // Skips honored.
        val names = entries.map { it.name }.toSet()
        assertTrue(".skb-root must be skipped", names.none { it == ".skb-root" })
        assertTrue(
            "pack .tmp must be skipped",
            names.none { it.endsWith(".git/objects/pack/.tmp-lock-123") },
        )

        // Regular files survive byte-identical.
        val expected = mapOf(
            "repo-a/AGENTS.md" to "# repo-a agents\n",
            "repo-a/calendars/cal-1/events/2026/05/evt-1.md"
                to "+++\ntitle = \"hello\"\n+++\nbody\n",
            "repo-a/.git/HEAD" to "ref: refs/heads/main\n",
            "repo-b/AGENTS.md" to "# repo-b agents\n",
            "repo-b/todolists/tl-1/tasks/2026/05/task-1.md"
                to "+++\ntitle = \"task\"\n+++\nbody-b\n",
        )
        for ((path, expectedText) in expected) {
            val bytes = contents[path]
                ?: error("missing entry $path; have=${contents.keys}")
            assertArrayEquals(
                "content mismatch for $path",
                expectedText.toByteArray(Charsets.UTF_8),
                bytes,
            )
        }

        // Symlink round-trips (when the host filesystem allowed creation).
        if (symlinkOk) {
            val link = entries.firstOrNull { it.name == "repo-a/CLAUDE.md" }
            assertNotNull("symlink entry missing", link)
            assertTrue("entry must be LF_SYMLINK", link!!.isSymbolicLink)
            assertEquals("AGENTS.md", linkTargets["repo-a/CLAUDE.md"])
        }
    }
}
