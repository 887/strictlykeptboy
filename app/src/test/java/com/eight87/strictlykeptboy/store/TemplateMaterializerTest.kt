package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Phase FFF / EC-G.3 — single-template materializer test.
 *
 * Verifies frontmatter copy, sub-beat preservation, audit fields
 * (`materialized_from`, `materialized_at`), UUIDv7-shaped id, and that
 * two materializations of the same template mint distinct ids.
 */
class TemplateMaterializerTest {

    private val now = OffsetDateTime.parse("2026-05-13T08:00:00+02:00")
    private val tpl = AtomicTemplate(
        schemaVersion = 1,
        templateId = "atomic-brush",
        displayName = "Brush teeth",
        category = "self-care",
        neutralSafe = true,
        entries = listOf(
            AtomicTemplateEntry(
                id = "brush",
                title = "Brush teeth",
                durationMinutes = 5,
                stickerId = "tooth",
                tags = listOf("hygiene"),
                subbeats = listOf(
                    AtomicTemplateSubbeat("upper", 30, "u"),
                    AtomicTemplateSubbeat("lower", 30, "l"),
                ),
            ),
        ),
    )

    @Test fun materialize_writes_audit_fields() {
        val ev = TemplateMaterializer.materialize(
            template = tpl,
            start = now,
            calendarId = "cal",
            author = "me",
            now = now,
        )
        assertEquals("atomic-brush", ev.materializedFrom)
        assertNotNull(ev.materializedAt)
        assertEquals("Brush teeth", ev.title)
        assertEquals("cal", ev.calendarId)
        assertEquals(2, ev.subbeats.size)
        assertEquals("upper", ev.subbeats[0].label)
    }

    @Test fun materialize_end_equals_start_plus_duration() {
        val ev = TemplateMaterializer.materialize(tpl, now, "cal", "me", now = now)
        val start = OffsetDateTime.parse(ev.start)
        val end = OffsetDateTime.parse(ev.end)
        assertEquals(5L, java.time.Duration.between(start, end).toMinutes())
    }

    @Test fun materialize_mints_distinct_ids() {
        val a = TemplateMaterializer.materialize(tpl, now, "cal", "me", now = now)
        val b = TemplateMaterializer.materialize(tpl, now, "cal", "me", now = now)
        assertNotEquals(a.id, b.id)
    }

    @Test fun materialize_title_override_wins() {
        val ev = TemplateMaterializer.materialize(
            template = tpl, start = now, calendarId = "cal", author = "me",
            titleOverride = "Custom title", now = now,
        )
        assertEquals("Custom title", ev.title)
    }

    @Test fun materialize_id_is_uuid_shaped() {
        val ev = TemplateMaterializer.materialize(tpl, now, "cal", "me", now = now)
        // UUID format: 8-4-4-4-12 hex digits
        val uuidRe = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue("id $${ev.id} not UUIDv7-shaped", uuidRe.matches(ev.id))
    }
}
