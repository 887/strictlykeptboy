package com.eight87.strictlykeptboy.store

import java.io.File

/**
 * Round 2.24 / D-2.24.b — shared reader for `<repoRoot>/.strictlykeptboy/repo.toml`.
 *
 * Pulled out of [com.eight87.strictlykeptboy.demo.RichDemoRegistrar] so
 * the demo registrar is not the canonical entry point for repo-meta
 * lookup. The renderer, Together-tab tz pickers, and the future
 * display-tz resolver all read through this helper.
 *
 * SOLID — single responsibility: file-on-disk → [RepoMetaSnapshot]. The
 * reader does no validation of zone strings; consumers handle malformed
 * `default_tz_id` values via `ZoneId.of(...)` fallback in their own
 * layer (per the codec-level decision in D-2.24.a — don't reject
 * hand-edited files at the parse seam).
 */
object RepoMetaReader {

    /**
     * Read `<repoRoot>/.strictlykeptboy/repo.toml` and return its
     * parsed snapshot, or `null` if the file is missing OR the TOML is
     * malformed enough that parsing throws. Never throws.
     */
    fun read(repoRoot: File): RepoMetaSnapshot? {
        val file = File(repoRoot, ".strictlykeptboy/repo.toml")
        if (!file.isFile) return null
        val table = runCatching { TomlReader.parse(file.readText(Charsets.UTF_8)) }
            .getOrNull() ?: return null
        return RepoMetaSnapshot(
            id = table.scalar("id"),
            name = table.scalar("name"),
            defaultCalendarId = table.scalar("default_calendar")
                ?: table.scalar("default_calendar_id"),
            defaultTodolistId = table.scalar("default_todolist")
                ?: table.scalar("default_todolist_id"),
            emoji = table.scalar("emoji"),
            defaultTzId = table.scalar("default_tz_id")?.takeIf { it.isNotBlank() },
            raw = table,
        )
    }

    private fun TomlTable.scalar(key: String): String? =
        (scalars[key] as? TomlValue.Str)?.value
}

/**
 * In-memory view of the fields strictlykeptboy reads from
 * `.strictlykeptboy/repo.toml`. Additive — new fields default to
 * `null` so older repos stay forward-compatible.
 */
data class RepoMetaSnapshot(
    val id: String?,
    val name: String?,
    val defaultCalendarId: String?,
    val defaultTodolistId: String?,
    val emoji: String?,
    /** Round 2.24 / D-2.24.b — repo-default display tz (`default_tz_id`). */
    val defaultTzId: String?,
    /** Raw parsed table for callers needing fields beyond the typed set above. */
    val raw: TomlTable,
)
