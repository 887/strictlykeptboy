package com.eight87.strictlykeptboy.store

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.Uuid7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Scaffolds the initial repo structure: AGENTS.md, CLAUDE.md (symlink
 * or stub fallback), identity.toml, .strictlykeptboy/{repo,schema}.toml,
 * and seed calendar/todolist directories.
 *
 * Per DM-G + Phase XX. The wizard (Phase K) calls this once on repo
 * init; nothing else writes these files.
 */
object RepoBootstrap {

    /** Bootstrap inputs assembled by the wizard / caller. */
    data class Spec(
        val repoName: String,
        val tzId: String,
        val identity: AuthorIdentity,
        val seedCalendars: List<String> = emptyList(),
        val seedTodolists: List<String> = emptyList(),
        val praiseTerm: String = "good boy",
        val pronouns: PronounSet = PronounSet.HeHim,
    )

    data class PronounSet(
        val subject: String,
        val obj: String,
        val possessive: String,
        val reflexive: String,
    ) {
        companion object {
            val HeHim = PronounSet("he", "him", "his", "himself")
            val SheHer = PronounSet("she", "her", "hers", "herself")
            val TheyThem = PronounSet("they", "them", "theirs", "themself")
        }
    }

    data class ScaffoldResult(
        val agentsMdPath: Path,
        val claudeMdPath: Path,
        val identityTomlPath: Path,
        val repoMetaPath: Path,
        val schemaMetaPath: Path,
        val identityFilePath: Path,
        val repoId: String,
        val identityId: String,
        val calendarIds: Map<String, String>,
        val todolistIds: Map<String, String>,
    )

    suspend fun scaffold(rootDir: File, spec: Spec): ScaffoldResult = withContext(Dispatchers.IO) {
        val root = rootDir.toPath()
        Files.createDirectories(root)
        Files.createDirectories(root.resolve(".strictlykeptboy"))
        Files.createDirectories(root.resolve("identities"))
        Files.createDirectories(root.resolve("calendars"))
        Files.createDirectories(root.resolve("todolists"))
        Files.createDirectories(root.resolve("journal"))
        Files.createDirectories(root.resolve("feedback"))
        Files.createDirectories(root.resolve("reviews"))
        Files.createDirectories(root.resolve("attachments"))

        val repoId = Uuid7.generate().toString()
        val identityId = Uuid7.generate().toString()
        val now = nowIso()

        val agentsMd = renderAgentsMd(spec, repoId, identityId)
        val agentsPath = root.resolve("AGENTS.md")
        Files.write(agentsPath, agentsMd.toByteArray(StandardCharsets.UTF_8))

        val claudePath = root.resolve("CLAUDE.md")
        try {
            // Symlink (relative) first per DM-G.1; stub fallback if unsupported.
            Files.deleteIfExists(claudePath)
            Files.createSymbolicLink(claudePath, Paths.get("AGENTS.md"))
        } catch (_: UnsupportedOperationException) {
            Files.write(claudePath, "See AGENTS.md.\n".toByteArray(StandardCharsets.UTF_8))
        } catch (_: FileSystemException) {
            Files.write(claudePath, "See AGENTS.md.\n".toByteArray(StandardCharsets.UTF_8))
        }

        val schemaTable = TomlTable().apply {
            putInt("schema_version", SchemaMigrationRunner.LATEST_SCHEMA)
            putString("kind", "schema_meta")
        }
        val schemaPath = root.resolve(".strictlykeptboy/schema.toml")
        Files.write(schemaPath, TomlWriter.emit(schemaTable).toByteArray(StandardCharsets.UTF_8))

        val repoTable = TomlTable().apply {
            putInt("schema_version", 1)
            putString("id", repoId)
            putString("kind", "repo_meta")
            putString("name", spec.repoName)
            putString("default_identity", identityId)
            putOffsetDateTime("created_at", now)
        }
        val repoMetaPath = root.resolve(".strictlykeptboy/repo.toml")
        Files.write(repoMetaPath, TomlWriter.emit(repoTable).toByteArray(StandardCharsets.UTF_8))

        val identityFile = Identity(
            header = EntityHeader(
                id = identityId,
                createdAt = now,
                updatedAt = now,
                author = identityId,
            ),
            displayName = spec.identity.name,
            email = spec.identity.email,
            defaultAuthor = true,
        )
        val identityFilePath = root.resolve(EntityPath.identity(identityId))
        Files.createDirectories(identityFilePath.parent)
        Files.write(
            identityFilePath,
            FrontmatterWriter.serialize(identityFile.toDoc()).toByteArray(StandardCharsets.UTF_8),
        )

        val identityTomlTable = TomlTable().apply {
            putInt("schema_version", 1)
            val praise = TomlTable().apply { putString("term", spec.praiseTerm) }
            sections["praise"] = praise
            val pron = TomlTable().apply {
                putString("subject", spec.pronouns.subject)
                putString("object", spec.pronouns.obj)
                putString("possessive", spec.pronouns.possessive)
                putString("reflexive", spec.pronouns.reflexive)
            }
            sections["pronouns"] = pron
        }
        val identityTomlPath = root.resolve("identity.toml")
        Files.write(
            identityTomlPath,
            TomlWriter.emit(identityTomlTable).toByteArray(StandardCharsets.UTF_8),
        )

        val calendarIds = spec.seedCalendars.associateWith { name ->
            val cid = Uuid7.generate().toString()
            val dir = root.resolve("calendars/$cid")
            Files.createDirectories(dir.resolve("events"))
            Files.createDirectories(dir.resolve("recurrences"))
            Files.createDirectories(dir.resolve("exceptions"))
            Files.createDirectories(dir.resolve("deviations"))
            val meta = TomlTable().apply {
                putInt("schema_version", 1)
                putString("id", cid)
                putString("kind", "calendar")
                putString("name", name)
                putString("tz_id", spec.tzId)
                putOffsetDateTime("created_at", now)
                putOffsetDateTime("updated_at", now)
                putString("author", identityId)
            }
            Files.write(dir.resolve("calendar.toml"), TomlWriter.emit(meta).toByteArray(StandardCharsets.UTF_8))
            cid
        }

        val todolistIds = spec.seedTodolists.associateWith { name ->
            val lid = Uuid7.generate().toString()
            val dir = root.resolve("todolists/$lid")
            Files.createDirectories(dir.resolve("tasks"))
            Files.createDirectories(dir.resolve("standing"))
            Files.createDirectories(dir.resolve("recurrences"))
            val meta = TomlTable().apply {
                putInt("schema_version", 1)
                putString("id", lid)
                putString("kind", "todolist")
                putString("name", name)
                putString("tz_id", spec.tzId)
                putOffsetDateTime("created_at", now)
                putOffsetDateTime("updated_at", now)
                putString("author", identityId)
            }
            Files.write(dir.resolve("todolist.toml"), TomlWriter.emit(meta).toByteArray(StandardCharsets.UTF_8))
            lid
        }

        // Gitignore for fingerprint-cache + tmp staging files per DM-N.6.
        val gitignore = """
            .strictlykeptboy/repo-fingerprint
            *.tmp-*
        """.trimIndent() + "\n"
        Files.write(root.resolve(".gitignore"), gitignore.toByteArray(StandardCharsets.UTF_8))

        // Phase DDD.1 / DM-Y.1 — default `mode.toml` at repo creation
        // (`free`; transitions later flip it). Mode IS committed.
        ModeTomlCodec.write(root, ModeTomlData.Default)

        ScaffoldResult(
            agentsMdPath = agentsPath,
            claudeMdPath = claudePath,
            identityTomlPath = identityTomlPath,
            repoMetaPath = repoMetaPath,
            schemaMetaPath = schemaPath,
            identityFilePath = identityFilePath,
            repoId = repoId,
            identityId = identityId,
            calendarIds = calendarIds,
            todolistIds = todolistIds,
        )
    }

    private fun nowIso(): String {
        // RFC-3339 offset datetime, second precision. We don't need ms.
        val odt = java.time.OffsetDateTime.now().withNano(0)
        return odt.toString()
    }

    private fun renderAgentsMd(spec: Spec, repoId: String, identityId: String): String =
        AGENTS_MD_TEMPLATE
            .replace("{{REPO_NAME}}", spec.repoName)
            .replace("{{REPO_ID}}", repoId)
            .replace("{{TZ_ID}}", spec.tzId)
            .replace("{{DEFAULT_IDENTITY_ID}}", identityId)
            .replace("{{SCHEMA_VERSION}}", SchemaMigrationRunner.LATEST_SCHEMA.toString())

    /**
     * The AGENTS.md content written into every user-data repo. Per
     * DM-G.3 + DM-Y.4 (identity.toml pointer). Embedded as a Kotlin
     * string literal so the wizard never needs to ship a separate
     * template asset. Placeholders are substituted at scaffold time.
     */
    private const val AGENTS_MD_TEMPLATE = """# AGENTS.md — {{REPO_NAME}}

> AI agent guide for this calendar/task repository. If you are an AI
> tool (Claude, Copilot, Aider, etc.) acting on this repo, read this
> file end to end before writing anything.

This repo is managed by **strictlykeptboy**, an Android app that uses a
git repository as the canonical store for calendar events, todolists,
recurrence rules, and per-instance deviations. Every entity lives as a
single hand-editable file. The app reads and writes the same files you
do — there is no hidden index.

See `identity.toml` at repo root for the user's praise term, pronouns,
and tone register — use these when generating content for this user.

## Ground rules

1. **One file per entity. One entity per file.** Never bundle two
   events in the same file.
2. **Never create an index file** (no `events.json`, no `tasks.csv`,
   no `summary.md`). Indexes are merge-conflict factories — the app
   rebuilds its index from a filesystem scan on every git HEAD change.
3. **IDs are UUIDv7, lowercase, canonical form.** Filenames embed the
   ID. Never invent an ID by hand — generate one (e.g. `uuidgen` with
   the v7 flag, or `python -c "import uuid; print(uuid.uuid7())"` on
   3.13+).
4. **File format = TOML frontmatter + Markdown body, fenced by `+++`.**
   The frontmatter opens with `+++` on line 1 and closes with `+++` on
   its own line. Exactly two fences, no trailing third fence. The body
   (Markdown) follows the closing `+++`. Pure-TOML metadata files
   (`calendar.toml`, `todolist.toml`, `.strictlykeptboy/*.toml`,
   `identity.toml`) have no fences and no body.
5. **Schema version is `{{SCHEMA_VERSION}}`.** Every entity file
   starts with `schema_version = {{SCHEMA_VERSION}}`. Bump only after
   coordinating with the app's migration code.
6. **Comments inside TOML frontmatter are dropped on next app write.**
   Put commentary in the Markdown body.

## Repo layout

```
{{REPO_NAME}}/
├── AGENTS.md                          ← this file
├── CLAUDE.md                          ← symlink → AGENTS.md
├── identity.toml                      praise / pronouns / tone register
├── .strictlykeptboy/
│   ├── schema.toml                    schema_version, last_writer
│   └── repo.toml                      repo display name + defaults
├── identities/<uuid>.md               authors
├── calendars/<uuid>/
│   ├── calendar.toml                  calendar metadata
│   ├── events/<yyyy>/<mm>/<uuid>.md   one event per file
│   ├── recurrences/<uuid>.md          RRULE rules
│   ├── exceptions/<rule>/<date>.md    cancel / override an instance
│   └── deviations/<target>/<date>.md  post-hoc reality reports
├── todolists/<uuid>/
│   ├── todolist.toml
│   ├── tasks/<yyyy>/<mm>/<uuid>.md
│   ├── standing/<uuid>.md
│   └── recurrences/<uuid>.md
├── overrides/<superseded-cal>/<event>/<date>.md
├── journal/<yyyy-mm-dd>.md
└── attachments/<sha-prefix>/<sha>.<ext>
```

## Defaults for this repo

- **Default timezone:** `{{TZ_ID}}` (override per-calendar in
  `calendar.toml`).
- **Default identity:** `{{DEFAULT_IDENTITY_ID}}` — the file at
  `identities/{{DEFAULT_IDENTITY_ID}}.md` is the seed author. Exactly
  one identity in `identities/` must carry `default_author = true`.

## Exact field names — canonical schema

The app parser reads these exact TOML keys. Use them verbatim. Drifting
to synonyms (e.g. `start_at`, `calendar`, `visibility`) causes silent
parse failures.

### Common header (all entity files)

| Key              | Type                  | Required | Notes                              |
|------------------|-----------------------|----------|------------------------------------|
| `schema_version` | int                   | yes      | Always `{{SCHEMA_VERSION}}`        |
| `id`             | string (UUIDv7)       | yes      | Matches filename stem              |
| `kind`           | string (enum)         | yes      | See per-entity tables below        |
| `created_at`     | offset-datetime       | yes      | RFC-3339 with offset, e.g. `2026-06-16T09:00:00+01:00` |
| `updated_at`     | offset-datetime       | yes      | Same format                        |
| `author`         | string (identity id)  | yes      | UUIDv7 of the authoring identity   |

Datetimes are bare TOML offset-datetimes — **not quoted strings**.
`2026-06-16T09:00:00+01:00` is correct; `"2026-06-16T09:00:00+01:00"` is wrong.

### One-off event — `kind = "event"`

Path: `calendars/<cal-id>/events/<yyyy>/<mm>/<id>.md`

| Key              | Type            | Required | Notes                                    |
|------------------|-----------------|----------|------------------------------------------|
| `title`          | string          | yes      |                                          |
| `start`          | offset-datetime | yes      | NOT `start_at`, NOT `dtstart`            |
| `end`            | offset-datetime | yes      | NOT `end_at`, NOT `duration`             |
| `calendar_id`    | string          | yes      | NOT `calendar`                           |
| `all_day`        | bool            | no       | Omit when false                          |
| `busy`           | bool            | no       | Default true; write only when false      |
| `private`        | bool            | no       | NOT `visibility`; omit when false        |
| `tags`           | string array    | no       | `tags = ["tag1", "tag2"]`                |
| `emoji`          | string          | no       |                                          |
| `group`          | string          | no       | Grouping label for consecutive events    |
| `location`       | string          | no       |                                          |
| `tz_id`          | string          | no       | IANA zone e.g. `"America/New_York"`      |

**Minimal example:**
```
+++
schema_version = {{SCHEMA_VERSION}}
id = "01952f3a-1234-7000-8000-000000000001"
kind = "event"
created_at = 2026-06-16T09:00:00+01:00
updated_at = 2026-06-16T09:00:00+01:00
author = "{{DEFAULT_IDENTITY_ID}}"
title = "Morning check-in"
start = 2026-06-16T09:00:00+01:00
end = 2026-06-16T09:15:00+01:00
calendar_id = "<cal-uuid>"
+++
Notes go here in Markdown.
```

### Recurrence rule — `kind = "recurrence"`

Path: `calendars/<cal-id>/recurrences/<id>.md`

| Key           | Type             | Required | Notes                                         |
|---------------|------------------|----------|-----------------------------------------------|
| `title`       | string           | yes      |                                               |
| `dtstart`     | local-datetime   | yes      | No offset, no zone — e.g. `2026-06-16T09:00:00` |
| `duration`    | string (ISO-8601)| yes      | e.g. `"PT45M"`, `"P1DT2H"`, `"PT1H30M"`      |
| `tz_id`       | string           | yes      | IANA zone name                                |
| `rrule`       | string           | yes      | RFC-5545 e.g. `"FREQ=DAILY"`, `"FREQ=WEEKLY;BYDAY=MO,WE,FR"` |
| `calendar_id` | string           | yes      |                                               |
| `passive`     | bool             | no       | True = done-by-schedule (passive habit)       |
| `active`      | bool             | no       | Default true; write only when false           |
| `busy`        | bool             | no       | Default true; write only when false           |
| `tags`        | string array     | no       |                                               |
| `emoji`       | string           | no       |                                               |
| `group`       | string           | no       |                                               |

Note: `passive` supersedes the old `inverted` key — both are accepted
on read but `passive` is canonical on write.

**Minimal example:**
```
+++
schema_version = {{SCHEMA_VERSION}}
id = "01952f3a-1234-7000-8000-000000000002"
kind = "recurrence"
created_at = 2026-06-16T09:00:00+01:00
updated_at = 2026-06-16T09:00:00+01:00
author = "{{DEFAULT_IDENTITY_ID}}"
title = "Morning walk"
dtstart = 2026-06-16T07:30:00
duration = "PT30M"
tz_id = "{{TZ_ID}}"
rrule = "FREQ=DAILY"
calendar_id = "<cal-uuid>"
passive = true
+++
```

### Task — `kind = "task"`

Path: `todolists/<list-id>/tasks/<yyyy>/<mm>/<id>.md`

| Key           | Type                  | Required | Notes                                |
|---------------|-----------------------|----------|--------------------------------------|
| `title`       | string                | yes      |                                      |
| `todolist_id` | string                | yes      | NOT `list_id`, NOT `todolist`        |
| `due`         | local-date or offset-datetime | no | e.g. `2026-06-16` or full datetime  |
| `done`        | bool                  | no       | Omit when false                      |
| `done_at`     | offset-datetime       | no       | Set when `done = true`               |
| `priority`    | int                   | no       | Lower = higher priority              |
| `tags`        | string array          | no       |                                      |

**Minimal example:**
```
+++
schema_version = {{SCHEMA_VERSION}}
id = "01952f3a-1234-7000-8000-000000000003"
kind = "task"
created_at = 2026-06-16T09:00:00+01:00
updated_at = 2026-06-16T09:00:00+01:00
author = "{{DEFAULT_IDENTITY_ID}}"
title = "Buy groceries"
todolist_id = "<list-uuid>"
due = 2026-06-16
+++
```

### Standing task — `kind = "standing_task"`

Path: `todolists/<list-id>/standing/<id>.md`

| Key           | Type            | Required | Notes                           |
|---------------|-----------------|----------|---------------------------------|
| `title`       | string          | yes      |                                 |
| `todolist_id` | string          | yes      |                                 |
| `done`        | bool            | no       | Omit when false                 |
| `done_at`     | offset-datetime | no       | Set when `done = true`          |
| `pinned`      | bool            | no       | Omit when false                 |
| `priority`    | int             | no       |                                 |
| `tags`        | string array    | no       |                                 |

### Deviation — `kind = "deviation"`

Path: `calendars/<cal-id>/deviations/<target-id>/<yyyy-mm-dd>.md`

| Key              | Type            | Required | Notes                                                              |
|------------------|-----------------|----------|--------------------------------------------------------------------|
| `target_id`      | string          | yes      | ID of the recurrence rule or event                                 |
| `instance_date`  | local-date      | yes      | e.g. `2026-06-16`                                                  |
| `deviation_kind` | string          | yes      | `"skipped"`, `"partial"`, `"completed-early"`, `"completed-late"` |
| `at`             | offset-datetime | yes      | When the deviation was recorded                                    |
| `note`           | string          | no       |                                                                    |

### Override — `kind = "override"`

Path: `overrides/<superseded-cal-id>/<event-id>/<yyyy-mm-dd>.md`

Used to force-show a specific event instance that supersedence would
otherwise hide.

| Key                     | Type       | Required | Notes                                    |
|-------------------------|------------|----------|------------------------------------------|
| `superseded_calendar_id`| string     | yes      | The calendar being paused by supersedence |
| `event_id`              | string     | yes      | The event/rule to force-show             |
| `instance_date`         | local-date | yes      |                                          |
| `override_kind`         | string     | yes      | `"force-show"` or `"force-show-for-range"` |
| `from`                  | local-date | no       | Only for `force-show-for-range`          |
| `to`                    | local-date | no       | Only for `force-show-for-range`          |

## Special rules

### nonSuperseable entries

A recurrence entry that must never be paused by a vacation/overlay
calendar (e.g. medication, pet care) signals this via its tags:

```
tags = ["meds", "nonSuperseable"]
```

The literal string `"nonSuperseable"` (camelCase, no space) in the
`tags` array tells the resolver to skip supersedence for this entry.
This is a **tag**, not a boolean field. Do NOT write `nonSuperseable = true`.

### Supersedence (trip/vacation pausing a calendar)

Supersedence is configured in `calendar.toml` (pure TOML, no fences):

```toml
# The vacation calendar pauses these calendars while active:
supersedes = ["<routine-cal-uuid>", "<work-cal-uuid>"]

# Optional date-range restriction (always-on if omitted):
[[superseded_during]]
from = 2026-06-20
to   = 2026-06-27

# Make THIS calendar immune to all supersedence:
nonSuperseable = true
```

Both `nonSuperseable` and `non_superseable` are accepted on read;
`nonSuperseable` is canonical on write.

### Passive habits

A recurrence with `passive = true` is treated as completed-by-default
unless a deviation file exists for that day. Use this for hygiene
routines, medication, pet care — things the user does unless noted
otherwise.

## How to add a one-off event

1. Generate a UUIDv7 (the `id`).
2. Decide on the calendar (its `<calendar-id>` is the folder name
   under `calendars/`).
3. Bucket path: `calendars/<cal-id>/events/<yyyy>/<mm>/<id>.md`
   (bucket by the event's *start* date in the calendar's timezone).
4. Write the file with exactly two `+++` fences and a body.

## How to cancel one instance of a recurring event

Create `calendars/<cal-id>/exceptions/<rule-id>/<yyyy-mm-dd>.md` with
`mode = "cancel"`. Never edit the rule file to silently drop dates.

## How to mark a task done

Set `done = true` and `done_at = <offset-datetime>` in the task's
frontmatter. The body checkbox is a visual cue only — the frontmatter
`done` flag is the truth.

## Things you should NOT do

- Don't use `start_at`/`end_at` for events — use `start`/`end`.
- Don't use `dtstart`/`duration` for one-off events — that's for recurrences.
- Don't use `calendar` — use `calendar_id`.
- Don't use `visibility = "private"` — use `private = true` (boolean).
- Don't write `nonSuperseable = true` on a recurrence — use the tag `"nonSuperseable"`.
- Don't bundle entities (one file per entity).
- Don't create index files.
- Don't invent IDs by hand.
- Don't write to `attachments/` (content-addressed; the app manages).
- Don't put commentary in TOML comments — use the Markdown body.
- Don't add a third `+++` fence at the end — exactly two fences only.
- Don't quote datetimes — bare TOML datetime literals, not strings.

## Where to find more

- App source: <github.com/eight87/strictlykeptboy>
- Schema spec: `docs/plans/data-model.md` in the app repo
- Locked decisions: `docs/plans/decisions.md` in the app repo
"""
}
