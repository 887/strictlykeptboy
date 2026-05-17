package com.eight87.strictlykeptboy.store

/**
 * Typed entity model for the store layer (DM-B).
 *
 * The discriminator is implicit in path bucketing (DM-D / DM-C) but
 * also recorded explicitly in the frontmatter `kind` field for
 * cross-tool readability per DM-B header table.
 *
 * Coverage decision (Phase C v1): we type the highest-traffic entity
 * shapes — Event, RecurrenceRule, Exception, Task, StandingTask,
 * Deviation, Override, JournalEntry — plus the four metadata files
 * (Calendar, Todolist, RepoMeta, SchemaMeta) and Identity. Anything
 * else, or a structurally-correct file with unknown `kind`, is
 * surfaced as [RawEntity] carrying the parsed TOML table. The
 * validator (DM-H, future phase) decides how to surface unknowns.
 */
sealed interface TypedEntity {
    val schemaVersion: Int
    val id: String
    val kind: EntityKind
}

enum class EntityKind(val tomlValue: String) {
    Event("event"),
    Recurrence("recurrence"),
    Exception("exception"),
    Task("task"),
    StandingTask("standing_task"),
    TaskRecurrence("task_recurrence"),
    Calendar("calendar"),
    Todolist("todolist"),
    Identity("identity"),
    RepoMeta("repo_meta"),
    SchemaMeta("schema_meta"),
    Deviation("deviation"),
    Override("override"),
    Journal("journal"),
    Bonus("bonus"),
    Raw("raw");

    companion object {
        fun fromToml(v: String?): EntityKind? = entries.firstOrNull { it.tomlValue == v }
    }
}

/** Common header fields per DM-B "Common header" table. */
data class EntityHeader(
    val schemaVersion: Int = 1,
    val id: String,
    val createdAt: String,
    val updatedAt: String,
    val author: String,
) {
    fun writeInto(t: TomlTable) {
        t.putInt("schema_version", schemaVersion)
        t.putString("id", id)
        // `kind` is written by each entity since the value differs.
        t.putOffsetDateTime("created_at", createdAt)
        t.putOffsetDateTime("updated_at", updatedAt)
        t.putString("author", author)
    }

    companion object {
        fun readFrom(t: TomlTable): EntityHeader = EntityHeader(
            schemaVersion = t.getInt("schema_version") ?: 1,
            id = t.getString("id") ?: error("missing id"),
            createdAt = t.getDateLike("created_at") ?: error("missing created_at"),
            updatedAt = t.getDateLike("updated_at") ?: error("missing updated_at"),
            author = t.getString("author") ?: error("missing author"),
        )
    }
}

// --- Event ------------------------------------------------------------------

/** Single-instance event per DM-B.1. */
data class Event(
    val header: EntityHeader,
    val title: String,
    val start: String,
    val end: String,
    val calendarId: String,
    val allDay: Boolean = false,
    val location: String? = null,
    val attendees: List<String> = emptyList(),
    val notifications: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val emoji: String? = null,
    val busy: Boolean = true,
    val priorityOverride: Int? = null,
    val externalUid: String? = null,
    val private: Boolean = false,
    /** Phase XX.8 / AT-H.3 audit: routine id this event was materialized from. */
    val materializedFrom: String? = null,
    /** Phase XX.8 / AT-H.3 audit: source routine-calendar template event id. */
    val materializedSourceEvent: String? = null,
    /** Phase XX.8 / AT-H.3 audit: ISO timestamp of materialization (shared across the batch — undo-group key). */
    val materializedAt: String? = null,
    /** Phase XX.9 / AT-I.1 inline `[[subbeat]]` array preserved verbatim across materialization. */
    val subbeats: List<AtomicTemplateSubbeat> = emptyList(),
    /**
     * Round 2.21.A.3 — optional grouping label. When the source
     * calendar's `meta_group_field` is set, consecutive events sharing
     * the same `group` value are visually collapsed at zoom ≤ 2 (per
     * D-2.21.j/k). The literal string here is the user-visible group
     * name ("Morning routine", "Grooming"). `null` ⇒ ungrouped.
     */
    val group: String? = null,
    /**
     * Round 2.27 / D-2.27.a — when `true`, this event is a keeper-prompt
     * and demands a response. Projected into the todolist via
     * `FromEventsProjector` + `TaskSource.KeeperPrompt` (Phase B).
     */
    val requiresResponse: Boolean = false,
    /** Round 2.27 / D-2.27.a — what response shape the prompt wants. `null` when [requiresResponse] is `false`. */
    val promptKind: PromptKind? = null,
    /** Round 2.27 / D-2.27.a — who the prompt is from. `null` when [requiresResponse] is `false`. */
    val promptTarget: PromptTarget? = null,
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Event

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.Event.tomlValue)
        t.putString("title", title)
        t.putOffsetDateTime("start", start)
        t.putOffsetDateTime("end", end)
        t.putString("calendar_id", calendarId)
        if (allDay) t.putBool("all_day", true)
        t.putString("location", location)
        if (attendees.isNotEmpty()) t.putStringArray("attendees", attendees)
        if (notifications.isNotEmpty()) t.putStringArray("notifications", notifications)
        if (tags.isNotEmpty()) t.putStringArray("tags", tags)
        t.putString("emoji", emoji)
        if (!busy) t.putBool("busy", false)
        t.putInt("priority_override", priorityOverride)
        t.putString("external_uid", externalUid)
        if (private) t.putBool("private", true)
        group?.takeIf { it.isNotBlank() }?.let { t.putString("group", it) }
        // Round 2.27 / D-2.27.b — keeper-prompt schema. Omit on write
        // when default-valued so re-saving a non-prompt event doesn't
        // sprinkle empty fields into hand-authored files.
        if (requiresResponse) t.putBool("requires_response", true)
        promptKind?.let { t.putString("prompt_kind", it.tomlValue) }
        promptTarget?.let { t.putString("prompt_target", it.tomlValue) }
        // Phase XX.8 / AT-H.3 — additive audit fields. Resolver ignores
        // them; they exist for `skb routine undo <materialized-at>` and
        // for surfacing "where did this event come from?" in the UI.
        materializedFrom?.let { t.putString("materialized_from", it) }
        materializedSourceEvent?.let { t.putString("materialized_source_event", it) }
        materializedAt?.let { t.putString("materialized_at", it) }
        // Phase XX.9 / AT-I.1 — inline sub-beat array. Stored as an
        // array-of-tables under the key `subbeat` (matches DM-M /
        // template format; the TOML codec emits `[[subbeat]]`).
        if (subbeats.isNotEmpty()) {
            t.aotables["subbeat"] = subbeats.map { sb ->
                TomlTable().apply {
                    putString("label", sb.label)
                    putInt("duration_seconds", sb.durationSeconds)
                    sb.stickerId?.let { putString("sticker_id", it) }
                }
            }.toMutableList()
        }
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): Event {
            val t = doc.frontmatter
            return Event(
                header = EntityHeader.readFrom(t),
                title = t.getString("title") ?: error("missing title"),
                start = t.getDateLike("start") ?: error("missing start"),
                end = t.getDateLike("end") ?: error("missing end"),
                calendarId = t.getString("calendar_id") ?: error("missing calendar_id"),
                allDay = t.getBool("all_day") ?: false,
                location = t.getString("location"),
                attendees = t.getStringArray("attendees") ?: emptyList(),
                notifications = t.getStringArray("notifications") ?: emptyList(),
                tags = t.getStringArray("tags") ?: emptyList(),
                emoji = t.getString("emoji"),
                busy = t.getBool("busy") ?: true,
                priorityOverride = t.getInt("priority_override"),
                externalUid = t.getString("external_uid"),
                private = t.getBool("private") ?: false,
                group = t.getString("group")?.takeIf { it.isNotBlank() },
                requiresResponse = t.getBool("requires_response") ?: false,
                promptKind = PromptKind.fromToml(t.getString("prompt_kind")),
                promptTarget = PromptTarget.fromToml(t.getString("prompt_target")),
                materializedFrom = t.getString("materialized_from"),
                materializedSourceEvent = t.getString("materialized_source_event"),
                materializedAt = t.getString("materialized_at"),
                subbeats = t.aotables["subbeat"]?.map { sb ->
                    AtomicTemplateSubbeat(
                        label = sb.getString("label") ?: error("subbeat missing label"),
                        durationSeconds = sb.getInt("duration_seconds") ?: error("subbeat missing duration_seconds"),
                        stickerId = sb.getString("sticker_id"),
                    )
                } ?: emptyList(),
                body = doc.body,
            )
        }
    }
}

// --- RecurrenceRule ---------------------------------------------------------

/** Recurring-event rule per DM-B.2. */
data class RecurrenceRule(
    val header: EntityHeader,
    val title: String,
    val dtstart: String,
    val duration: String,
    val tzId: String,
    val rrule: String,
    val calendarId: String,
    val rdate: List<String> = emptyList(),
    val exdate: List<String> = emptyList(),
    val location: String? = null,
    val notifications: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val emoji: String? = null,
    val busy: Boolean = true,
    val active: Boolean = true,
    /**
     * Phase 2.1.I.6 — inverted-default habit marker (per Phase XX inversion
     * model, draft-atomic-activities.md AT-B.2). When `true`, this rule's
     * occurrences should be treated as "completed-by-schedule" until a
     * deviation file is written for that day. The resolver applies the
     * same algorithm universally today; this field is the explicit
     * signal on disk so readers / future per-rule visualization can
     * distinguish "this is a habit" from "this is a meeting".
     *
     * Default `false` keeps existing rule files round-tripping unchanged.
     */
    val inverted: Boolean = false,
    /** Round 2.21.A.3 — optional grouping label, see [Event.group]. */
    val group: String? = null,
    /** Round 2.27 / D-2.27.a — recurring keeper-prompt; propagates to every materialized instance. */
    val requiresResponse: Boolean = false,
    /** Round 2.27 / D-2.27.a — see [Event.promptKind]. */
    val promptKind: PromptKind? = null,
    /** Round 2.27 / D-2.27.a — see [Event.promptTarget]. */
    val promptTarget: PromptTarget? = null,
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Recurrence

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.Recurrence.tomlValue)
        t.putString("title", title)
        t.putLocalDateTime("dtstart", dtstart)
        t.putString("duration", duration)
        t.putString("tz_id", tzId)
        t.putString("rrule", rrule)
        t.putString("calendar_id", calendarId)
        if (rdate.isNotEmpty()) t.scalars["rdate"] = TomlValue.Arr(rdate.map { TomlValue.LocalDateTime(it) })
        if (exdate.isNotEmpty()) t.scalars["exdate"] = TomlValue.Arr(exdate.map { TomlValue.LocalDateTime(it) })
        t.putString("location", location)
        if (notifications.isNotEmpty()) t.putStringArray("notifications", notifications)
        if (tags.isNotEmpty()) t.putStringArray("tags", tags)
        t.putString("emoji", emoji)
        if (!busy) t.putBool("busy", false)
        if (!active) t.putBool("active", false)
        if (inverted) t.putBool("inverted", true)
        group?.takeIf { it.isNotBlank() }?.let { t.putString("group", it) }
        // Round 2.27 / D-2.27.b — keeper-prompt schema (same omit-on-default rule as Event).
        if (requiresResponse) t.putBool("requires_response", true)
        promptKind?.let { t.putString("prompt_kind", it.tomlValue) }
        promptTarget?.let { t.putString("prompt_target", it.tomlValue) }
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): RecurrenceRule {
            val t = doc.frontmatter
            return RecurrenceRule(
                header = EntityHeader.readFrom(t),
                title = t.getString("title") ?: error("missing title"),
                dtstart = t.getDateLike("dtstart") ?: error("missing dtstart"),
                duration = t.getString("duration") ?: error("missing duration"),
                tzId = t.getString("tz_id") ?: error("missing tz_id"),
                rrule = t.getString("rrule") ?: error("missing rrule"),
                calendarId = t.getString("calendar_id") ?: error("missing calendar_id"),
                rdate = (t.scalars["rdate"] as? TomlValue.Arr)?.items
                    ?.mapNotNull { (it as? TomlValue.LocalDateTime)?.text } ?: emptyList(),
                exdate = (t.scalars["exdate"] as? TomlValue.Arr)?.items
                    ?.mapNotNull { (it as? TomlValue.LocalDateTime)?.text } ?: emptyList(),
                location = t.getString("location"),
                notifications = t.getStringArray("notifications") ?: emptyList(),
                tags = t.getStringArray("tags") ?: emptyList(),
                emoji = t.getString("emoji"),
                busy = t.getBool("busy") ?: true,
                active = t.getBool("active") ?: true,
                inverted = t.getBool("inverted") ?: false,
                group = t.getString("group")?.takeIf { it.isNotBlank() },
                requiresResponse = t.getBool("requires_response") ?: false,
                promptKind = PromptKind.fromToml(t.getString("prompt_kind")),
                promptTarget = PromptTarget.fromToml(t.getString("prompt_target")),
                body = doc.body,
            )
        }
    }
}

// --- Exception --------------------------------------------------------------

/** One-instance cancellation / override of a recurring rule (DM-B.3). */
data class Exception(
    val header: EntityHeader,
    val ruleId: String,
    val instanceDate: String,
    val mode: String,
    val calendarId: String,
    val overrideStart: String? = null,
    val overrideEnd: String? = null,
    val overrideTitle: String? = null,
    val overrideLocation: String? = null,
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Exception

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.Exception.tomlValue)
        t.putString("rule_id", ruleId)
        t.putString("calendar_id", calendarId)
        t.putLocalDate("instance_date", instanceDate)
        t.putString("mode", mode)
        t.putOffsetDateTime("override_start", overrideStart)
        t.putOffsetDateTime("override_end", overrideEnd)
        t.putString("override_title", overrideTitle)
        t.putString("override_location", overrideLocation)
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc, calendarIdFromPath: String? = null): Exception {
            val t = doc.frontmatter
            return Exception(
                header = EntityHeader.readFrom(t),
                ruleId = t.getString("rule_id") ?: error("missing rule_id"),
                instanceDate = t.getDateLike("instance_date") ?: error("missing instance_date"),
                mode = t.getString("mode") ?: error("missing mode"),
                calendarId = t.getString("calendar_id") ?: calendarIdFromPath ?: error("missing calendar_id"),
                overrideStart = t.getDateLike("override_start"),
                overrideEnd = t.getDateLike("override_end"),
                overrideTitle = t.getString("override_title"),
                overrideLocation = t.getString("override_location"),
                body = doc.body,
            )
        }
    }
}

// --- Task -------------------------------------------------------------------

/** Dated task per DM-B.6. */
data class Task(
    val header: EntityHeader,
    val title: String,
    val todolistId: String,
    val due: String? = null,
    val done: Boolean = false,
    val doneAt: String? = null,
    val priority: Int? = null,
    val tags: List<String> = emptyList(),
    val notifications: List<String> = emptyList(),
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Task

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.Task.tomlValue)
        t.putString("title", title)
        t.putString("todolist_id", todolistId)
        // `due` may be either a date or a datetime; preserve original form.
        if (due != null) {
            if (due.length == 10) t.putLocalDate("due", due) else t.putOffsetDateTime("due", due)
        }
        if (done) t.putBool("done", true)
        t.putOffsetDateTime("done_at", doneAt)
        t.putInt("priority", priority)
        if (tags.isNotEmpty()) t.putStringArray("tags", tags)
        if (notifications.isNotEmpty()) t.putStringArray("notifications", notifications)
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): Task {
            val t = doc.frontmatter
            return Task(
                header = EntityHeader.readFrom(t),
                title = t.getString("title") ?: error("missing title"),
                todolistId = t.getString("todolist_id") ?: error("missing todolist_id"),
                due = t.getDateLike("due"),
                done = t.getBool("done") ?: false,
                doneAt = t.getDateLike("done_at"),
                priority = t.getInt("priority"),
                tags = t.getStringArray("tags") ?: emptyList(),
                notifications = t.getStringArray("notifications") ?: emptyList(),
                body = doc.body,
            )
        }
    }
}

// --- StandingTask -----------------------------------------------------------

/** Standing (un-dated) task per DM-B.7. */
data class StandingTask(
    val header: EntityHeader,
    val title: String,
    val todolistId: String,
    val done: Boolean = false,
    val pinned: Boolean = false,
    val priority: Int? = null,
    val tags: List<String> = emptyList(),
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.StandingTask

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.StandingTask.tomlValue)
        t.putString("title", title)
        t.putString("todolist_id", todolistId)
        if (done) t.putBool("done", true)
        if (pinned) t.putBool("pinned", true)
        t.putInt("priority", priority)
        if (tags.isNotEmpty()) t.putStringArray("tags", tags)
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): StandingTask {
            val t = doc.frontmatter
            return StandingTask(
                header = EntityHeader.readFrom(t),
                title = t.getString("title") ?: error("missing title"),
                todolistId = t.getString("todolist_id") ?: error("missing todolist_id"),
                done = t.getBool("done") ?: false,
                pinned = t.getBool("pinned") ?: false,
                priority = t.getInt("priority"),
                tags = t.getStringArray("tags") ?: emptyList(),
                body = doc.body,
            )
        }
    }
}

// --- Deviation --------------------------------------------------------------

/** Post-hoc reality report on a scheduled event/rule instance (DM-M.1 / Phase XX). */
data class Deviation(
    val header: EntityHeader,
    val targetId: String,
    val instanceDate: String,
    /** `skipped` | `partial` | `completed-early` | `completed-late`. */
    val devKind: String,
    val at: String,
    val note: String? = null,
    val subbeatsCompleted: List<String> = emptyList(),
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Deviation

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        // `kind` here is the deviation flavour, not the entity type.
        // We store the entity-type discriminator under `entity_kind`
        // to preserve DM-B's invariant that a `kind = "deviation"` row
        // is parseable.
        t.putString("kind", EntityKind.Deviation.tomlValue)
        t.putString("deviation_kind", devKind)
        t.putString("target_id", targetId)
        t.putLocalDate("instance_date", instanceDate)
        t.putOffsetDateTime("at", at)
        t.putString("note", note)
        if (subbeatsCompleted.isNotEmpty()) t.putStringArray("subbeats_completed", subbeatsCompleted)
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): Deviation {
            val t = doc.frontmatter
            return Deviation(
                header = EntityHeader.readFrom(t),
                targetId = t.getString("target_id") ?: error("missing target_id"),
                instanceDate = t.getDateLike("instance_date") ?: error("missing instance_date"),
                devKind = t.getString("deviation_kind") ?: error("missing deviation_kind"),
                at = t.getDateLike("at") ?: error("missing at"),
                note = t.getString("note"),
                subbeatsCompleted = t.getStringArray("subbeats_completed") ?: emptyList(),
                body = doc.body,
            )
        }
    }
}

// --- Override ---------------------------------------------------------------

/** Force-show opt-out for supersedence (DM-AA.3 / HV-E). */
data class Override(
    val header: EntityHeader,
    val supersededCalendarId: String,
    val eventId: String,
    val instanceDate: String,
    /** `force-show` | `force-show-for-range` */
    val overrideKind: String,
    val rangeFrom: String? = null,
    val rangeTo: String? = null,
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Override

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.Override.tomlValue)
        t.putString("override_kind", overrideKind)
        t.putString("superseded_calendar_id", supersededCalendarId)
        t.putString("event_id", eventId)
        t.putLocalDate("instance_date", instanceDate)
        t.putLocalDate("from", rangeFrom)
        t.putLocalDate("to", rangeTo)
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): Override {
            val t = doc.frontmatter
            return Override(
                header = EntityHeader.readFrom(t),
                supersededCalendarId = t.getString("superseded_calendar_id")
                    ?: error("missing superseded_calendar_id"),
                eventId = t.getString("event_id") ?: error("missing event_id"),
                instanceDate = t.getDateLike("instance_date") ?: error("missing instance_date"),
                overrideKind = t.getString("override_kind") ?: error("missing override_kind"),
                rangeFrom = t.getDateLike("from"),
                rangeTo = t.getDateLike("to"),
                body = doc.body,
            )
        }
    }
}

// --- JournalEntry -----------------------------------------------------------

/** Free-form daily journal entry (DM-N.2 / HV-Q). */
data class JournalEntry(
    val header: EntityHeader,
    val day: String,
    val sequence: Int = 0,
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Journal

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.Journal.tomlValue)
        t.putLocalDate("day", day)
        if (sequence != 0) t.putInt("sequence", sequence)
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): JournalEntry {
            val t = doc.frontmatter
            return JournalEntry(
                header = EntityHeader.readFrom(t),
                day = t.getDateLike("day") ?: error("missing day"),
                sequence = t.getInt("sequence") ?: 0,
                body = doc.body,
            )
        }
    }
}

// --- Identity ---------------------------------------------------------------

/** Author identity per DM-B.9. */
data class Identity(
    val header: EntityHeader,
    val displayName: String,
    val email: String? = null,
    val avatar: String? = null,
    val defaultAuthor: Boolean = false,
    val pronouns: String? = null,
    val body: String = "",
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Identity

    fun toDoc(): FrontmatterDoc {
        val t = TomlTable()
        header.writeInto(t)
        t.putString("kind", EntityKind.Identity.tomlValue)
        t.putString("display_name", displayName)
        t.putString("email", email)
        t.putString("avatar", avatar)
        if (defaultAuthor) t.putBool("default_author", true)
        t.putString("pronouns", pronouns)
        return FrontmatterDoc(t, body)
    }

    companion object {
        fun fromDoc(doc: FrontmatterDoc): Identity {
            val t = doc.frontmatter
            return Identity(
                header = EntityHeader.readFrom(t),
                displayName = t.getString("display_name") ?: error("missing display_name"),
                email = t.getString("email"),
                avatar = t.getString("avatar"),
                defaultAuthor = t.getBool("default_author") ?: false,
                pronouns = t.getString("pronouns"),
                body = doc.body,
            )
        }
    }
}

// --- Raw fallback -----------------------------------------------------------

/**
 * Carrier for files that parsed cleanly but whose `kind` we don't yet
 * model. Lets the indexer (Phase D) shrug and move on rather than
 * crashing the scan.
 */
data class RawEntity(
    val header: EntityHeader,
    val rawTable: TomlTable,
    val body: String,
    val rawKind: String?,
) : TypedEntity {
    override val schemaVersion: Int get() = header.schemaVersion
    override val id: String get() = header.id
    override val kind: EntityKind get() = EntityKind.Raw
}
