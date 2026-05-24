package com.eight87.strictlykeptboy.cache.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Phase D.1 — Room entity mirrors for typed file entities.
 *
 * Source-of-truth rule: every row here is derivable from a file on
 * disk. Rows MAY be wiped + rebuilt at any time. FKs across rows are
 * NOT enforced by Room — the indexer can insert in any order during
 * batch operations and we don't want partial-state aborts (see D.8).
 *
 * `sourcePath` carries the repo-relative POSIX path that produced the
 * row; the incremental indexer uses it for `path → row` lookup on
 * file deletion.
 *
 * `tags` are stored as JSON-encoded `List<String>` (per the task spec)
 * to keep rows flat. FTS indices cover textual fields per D.13;
 * Android Room currently exposes `@Fts4` (not Fts5) so we use Fts4
 * with the `unicode61` tokenizer.
 */

@Entity(tableName = "events", primaryKeys = ["repoId", "id"])
data class EventRow(
    val repoId: String,
    val id: String,
    val calendarId: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val allDay: Boolean,
    val title: String,
    val body: String,
    val tagsJson: String,
    val location: String?,
    val emoji: String?,
    val busy: Boolean,
    val priorityOverride: Int?,
    val externalUid: String?,
    val privateFlag: Boolean,
    val sourcePath: String,
    /** Round 2.21.A.3 — atomic-event grouping label. */
    val groupLabel: String? = null,
    /** Round 2.27 / D-2.27.a — keeper-prompt flag. */
    val requiresResponse: Boolean = false,
    /** Round 2.27 / D-2.27.a — prompt-kind TOML string (`photo` / `text` / `check-in`). */
    val promptKindRaw: String? = null,
    /** Round 2.27 / D-2.27.a — prompt-target TOML string (`keeper` / `self`). */
    val promptTargetRaw: String? = null,
)

@Entity(tableName = "tasks", primaryKeys = ["repoId", "id"])
data class TaskRow(
    val repoId: String,
    val id: String,
    val todolistId: String,
    val title: String,
    val body: String,
    val dueEpochMs: Long?,
    val done: Boolean,
    val doneAtEpochMs: Long?,
    val priority: Int?,
    val tagsJson: String,
    val sourcePath: String,
)

@Entity(tableName = "standing_tasks", primaryKeys = ["repoId", "id"])
data class StandingTaskRow(
    val repoId: String,
    val id: String,
    val todolistId: String,
    val title: String,
    val body: String,
    val done: Boolean,
    val pinned: Boolean,
    val priority: Int?,
    val tagsJson: String,
    val sourcePath: String,
)

@Entity(tableName = "recurrence_rules", primaryKeys = ["repoId", "id"])
data class RecurrenceRuleRow(
    val repoId: String,
    val id: String,
    val calendarId: String,
    val title: String,
    val rrule: String,
    val dtstart: String,
    val duration: String,
    val tzId: String,
    val location: String?,
    val emoji: String?,
    val busy: Boolean,
    val active: Boolean,
    val tagsJson: String,
    val body: String,
    val sourcePath: String,
    /** Round 2.21.A.3 — atomic-event grouping label. */
    val groupLabel: String? = null,
    /** Round 2.27 / D-2.27.a — keeper-prompt flag on the rule. */
    val requiresResponse: Boolean = false,
    /** Round 2.27 / D-2.27.a — prompt-kind TOML string. */
    val promptKindRaw: String? = null,
    /** Round 2.27 / D-2.27.a — prompt-target TOML string. */
    val promptTargetRaw: String? = null,
    /**
     * Round 2026-05-24 — per-rule opt-out from the repo-level
     * `adjustToLocalTimezone` re-anchor. Same semantic as
     * `Event.pinTimezone`; persisted so the resolver can decide
     * whether to materialize the rule in its stored `tzId` or in
     * the device's local zone.
     */
    val pinTimezone: Boolean = false,
)

@Entity(tableName = "exceptions", primaryKeys = ["repoId", "ruleId", "instanceDate"])
data class ExceptionRow(
    val repoId: String,
    val id: String,
    val ruleId: String,
    val calendarId: String,
    val instanceDate: String,
    val mode: String,
    val overrideStart: String?,
    val overrideEnd: String?,
    val overrideTitle: String?,
    val overrideLocation: String?,
    val body: String,
    val sourcePath: String,
)

@Entity(tableName = "deviations", primaryKeys = ["repoId", "id"])
data class DeviationRow(
    val repoId: String,
    val id: String,
    val targetId: String,
    val instanceDate: String,
    val devKind: String,
    val atEpochMs: Long,
    val note: String?,
    val subbeatsCompletedJson: String,
    val body: String,
    val sourcePath: String,
)

@Entity(tableName = "overrides", primaryKeys = ["repoId", "id"])
data class OverrideRow(
    val repoId: String,
    val id: String,
    val supersededCalendarId: String,
    val eventId: String,
    val instanceDate: String,
    val overrideKind: String,
    val rangeFrom: String?,
    val rangeTo: String?,
    val body: String,
    val sourcePath: String,
)

@Entity(tableName = "journal_entries", primaryKeys = ["repoId", "id"])
data class JournalEntryRow(
    val repoId: String,
    val id: String,
    val day: String,
    val dayEpochMs: Long,
    val sequence: Int,
    val body: String,
    val sourcePath: String,
)

@Entity(tableName = "identities", primaryKeys = ["repoId", "id"])
data class IdentityRow(
    val repoId: String,
    val id: String,
    val displayName: String,
    val email: String?,
    val avatar: String?,
    val defaultAuthor: Boolean,
    val pronouns: String?,
    val body: String,
    val sourcePath: String,
)

/** Per-repo cache state. `lastIndexedHeadSha` is the HEX of the indexed git HEAD. */
@Entity(tableName = "repo_state")
data class RepoStateRow(
    @PrimaryKey val repoId: String,
    val lastIndexedHeadSha: String?,
    val schemaVersion: Int,
    val updatedAtEpochMs: Long,
)

/**
 * Phase XX.2 / AT-B.4 / RV-R.4 — cached completion-state per resolver
 * instance. Derived column; rebuilt by the render pass on demand. The
 * [completionState] value is the `.name` of [CompletionState] for
 * compactness + Room-driver friendliness.
 *
 * Invalidation triggers (caller-enforced per AT-B.4):
 *   (a) underlying file changes (indexer pass deletes affected rows),
 *   (b) deviation file for the (target, date) added/removed,
 *   (c) `now` crosses event.start / event.end (per-event AlarmManager
 *       state-tick from XX.3 — the end-alarm receiver writes the
 *       `CompletedBySchedule` row here).
 */
@Entity(
    tableName = "event_instance_state",
    primaryKeys = ["repoId", "targetId", "occurrenceDate"],
)
data class EventInstanceStateRow(
    val repoId: String,
    val targetId: String,
    /** ISO yyyy-MM-dd, in the event's local zone. */
    val occurrenceDate: String,
    /** [CompletionState.name]. */
    val completionState: String,
    val updatedAtEpochMs: Long,
)

/** ParseResult.Failed surface — per repo, per file. */
@Entity(tableName = "index_errors", primaryKeys = ["repoId", "sourcePath"])
data class IndexErrorRow(
    val repoId: String,
    val sourcePath: String,
    val errorMessage: String,
    val atEpochMs: Long,
)

/**
 * FTS table for event titles + bodies. Synced programmatically by the
 * indexer (not via SQL triggers — keeping the indexer the single
 * authority over what's in cache).
 */
@Entity(tableName = "events_fts")
@Fts4(tokenizer = "unicode61")
data class EventFtsRow(
    @PrimaryKey(autoGenerate = true) val rowid: Int = 0,
    val repoId: String,
    val eventId: String,
    val title: String,
    val body: String,
)

@Entity(tableName = "tasks_fts")
@Fts4(tokenizer = "unicode61")
data class TaskFtsRow(
    @PrimaryKey(autoGenerate = true) val rowid: Int = 0,
    val repoId: String,
    val taskId: String,
    val title: String,
    val body: String,
)
