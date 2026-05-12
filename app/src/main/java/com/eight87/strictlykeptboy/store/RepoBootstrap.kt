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
   The frontmatter is structured; the body is yours to write notes,
   links, and reminders. Pure-TOML metadata files (`calendar.toml`,
   `todolist.toml`, `.strictlykeptboy/*.toml`, `identity.toml`) have
   no fences and no body.
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

## How to add a one-off event

1. Generate a UUIDv7 (the `id`).
2. Decide on the calendar (its `<calendar-id>` is the folder name
   under `calendars/`).
3. Compute the bucket path: month-bucket by the event's *start* date
   in the calendar's timezone — `calendars/<cal-id>/events/<yyyy>/<mm>/<id>.md`.
4. Write the file with `+++`-fenced TOML frontmatter and a body. See
   the schema in `docs/plans/data-model.md` DM-B.1 for the field list.

## How to cancel one instance of a recurring event

Create `calendars/<cal-id>/exceptions/<rule-id>/<yyyy-mm-dd>.md` with
`mode = "cancel"`. Never edit the rule file to silently drop dates.

## How to mark a task done

Set `done = true` and `done_at = <RFC-3339 offset-datetime>` in the
task's frontmatter. The body checkbox is a visual cue only — the
frontmatter `done` flag is the truth.

## Things you should NOT do

- Don't bundle entities (one file per entity).
- Don't invent IDs by hand.
- Don't write to `attachments/` (content-addressed; the app manages).
- Don't put commentary in TOML comments — use the Markdown body.
- Don't edit `.strictlykeptboy/schema.toml`'s `schema_version` field.

## Where to find more

- App source: <github.com/eight87/strictlykeptboy>
- Schema spec: `docs/plans/data-model.md` in the app repo
- Locked decisions: `docs/plans/decisions.md` in the app repo
"""
}
