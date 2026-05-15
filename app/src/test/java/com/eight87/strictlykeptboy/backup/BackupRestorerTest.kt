package com.eight87.strictlykeptboy.backup

import com.eight87.strictlykeptboy.prefs.SkbRootMarker
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlValue
import com.eight87.strictlykeptboy.store.TomlWriter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Round 2.17 Phase G.7 — export-then-restore round-trip.
 *
 * Builds a parent A with two repos (including `.skb-root` marker +
 * `.strictlykeptboy/repo.toml` carrying `id` + `name`), exports via
 * [BackupArchiver], restores into an empty parent B via
 * [BackupRestorer], then asserts:
 *
 *  - `rescanParent(B)` returns one [com.eight87.strictlykeptboy.git.RepoConfig]
 *    per source repo,
 *  - the discovered `repoId` + `displayName` match what `repo.toml` said,
 *  - the `.skb-root` marker is re-created at B's root (preservation
 *    guarantee per G.6 — archive skipped the marker but the restorer
 *    regenerates one).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BackupRestorerTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun roundTripRescansToSameConfigs() = runTest {
        val parentA = tmp.newFolder("A")
        SkbRootMarker.write(parentA, "device-A")
        seedRepo(parentA, repoId = "repo-one", displayName = "Repo One")
        seedRepo(parentA, repoId = "repo-two", displayName = "Repo Two")

        val buf = ByteArrayOutputStream()
        BackupArchiver.export(
            parent = parentA,
            out = buf,
            skbVersion = "test",
            parentLabel = "A",
            deviceName = "device-A",
        )

        val parentB = tmp.newFolder("B")
        val manifest = BackupRestorer.restoreFromArchive(
            input = ByteArrayInputStream(buf.toByteArray()),
            parent = parentB,
            deviceName = "device-B",
        )
        assertEquals(2, manifest.repoCount)

        // Marker re-created (archive skipped it; restorer regenerated).
        assertTrue(
            "skb-root marker should be present after restore",
            SkbRootMarker.isSkbRoot(parentB),
        )

        val rescanned = BackupRestorer.rescanParent(parentB)
        assertEquals(2, rescanned.size)
        val byId = rescanned.associateBy { it.repoId }
        assertNotNull(byId["repo-one"])
        assertNotNull(byId["repo-two"])
        assertEquals("Repo One", byId["repo-one"]!!.displayName)
        assertEquals("Repo Two", byId["repo-two"]!!.displayName)
        // rootDirs land under the new parent.
        assertTrue(byId["repo-one"]!!.rootDir.startsWith(parentB.absolutePath))
    }

    private fun seedRepo(parent: File, repoId: String, displayName: String) {
        val dir = File(parent, repoId).apply { mkdirs() }
        File(dir, ".git").apply { mkdirs() }
        File(dir, ".git/HEAD").writeText("ref: refs/heads/main\n")
        File(dir, "AGENTS.md").writeText("# $displayName\n")
        val meta = File(dir, ".strictlykeptboy").apply { mkdirs() }
        val table = TomlTable().apply {
            scalars["schema_version"] = TomlValue.I64(1L)
            scalars["id"] = TomlValue.Str(repoId)
            scalars["kind"] = TomlValue.Str("repo_meta")
            scalars["name"] = TomlValue.Str(displayName)
        }
        File(meta, "repo.toml").writeText(TomlWriter.emit(table))
    }
}
