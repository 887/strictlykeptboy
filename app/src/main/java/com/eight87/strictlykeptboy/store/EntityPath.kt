package com.eight87.strictlykeptboy.store

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Filename + bucketing builder per DM-C.
 *
 * All paths are repo-root-relative. The bucket dirs ARE the index —
 * we never emit `events.json` or any other materialised index file
 * (per D.3 / CLAUDE.md "never create an index file").
 */
object EntityPath {

    /** `<yyyy>` and `<mm>` are derived from the entity's defining moment, NOT its create time. */
    fun event(calendarId: String, entityId: String, startDate: BucketDate): Path =
        rel("calendars", calendarId, "events", startDate.yyyy, startDate.mm, "$entityId.md")

    fun recurrenceRule(calendarId: String, ruleId: String): Path =
        rel("calendars", calendarId, "recurrences", "$ruleId.md")

    /** Exceptions are bucketed by `<rule-id>/<yyyy-mm-dd>.md` per DM-B.3. */
    fun exception(calendarId: String, ruleId: String, instanceDate: String): Path =
        rel("calendars", calendarId, "exceptions", ruleId, "$instanceDate.md")

    /** Deviations parallel exceptions in shape but live at the calendar root (DM-M.1). */
    fun deviation(calendarId: String, targetId: String, instanceDate: String): Path =
        rel("calendars", calendarId, "deviations", targetId, "$instanceDate.md")

    /** Overrides (force-show) parallel exceptions in shape (DM-AA.3). */
    fun override(supersededCalendarId: String, eventId: String, instanceDate: String): Path =
        rel("overrides", supersededCalendarId, eventId, "$instanceDate.md")

    fun datedTask(todolistId: String, entityId: String, dueDate: BucketDate): Path =
        rel("todolists", todolistId, "tasks", dueDate.yyyy, dueDate.mm, "$entityId.md")

    fun standingTask(todolistId: String, entityId: String): Path =
        rel("todolists", todolistId, "standing", "$entityId.md")

    fun taskRecurrence(todolistId: String, ruleId: String): Path =
        rel("todolists", todolistId, "recurrences", "$ruleId.md")

    fun identity(entityId: String): Path = rel("identities", "$entityId.md")

    fun calendarMeta(calendarId: String): Path = rel("calendars", calendarId, "calendar.toml")
    fun todolistMeta(todolistId: String): Path = rel("todolists", todolistId, "todolist.toml")
    fun repoMeta(): Path = rel(".strictlykeptboy", "repo.toml")
    fun schemaMeta(): Path = rel(".strictlykeptboy", "schema.toml")

    fun journal(day: String, sequence: Int = 0): Path =
        if (sequence == 0) rel("journal", "$day.md") else rel("journal", "$day-$sequence.md")

    private fun rel(vararg parts: String): Path = Paths.get(parts.first(), *parts.drop(1).toTypedArray())

    /** A year + month bucket. Use [forIsoDateTime] for the typical RFC-3339 inputs. */
    data class BucketDate(val yyyy: String, val mm: String) {
        companion object {
            /** Extracts year + month from a string starting with `YYYY-MM-...`. Does no tz arithmetic. */
            fun forIsoDateTime(s: String): BucketDate {
                require(s.length >= 7 && s[4] == '-') {
                    "expected ISO 8601 date-time prefix, got $s"
                }
                return BucketDate(s.substring(0, 4), s.substring(5, 7))
            }
        }
    }
}
