package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.store.AtomicTemplateSubbeat
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.git.Uuid7
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase FFF / EC-B.1 — in-memory draft model for the free-form event
 * creator. Plain data class; pure (no Android, no IO).
 */
data class EventDraft(
    val title: String = "",
    val start: OffsetDateTime = OffsetDateTime.now().plusMinutes(15),
    val end: OffsetDateTime = OffsetDateTime.now().plusMinutes(45),
    val calendarId: String = "",
    val notes: String = "",
    val identity: String? = null,
    val reminder: String? = null,
    val private: Boolean = false,
    val recurrence: RecurrencePreset = RecurrencePreset.Once,
    val customRRule: String = "",
    /**
     * Phase 2.1.D.7 — when a draft was opened from a long-press
     * "Schedule as timebox" on a task, this carries the source task's
     * id so [EventCreateController] can write the reciprocal link back
     * into [com.eight87.strictlykeptboy.ui.tasks.TasksViewState] on
     * commit. Empty = unlinked.
     */
    val relatedTaskId: String = "",
)

enum class RecurrencePreset { Once, Daily, Weekly, Monthly, Custom }

data class DraftErrors(
    val titleEmpty: Boolean = false,
    val endNotAfterStart: Boolean = false,
    val durationTooLong: Boolean = false,
    val calendarMissing: Boolean = false,
    val customRRuleBlank: Boolean = false,
) {
    val any: Boolean
        get() = titleEmpty || endNotAfterStart || durationTooLong ||
            calendarMissing || customRRuleBlank
}

object EventDraftValidator {
    /** EC-B.2 — title non-empty, end > start, duration ≤ 24h. */
    fun validate(d: EventDraft): DraftErrors = DraftErrors(
        titleEmpty = d.title.trim().isEmpty(),
        endNotAfterStart = !d.end.isAfter(d.start),
        durationTooLong = java.time.Duration.between(d.start, d.end).toHours() > 24,
        calendarMissing = d.calendarId.isBlank(),
        customRRuleBlank = d.recurrence == RecurrencePreset.Custom && d.customRRule.trim().isEmpty(),
    )
}

object DraftToEvent {
    /** Mint an [Event] from the [draft]. Caller wraps with EntityWriter + commit. */
    fun mint(
        draft: EventDraft,
        author: String,
        now: OffsetDateTime = OffsetDateTime.now(),
        subbeats: List<AtomicTemplateSubbeat> = emptyList(),
        overrideId: String? = null,
    ): Event {
        val id = overrideId ?: Uuid7.generate().toString()
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now)
        return Event(
            header = EntityHeader(
                schemaVersion = 1,
                id = id,
                createdAt = iso,
                updatedAt = iso,
                author = author,
            ),
            title = draft.title.trim(),
            start = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(draft.start),
            end = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(draft.end),
            calendarId = draft.calendarId,
            private = draft.private,
            body = draft.notes,
            subbeats = subbeats,
        )
    }
}

/**
 * EC-E.1 — overlap detection. Pure: caller hands the candidate
 * `[start, end)` plus existing events. Returns the first overlap.
 */
data class OverlapHit(val otherTitle: String, val otherStart: OffsetDateTime)

object OverlapDetector {
    fun firstOverlap(
        start: OffsetDateTime,
        end: OffsetDateTime,
        existing: List<Triple<String, OffsetDateTime, OffsetDateTime>>,
    ): OverlapHit? = existing.firstOrNull { (_, eStart, eEnd) ->
        start.isBefore(eEnd) && eStart.isBefore(end)
    }?.let { OverlapHit(it.first, it.second) }
}
