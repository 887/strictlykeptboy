package com.eight87.strictlykeptboy.cache

import com.eight87.strictlykeptboy.cache.entities.DeviationRow
import com.eight87.strictlykeptboy.cache.entities.EventFtsRow
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.cache.entities.ExceptionRow
import com.eight87.strictlykeptboy.cache.entities.IdentityRow
import com.eight87.strictlykeptboy.cache.entities.JournalEntryRow
import com.eight87.strictlykeptboy.cache.entities.OverrideRow
import com.eight87.strictlykeptboy.cache.entities.RecurrenceRuleRow
import com.eight87.strictlykeptboy.cache.entities.StandingTaskRow
import com.eight87.strictlykeptboy.cache.entities.TaskFtsRow
import com.eight87.strictlykeptboy.cache.entities.TaskRow
import com.eight87.strictlykeptboy.store.Deviation
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception as StoreException
import com.eight87.strictlykeptboy.store.Identity
import com.eight87.strictlykeptboy.store.JournalEntry
import com.eight87.strictlykeptboy.store.Override
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.StandingTask
import com.eight87.strictlykeptboy.store.Task
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Translation: file-typed entity ↔ Room row.
 *
 * Timestamps in file format are RFC-3339 strings (possibly date-only).
 * The cache stores epoch-millis for sortability + range queries.
 * Date-only inputs are anchored to UTC midnight to stay deterministic;
 * the resolver (Phase E) is responsible for tz arithmetic when rendering.
 */
internal object EntityMapping {

    private val json = Json { encodeDefaults = false }

    fun event(
        repoId: String,
        e: Event,
        sourcePath: String,
        reanchorAtLocalTz: Boolean = false,
    ): EventRow {
        // Per-event `pin_timezone = true` overrides the repo-level
        // re-anchor so travel/convention events stay in their stored
        // zone (e.g. devconf-berlin must render at Europe/Berlin
        // 09:00 regardless of where the device is).
        val reanchor = reanchorAtLocalTz && !e.pinTimezone
        return EventRow(
        repoId = repoId,
        id = e.id,
        calendarId = e.calendarId,
        startEpochMs = parseEpochMs(e.start, reanchor),
        endEpochMs = parseEpochMs(e.end, reanchor),
        allDay = e.allDay,
        title = e.title,
        body = e.body,
        tagsJson = encodeTags(e.tags),
        location = e.location,
        emoji = e.emoji,
        busy = e.busy,
        priorityOverride = e.priorityOverride,
        externalUid = e.externalUid,
        privateFlag = e.private,
        sourcePath = sourcePath,
        groupLabel = e.group,
        requiresResponse = e.requiresResponse,
        promptKindRaw = e.promptKind?.tomlValue,
        promptTargetRaw = e.promptTarget?.tomlValue,
        )
    }

    fun task(repoId: String, t: Task, sourcePath: String): TaskRow = TaskRow(
        repoId = repoId,
        id = t.id,
        todolistId = t.todolistId,
        title = t.title,
        body = t.body,
        dueEpochMs = t.due?.let { parseEpochMs(it) },
        done = t.done,
        doneAtEpochMs = t.doneAt?.let { parseEpochMs(it) },
        priority = t.priority,
        tagsJson = encodeTags(t.tags),
        sourcePath = sourcePath,
    )

    fun standingTask(repoId: String, t: StandingTask, sourcePath: String): StandingTaskRow =
        StandingTaskRow(
            repoId = repoId,
            id = t.id,
            todolistId = t.todolistId,
            title = t.title,
            body = t.body,
            done = t.done,
            pinned = t.pinned,
            priority = t.priority,
            tagsJson = encodeTags(t.tags),
            sourcePath = sourcePath,
        )

    fun recurrenceRule(
        repoId: String,
        r: RecurrenceRule,
        sourcePath: String,
        @Suppress("UNUSED_PARAMETER") reanchorAtLocalTz: Boolean = false,
    ): RecurrenceRuleRow {
        // Recurrence rules are inherently tied to their authored
        // zone — overriding `tz_id` at index time was attempted in
        // an earlier revision and made all rule-derived bands vanish
        // on the AVD (the dmfs lib-recur iterator + the resolver's
        // per-day bucketing got out of sync). Per-event re-anchoring
        // on one-off events is still applied above; recurrences
        // continue to materialize in their stored zone.
        return RecurrenceRuleRow(
            repoId = repoId,
            id = r.id,
            calendarId = r.calendarId,
            title = r.title,
            rrule = r.rrule,
            dtstart = r.dtstart,
            duration = r.duration,
            tzId = r.tzId,
            location = r.location,
            emoji = r.emoji,
            busy = r.busy,
            active = r.active,
            tagsJson = encodeTags(r.tags),
            body = r.body,
            sourcePath = sourcePath,
            groupLabel = r.group,
            requiresResponse = r.requiresResponse,
            promptKindRaw = r.promptKind?.tomlValue,
            promptTargetRaw = r.promptTarget?.tomlValue,
        )
    }

    fun exception(repoId: String, x: StoreException, sourcePath: String): ExceptionRow =
        ExceptionRow(
            repoId = repoId,
            id = x.id,
            ruleId = x.ruleId,
            calendarId = x.calendarId,
            instanceDate = x.instanceDate,
            mode = x.mode,
            overrideStart = x.overrideStart,
            overrideEnd = x.overrideEnd,
            overrideTitle = x.overrideTitle,
            overrideLocation = x.overrideLocation,
            body = x.body,
            sourcePath = sourcePath,
        )

    fun deviation(repoId: String, d: Deviation, sourcePath: String): DeviationRow = DeviationRow(
        repoId = repoId,
        id = d.id,
        targetId = d.targetId,
        instanceDate = d.instanceDate,
        devKind = d.devKind,
        atEpochMs = parseEpochMs(d.at),
        note = d.note,
        subbeatsCompletedJson = encodeTags(d.subbeatsCompleted),
        body = d.body,
        sourcePath = sourcePath,
    )

    fun override(repoId: String, o: Override, sourcePath: String): OverrideRow = OverrideRow(
        repoId = repoId,
        id = o.id,
        supersededCalendarId = o.supersededCalendarId,
        eventId = o.eventId,
        instanceDate = o.instanceDate,
        overrideKind = o.overrideKind,
        rangeFrom = o.rangeFrom,
        rangeTo = o.rangeTo,
        body = o.body,
        sourcePath = sourcePath,
    )

    fun journal(repoId: String, j: JournalEntry, sourcePath: String): JournalEntryRow =
        JournalEntryRow(
            repoId = repoId,
            id = j.id,
            day = j.day,
            dayEpochMs = parseEpochMs(j.day),
            sequence = j.sequence,
            body = j.body,
            sourcePath = sourcePath,
        )

    fun identity(repoId: String, i: Identity, sourcePath: String): IdentityRow = IdentityRow(
        repoId = repoId,
        id = i.id,
        displayName = i.displayName,
        email = i.email,
        avatar = i.avatar,
        defaultAuthor = i.defaultAuthor,
        pronouns = i.pronouns,
        body = i.body,
        sourcePath = sourcePath,
    )

    fun eventFts(repoId: String, e: Event): EventFtsRow =
        EventFtsRow(repoId = repoId, eventId = e.id, title = e.title, body = e.body)

    fun taskFts(repoId: String, t: Task): TaskFtsRow =
        TaskFtsRow(repoId = repoId, taskId = t.id, title = t.title, body = t.body)

    fun taskFts(repoId: String, t: StandingTask): TaskFtsRow =
        TaskFtsRow(repoId = repoId, taskId = t.id, title = t.title, body = t.body)

    private fun encodeTags(items: List<String>): String =
        if (items.isEmpty()) "[]" else json.encodeToString(items)

    fun parseEpochMs(s: String, reanchorAtLocalTz: Boolean = false): Long {
        if (s.isEmpty()) return 0L
        val deviceZone = ZoneId.systemDefault()
        // Try offset datetime (`2026-05-09T18:30:00+02:00`) first. When
        // re-anchoring, drop the stored offset and treat the wall-clock
        // as device-local (so 23:30+01:00 becomes 23:30 in whatever zone
        // the device is in — same wall-clock, different absolute
        // instant).
        return try {
            val odt = OffsetDateTime.parse(s)
            if (reanchorAtLocalTz) {
                odt.toLocalDateTime().atZone(deviceZone).toInstant().toEpochMilli()
            } else {
                odt.toInstant().toEpochMilli()
            }
        } catch (_: Throwable) {
            try {
                val ldt = LocalDateTime.parse(s)
                val anchor = if (reanchorAtLocalTz) deviceZone else ZoneOffset.UTC
                ldt.atZone(anchor).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                try {
                    val ld = LocalDate.parse(s)
                    val anchor = if (reanchorAtLocalTz) deviceZone else ZoneOffset.UTC
                    ld.atStartOfDay(anchor).toInstant().toEpochMilli()
                } catch (_: Throwable) {
                    0L
                }
            }
        }
    }
}
