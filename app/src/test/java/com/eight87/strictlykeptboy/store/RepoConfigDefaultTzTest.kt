package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round 2.24.A.4 — `RepoMetaReader` exposes `default_tz_id` from
 * `<repoRoot>/.strictlykeptboy/repo.toml` (D-2.24.b).
 *
 * Cases:
 * - File present with `default_tz_id = "..."` → returned.
 * - File present without the field → `null`.
 * - File missing entirely → reader returns `null` (no throw).
 */
class RepoConfigDefaultTzTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun writeRepoToml(content: String): File {
        val root = tmp.newFolder("repo")
        val metaDir = File(root, ".strictlykeptboy").apply { mkdirs() }
        File(metaDir, "repo.toml").writeText(content, Charsets.UTF_8)
        return root
    }

    @Test fun defaultTzIdPresentInRepoToml() {
        val root = writeRepoToml(
            """
            id = "0190a0aa-1c1d-7000-8a0a-deadbeef0001"
            name = "kept-life"
            default_tz_id = "America/New_York"
            """.trimIndent()
        )
        val snap = RepoMetaReader.read(root)
        assertNotNull(snap)
        assertEquals("America/New_York", snap!!.defaultTzId)
    }

    @Test fun defaultTzIdAbsentReturnsNull() {
        val root = writeRepoToml(
            """
            id = "0190a0aa-1c1d-7000-8a0a-deadbeef0002"
            name = "kept-life"
            """.trimIndent()
        )
        val snap = RepoMetaReader.read(root)
        assertNotNull(snap)
        assertNull(snap!!.defaultTzId)
    }

    @Test fun missingRepoTomlReturnsNull() {
        val root = tmp.newFolder("empty-repo")
        // Note: no `.strictlykeptboy/repo.toml` written.
        val snap = RepoMetaReader.read(root)
        assertNull(snap)
    }

    @Test fun blankDefaultTzIdNormalisesToNull() {
        val root = writeRepoToml(
            """
            id = "0190a0aa-1c1d-7000-8a0a-deadbeef0003"
            default_tz_id = ""
            """.trimIndent()
        )
        val snap = RepoMetaReader.read(root)
        assertNotNull(snap)
        assertNull(snap!!.defaultTzId)
    }
}
