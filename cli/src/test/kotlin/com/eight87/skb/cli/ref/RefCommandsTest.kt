package com.eight87.skb.cli.ref

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefCommandsTest {

    @Test fun upsert_into_empty_manifest_adds_block() {
        val empty = emptyManifestText()
        val out = upsertEntry(
            empty,
            repoId = "01900000-0000-7000-8000-000000000001",
            displayName = "Master",
            urls = listOf("git@host:o/r.git"),
            required = false,
            writeBackTarget = null,
        )
        assertTrue(out.contains("[[reference]]"))
        assertTrue(out.contains("repo_id = \"01900000-0000-7000-8000-000000000001\""))
        assertTrue(out.contains("url = \"git@host:o/r.git\""))
        assertFalse(out.contains("required = true"))
    }

    @Test fun upsert_with_multi_remotes_emits_array_form() {
        val out = upsertEntry(
            emptyManifestText(),
            repoId = "01900000-0000-7000-8000-000000000002",
            displayName = "Multi",
            urls = listOf("git@host:o/r.git", "https://host/o/r.git"),
            required = true,
            writeBackTarget = "abcdef0123456789",
        )
        assertTrue(out.contains("remotes = [\"git@host:o/r.git\", \"https://host/o/r.git\"]"))
        assertTrue(out.contains("required = true"))
        assertTrue(out.contains("write_back_target = \"abcdef0123456789\""))
    }

    @Test fun upsert_replaces_existing_block_with_same_repo_id() {
        val firstPass = upsertEntry(emptyManifestText(), "ID-A", "Old", listOf("u1"), false, null)
        val secondPass = upsertEntry(firstPass, "ID-A", "NEW", listOf("u2"), true, null)
        val entries = parseEntries(secondPass)
        assertEquals(1, entries.size)
        assertEquals("NEW", entries.single().displayName)
        assertEquals(listOf("u2"), entries.single().urls)
        assertTrue(entries.single().required)
    }

    @Test fun remove_drops_only_the_matching_block() {
        val s1 = upsertEntry(emptyManifestText(), "ID-A", "A", listOf("u1"), false, null)
        val s2 = upsertEntry(s1, "ID-B", "B", listOf("u2"), false, null)
        val (out, removed) = removeEntry(s2, "ID-A")
        assertTrue(removed)
        val entries = parseEntries(out)
        assertEquals(1, entries.size)
        assertEquals("ID-B", entries.single().repoId)
    }

    @Test fun remove_returns_false_when_nothing_to_remove() {
        val (_, removed) = removeEntry(emptyManifestText(), "ID-A")
        assertFalse(removed)
    }

    @Test fun parse_entries_handles_array_remotes_and_required_flag() {
        val text = buildString {
            append("+++\nschema_version = 1\n+++\n\n")
            append("[[reference]]\n")
            append("repo_id = \"ID-X\"\n")
            append("display_name = \"Xthing\"\n")
            append("remotes = [\"a\", \"b\"]\n")
            append("required = true\n")
        }
        val es = parseEntries(text)
        assertEquals(1, es.size)
        val e = es.single()
        assertEquals("ID-X", e.repoId)
        assertEquals(listOf("a", "b"), e.urls)
        assertTrue(e.required)
    }

    @Test fun round_trip_preserves_repo_id_through_remove_and_readd() {
        val original = upsertEntry(emptyManifestText(), "ID-Z", "Z", listOf("u"), false, null)
        val (afterRemove, _) = removeEntry(original, "ID-Z")
        val replayed = upsertEntry(afterRemove, "ID-Z", "Z", listOf("u"), false, null)
        assertEquals(parseEntries(original).single().repoId, parseEntries(replayed).single().repoId)
    }
}
