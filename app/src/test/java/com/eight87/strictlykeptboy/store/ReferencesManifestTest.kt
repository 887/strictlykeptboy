package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReferencesManifestTest {

    @get:Rule val tmp = TemporaryFolder()

    private val thisRepo = "01900000-0000-7000-8000-aaaaaaaaaaaa"
    private val otherA = "01900000-0000-7000-8000-bbbbbbbbbbbb"
    private val otherB = "01900000-0000-7000-8000-cccccccccccc"

    @Test fun missing_file_returns_empty_manifest() {
        val m = ReferencesManifest.read(tmp.root.toPath())
        assertEquals(0, m.entries.size)
    }

    @Test fun round_trip_single_entry() {
        val original = ReferencesManifest.Manifest(
            entries = listOf(
                ReferencesManifest.Entry(
                    repoId = otherA,
                    displayName = "Master's schedule",
                    urls = listOf("git@host:o/r.git"),
                    required = true,
                    writeBackTarget = "abcdef0123456789",
                ),
            ),
        )
        ReferencesManifest.write(tmp.root.toPath(), original)
        val read = ReferencesManifest.read(tmp.root.toPath())
        assertEquals(1, read.entries.size)
        val e = read.entries.single()
        assertEquals(otherA, e.repoId)
        assertEquals("Master's schedule", e.displayName)
        assertEquals(listOf("git@host:o/r.git"), e.urls)
        assertTrue(e.required)
        assertEquals("abcdef0123456789", e.writeBackTarget)
    }

    @Test fun round_trip_multi_remotes_array_form() {
        val original = ReferencesManifest.Manifest(
            entries = listOf(
                ReferencesManifest.Entry(
                    repoId = otherA,
                    displayName = "Multi",
                    urls = listOf("git@host:o/r.git", "https://host/o/r.git"),
                ),
            ),
        )
        ReferencesManifest.write(tmp.root.toPath(), original)
        val read = ReferencesManifest.read(tmp.root.toPath())
        assertEquals(2, read.entries.single().urls.size)
    }

    @Test fun round_trip_credential_hint() {
        val original = ReferencesManifest.Manifest(
            entries = listOf(
                ReferencesManifest.Entry(
                    repoId = otherA,
                    displayName = "x",
                    urls = listOf("g@h:o/r"),
                    credentialHint = ReferencesManifest.CredentialHint(
                        kind = "ssh-key",
                        fingerprint = "abc123",
                    ),
                ),
            ),
        )
        ReferencesManifest.write(tmp.root.toPath(), original)
        val read = ReferencesManifest.read(tmp.root.toPath())
        val h = read.entries.single().credentialHint
        assertNotNull(h)
        assertEquals("ssh-key", h!!.kind)
        assertEquals("abc123", h.fingerprint)
    }

    @Test fun add_or_replace_adds_new_entry() {
        val empty = ReferencesManifest.Manifest()
        val newEntry = ReferencesManifest.Entry(otherA, "x", listOf("g@h:o/r"))
        val result = ReferencesManifest.addOrReplace(empty, newEntry, thisRepo)
        assertTrue(result is ReferencesManifest.AddResult.Added)
    }

    @Test fun add_or_replace_replaces_on_dup_repo_id() {
        val first = ReferencesManifest.Entry(otherA, "old", listOf("g@h:o/r"))
        val second = ReferencesManifest.Entry(otherA, "NEW", listOf("g@h:o/r"))
        val m = ReferencesManifest.Manifest(entries = listOf(first))
        val result = ReferencesManifest.addOrReplace(m, second, thisRepo)
        assertTrue(result is ReferencesManifest.AddResult.Replaced)
        val newManifest = (result as ReferencesManifest.AddResult.Replaced).manifest
        assertEquals(1, newManifest.entries.size)
        assertEquals("NEW", newManifest.entries.single().displayName)
    }

    @Test fun add_or_replace_rejects_self_reference() {
        val result = ReferencesManifest.addOrReplace(
            ReferencesManifest.Manifest(),
            ReferencesManifest.Entry(thisRepo, "self", listOf("g@h:o/r")),
            thisRepo,
        )
        assertTrue(result is ReferencesManifest.AddResult.CyclicRejected)
    }

    @Test fun add_or_replace_rejects_cycle() {
        // Graph: otherA -> otherB -> thisRepo. Adding thisRepo -> otherA closes the cycle.
        val graph = mapOf(
            otherA to setOf(otherB),
            otherB to setOf(thisRepo),
        )
        val result = ReferencesManifest.addOrReplace(
            ReferencesManifest.Manifest(),
            ReferencesManifest.Entry(otherA, "x", listOf("g@h:o/r")),
            thisRepo,
            referenceGraph = graph,
        )
        assertTrue("got $result", result is ReferencesManifest.AddResult.CyclicRejected)
    }

    @Test fun add_or_replace_allows_non_cyclic_in_graph() {
        // Graph: otherA -> otherB (no path back to thisRepo).
        val graph = mapOf(otherA to setOf(otherB))
        val result = ReferencesManifest.addOrReplace(
            ReferencesManifest.Manifest(),
            ReferencesManifest.Entry(otherA, "x", listOf("g@h:o/r")),
            thisRepo,
            referenceGraph = graph,
        )
        assertTrue("got $result", result is ReferencesManifest.AddResult.Added)
    }

    @Test fun remove_drops_entry_by_repo_id() {
        val m = ReferencesManifest.Manifest(
            entries = listOf(
                ReferencesManifest.Entry(otherA, "a", listOf("u1")),
                ReferencesManifest.Entry(otherB, "b", listOf("u2")),
            ),
        )
        val m2 = ReferencesManifest.remove(m, otherA)
        assertEquals(1, m2.entries.size)
        assertEquals(otherB, m2.entries.single().repoId)
    }

    @Test fun validate_no_cycle_returns_null_when_clean() {
        assertNull(ReferencesManifest.validateNoCycle(thisRepo, otherA, emptyMap()))
    }
}
