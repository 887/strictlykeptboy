package com.eight87.strictlykeptboy.store

import com.eight87.strictlykeptboy.git.Uuid7
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase FFF / EC-C.7 — single-template materialization engine.
 *
 * Sibling to [RoutineMaterializer]; this one materializes ONE picked
 * atomic-activity template into a single [Event] at the chosen start
 * time. Sub-beats are copied verbatim onto the Event's `subbeats`
 * field (DM-M / AT-I.1).
 *
 * Pure (SOLID-S/SOLID-D): no Android, no JGit.
 */
object TemplateMaterializer {

    fun materialize(
        template: AtomicTemplate,
        start: OffsetDateTime,
        calendarId: String,
        author: String,
        titleOverride: String? = null,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): Event {
        val entry = template.entries.firstOrNull()
            ?: error("template ${template.templateId} has no entries")
        return materializeEntry(template, entry, start, calendarId, author, titleOverride, now)
    }

    fun materializeEntry(
        template: AtomicTemplate,
        entry: AtomicTemplateEntry,
        start: OffsetDateTime,
        calendarId: String,
        author: String,
        titleOverride: String? = null,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): Event {
        val end = start.plusMinutes(entry.durationMinutes.toLong())
        val id = Uuid7.generate().toString()
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now)
        return Event(
            header = EntityHeader(
                schemaVersion = 1,
                id = id,
                createdAt = iso,
                updatedAt = iso,
                author = author,
            ),
            title = titleOverride?.trim()?.takeIf { it.isNotEmpty() } ?: entry.title,
            start = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(start),
            end = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(end),
            calendarId = calendarId,
            tags = entry.tags,
            emoji = null,
            materializedFrom = template.templateId,
            materializedAt = iso,
            subbeats = entry.subbeats,
        )
    }
}
