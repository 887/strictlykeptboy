package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate

/**
 * Round 2.27 / Phase D.4 — verifies the prompt-response writer emits
 * the D-2.27.g shape, round-trips through [PromptResponseReader], and
 * is idempotent on same-date overwrite.
 */
class PromptResponseWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val calId = "kinky-rituals"
    private val ruleId = "0190d0a0-7fab-7c50-9c1e-c100000000a1"
    private val date = LocalDate.of(2026, 5, 17)
    private val boyId = "01900000-0000-7000-8000-0000000000b0"

    @Test fun writes_file_and_reader_sees_date() {
        val root = tmp.root.toPath()
        val written = PromptResponseWriter.write(
            repoRoot = root,
            calId = calId,
            ruleId = ruleId,
            instanceDate = date,
            responderId = boyId,
            body = "sent. all good Sir.",
            nowIso = "2026-05-17T11:14:00Z",
        )
        assertTrue(Files.exists(written))
        val text = String(Files.readAllBytes(written), StandardCharsets.UTF_8)
        assertTrue("frontmatter has kind", text.contains("kind = \"prompt_response\""))
        assertTrue("frontmatter has prompt_id", text.contains("prompt_id = \"$ruleId\""))
        assertTrue("frontmatter has prompt_instance_date", text.contains("prompt_instance_date = 2026-05-17"))
        assertTrue("frontmatter has responder", text.contains("responder = \"$boyId\""))
        assertTrue("frontmatter has author", text.contains("author = \"$boyId\""))
        assertTrue("frontmatter has updated_at", text.contains("updated_at"))
        assertTrue("body emitted", text.contains("sent. all good Sir."))

        val answered = PromptResponseReader.listAnsweredInstances(root, calId, ruleId)
        assertEquals(setOf(date), answered)
    }

    @Test fun overwrite_same_date_is_idempotent() {
        val root = tmp.root.toPath()
        PromptResponseWriter.write(
            repoRoot = root, calId = calId, ruleId = ruleId,
            instanceDate = date, responderId = boyId,
            body = "first", nowIso = "2026-05-17T11:00:00Z",
        )
        val second = PromptResponseWriter.write(
            repoRoot = root, calId = calId, ruleId = ruleId,
            instanceDate = date, responderId = boyId,
            body = "second", nowIso = "2026-05-17T12:00:00Z",
        )
        val dir = root.resolve("calendars/$calId/cage-check-responses/$ruleId")
        val files = Files.list(dir).use { it.toList() }
        assertEquals("exactly one file after overwrite", 1, files.size)
        val text = String(Files.readAllBytes(second), StandardCharsets.UTF_8)
        assertTrue("body is from second write", text.contains("second"))
        assertTrue("body from first is gone", !text.contains("first"))
    }

    @Test fun writes_attachment_when_provided() {
        val root = tmp.root.toPath()
        val target = PromptResponseWriter.write(
            repoRoot = root, calId = calId, ruleId = ruleId,
            instanceDate = date, responderId = boyId,
            body = "",
            attachment = "attachments/cage-2026-05-17.jpg",
            nowIso = "2026-05-17T11:14:00Z",
        )
        val text = String(Files.readAllBytes(target), StandardCharsets.UTF_8)
        assertTrue(text.contains("attachment = \"attachments/cage-2026-05-17.jpg\""))
    }

    @Test fun stable_id_is_deterministic() {
        val a = PromptResponseWriter.stableUuid(date, ruleId)
        val b = PromptResponseWriter.stableUuid(date, ruleId)
        assertEquals(a, b)
        // UUID-shape sanity.
        assertNotNull(java.util.UUID.fromString(a))
        // Version = 7 nibble at index 14 of the bare UUID.
        assertEquals('7', a[14])
    }

    @Test fun empty_dir_returns_empty_set() {
        val root = tmp.root.toPath()
        val out = PromptResponseReader.listAnsweredInstances(root, calId, ruleId)
        assertTrue(out.isEmpty())
    }
}
