package com.eight87.strictlykeptboy.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Result of attempting to parse a single entity file. Per DM-D.2: a
 * malformed file does NOT abort the scan; it surfaces as
 * [ParseResult.Failed] so the indexer (Phase D) and the broken-entries
 * tray (DM-H) can decide what to do.
 */
sealed interface ParseResult {
    val sourcePath: Path

    data class Success(val entity: TypedEntity, override val sourcePath: Path) : ParseResult
    data class Failed(override val sourcePath: Path, val error: String) : ParseResult
}

/**
 * Walks a strictlykeptboy repo working tree and emits one
 * [ParseResult] per entity file per DM-D.1.
 *
 * Skipped (per DM-D.3 + Phase C scope): `.git/`, `.strictlykeptboy/`'s
 * non-meta children, top-level `attachments/`, `reviews/`, `feedback/`,
 * and any hidden dotfile that isn't a known meta file.
 */
object RepoScanner {

    suspend fun scanAll(rootDir: File): List<ParseResult> = withContext(Dispatchers.IO) {
        if (!rootDir.isDirectory) return@withContext emptyList()
        val root = rootDir.toPath().toAbsolutePath().normalize()
        val out = mutableListOf<ParseResult>()
        Files.walk(root).use { stream ->
            for (p in stream) {
                if (!Files.isRegularFile(p)) continue
                if (shouldSkip(root, p)) continue
                val name = p.fileName.toString()
                if (!(name.endsWith(".md") || name.endsWith(".toml"))) continue
                out += parseOne(root, p)
            }
        }
        out
    }

    suspend fun scanCalendar(rootDir: File, calendarId: String): List<ParseResult> =
        withContext(Dispatchers.IO) {
            val sub = File(rootDir, "calendars/$calendarId")
            if (!sub.isDirectory) return@withContext emptyList()
            scanAll(sub)
        }

    /**
     * Parse a single file by absolute path, applying the same skip and
     * decode rules as [scanAll]. Used by the incremental indexer
     * (Phase D.3) to avoid re-walking the whole tree on small diffs.
     * Returns `null` if the file would have been skipped by the
     * walker (hidden, attachments, unrecognised extension).
     */
    suspend fun parseSingle(rootDir: File, absolutePath: Path): ParseResult? =
        withContext(Dispatchers.IO) {
            val root = rootDir.toPath().toAbsolutePath().normalize()
            val p = absolutePath.toAbsolutePath().normalize()
            if (!Files.isRegularFile(p)) return@withContext null
            if (shouldSkip(root, p)) return@withContext null
            val name = p.fileName.toString()
            if (!(name.endsWith(".md") || name.endsWith(".toml"))) return@withContext null
            parseOne(root, p)
        }

    private fun shouldSkip(root: Path, p: Path): Boolean {
        val rel = root.relativize(p).toString().replace('\\', '/')
        if (rel.startsWith(".git/") || rel == ".git") return true
        if (rel.startsWith("attachments/")) return true
        if (rel.startsWith("reviews/")) return true
        if (rel.startsWith("feedback/")) return true
        if (rel.startsWith(".strictlykeptboy/")) {
            // Only allow the two meta files through.
            return !(rel == ".strictlykeptboy/repo.toml" || rel == ".strictlykeptboy/schema.toml")
        }
        if (rel.startsWith(".")) return true
        // Known root-level non-entity files emitted by RepoBootstrap (DM-G).
        if (rel == "AGENTS.md" || rel == "CLAUDE.md" || rel == "README.md" || rel == "identity.toml" ||
            rel == "mode.toml" || rel == "MIGRATION-NOTES.md") return true
        return false
    }

    private fun parseOne(root: Path, p: Path): ParseResult {
        val rel = root.relativize(p).toString().replace('\\', '/')
        val text = try {
            String(Files.readAllBytes(p), Charsets.UTF_8)
        } catch (t: Throwable) {
            return ParseResult.Failed(p, "read failed: ${t.message}")
        }

        // Pure-TOML metadata files: no frontmatter fences.
        if (rel == ".strictlykeptboy/repo.toml" || rel == ".strictlykeptboy/schema.toml" ||
            rel.endsWith("/calendar.toml") || rel.endsWith("/todolist.toml")
        ) {
            return try {
                val table = TomlReader.parse(text)
                val doc = FrontmatterDoc(table, body = "")
                ParseResult.Success(decodeMetaByPath(rel, doc), p)
            } catch (t: Throwable) {
                ParseResult.Failed(p, "toml parse failed: ${t.message}")
            }
        }

        if (!rel.endsWith(".md")) return ParseResult.Failed(p, "not a recognised entity path")

        val doc = FrontmatterReader.parse(text)
        if (doc.kind != FrontmatterDoc.Kind.Ok) {
            return ParseResult.Failed(p, "frontmatter ${doc.kind}")
        }
        return try {
            ParseResult.Success(decodeByPath(rel, doc), p)
        } catch (t: Throwable) {
            ParseResult.Failed(p, "entity decode failed: ${t.message}")
        }
    }

    private fun decodeByPath(rel: String, doc: FrontmatterDoc): TypedEntity {
        // Use the path bucket as the implicit discriminator first; fall
        // back to the `kind` frontmatter field for paths that don't
        // unambiguously imply a kind (e.g. journal/).
        val segs = rel.split('/')
        return when {
            segs.size >= 2 && segs[0] == "calendars" && segs.contains("events") -> Event.fromDoc(doc)
            segs.size >= 2 && segs[0] == "calendars" && segs.contains("recurrences") ->
                RecurrenceRule.fromDoc(doc)
            segs.size >= 2 && segs[0] == "calendars" && segs.contains("exceptions") -> {
                val calId = segs.getOrNull(1)
                Exception.fromDoc(doc, calId)
            }
            segs.size >= 2 && segs[0] == "calendars" && segs.contains("deviations") ->
                Deviation.fromDoc(doc)
            segs.size >= 2 && segs[0] == "todolists" && segs.contains("tasks") -> Task.fromDoc(doc)
            segs.size >= 2 && segs[0] == "todolists" && segs.contains("standing") ->
                StandingTask.fromDoc(doc)
            segs.size >= 2 && segs[0] == "todolists" && segs.contains("recurrences") ->
                TaskRecurrenceRaw.fromDoc(doc)
            segs.size >= 2 && segs[0] == "overrides" -> Override.fromDoc(doc)
            segs.size >= 2 && segs[0] == "identities" -> Identity.fromDoc(doc)
            segs.size >= 2 && segs[0] == "journal" -> JournalEntry.fromDoc(doc)
            else -> RawEntity(
                header = EntityHeader.readFrom(doc.frontmatter),
                rawTable = doc.frontmatter,
                body = doc.body,
                rawKind = doc.frontmatter.getString("kind"),
            )
        }
    }

    private fun decodeMetaByPath(rel: String, doc: FrontmatterDoc): TypedEntity =
        // We don't have typed Calendar / Todolist meta data classes yet — surface
        // as RawEntity carrying the table. Phase C v1 scope-trim, documented in
        // docs/plans/main.md Phase C ticking notes.
        RawEntity(
            header = EntityHeader(
                schemaVersion = doc.frontmatter.getInt("schema_version") ?: 1,
                id = doc.frontmatter.getString("id") ?: rel,
                createdAt = doc.frontmatter.getDateLike("created_at") ?: "1970-01-01T00:00:00+00:00",
                updatedAt = doc.frontmatter.getDateLike("updated_at") ?: "1970-01-01T00:00:00+00:00",
                author = doc.frontmatter.getString("author") ?: "unknown",
            ),
            rawTable = doc.frontmatter,
            body = doc.body,
            rawKind = doc.frontmatter.getString("kind"),
        )
}

/**
 * Placeholder typed decoder for task recurrences (DM-B.8). Phase C v1
 * surfaces these as RawEntity since the resolver (Phase E) needs the
 * RRULE expansion logic; the typed shape lands when that ships.
 */
private object TaskRecurrenceRaw {
    fun fromDoc(doc: FrontmatterDoc): TypedEntity = RawEntity(
        header = EntityHeader.readFrom(doc.frontmatter),
        rawTable = doc.frontmatter,
        body = doc.body,
        rawKind = doc.frontmatter.getString("kind"),
    )
}
