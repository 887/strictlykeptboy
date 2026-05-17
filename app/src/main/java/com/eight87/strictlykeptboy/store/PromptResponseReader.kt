package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * Round 2.27 / Phase B.3 — pure-function reader over keeper-prompt
 * response files.
 *
 * Walks `<repoRoot>/calendars/<calId>/cage-check-responses/<ruleId>/`
 * for `*.md` files
 * and returns the set of [LocalDate]s that already carry a response.
 * The projector uses the result to drop closed prompt instances.
 *
 * Per D-2.27.g, the canonical date source is the
 * `prompt_instance_date` frontmatter scalar. The filename
 * (`<yyyy-MM-dd>.md`) is the documented fallback when the
 * frontmatter date is missing — that path stays tolerant so
 * hand-edited files still close their instance.
 *
 * Missing directory / empty directory → empty set (no throw). The
 * reader never raises on individual malformed files; it just skips
 * them.
 *
 * SOLID:
 *  - **S:** Only reads response-file dates — no projector wiring, no
 *    cache mutation.
 *  - **D:** Pure over [Path] / FS; consumers inject the lambda into
 *    [com.eight87.strictlykeptboy.ui.tasks.FromEventsProjector].
 */
object PromptResponseReader {

    fun listAnsweredInstances(
        repoRoot: Path,
        calId: String,
        ruleId: String,
    ): Set<LocalDate> {
        val dir = repoRoot
            .resolve("calendars")
            .resolve(calId)
            .resolve("cage-check-responses")
            .resolve(ruleId)
        if (!Files.isDirectory(dir)) return emptySet()
        val out = mutableSetOf<LocalDate>()
        Files.newDirectoryStream(dir, "*.md").use { stream ->
            for (path in stream) {
                if (!Files.isRegularFile(path)) continue
                val date = readDate(path) ?: continue
                out += date
            }
        }
        return out
    }

    private fun readDate(path: Path): LocalDate? {
        val text = runCatching {
            String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        }.getOrNull()
        if (text != null) {
            val doc = runCatching { FrontmatterReader.parse(text) }.getOrNull()
            val raw = doc?.frontmatter?.getLocalDate("prompt_instance_date")
            if (raw != null) {
                val parsed = runCatching { LocalDate.parse(raw) }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        // Filename fallback: `<yyyy-MM-dd>.md`.
        val name = path.fileName.toString().removeSuffix(".md")
        return runCatching { LocalDate.parse(name) }.getOrNull()
    }
}
