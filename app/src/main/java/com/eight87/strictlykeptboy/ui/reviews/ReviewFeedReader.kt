package com.eight87.strictlykeptboy.ui.reviews

import com.eight87.strictlykeptboy.store.FrontmatterReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Round 2.23 Phase E (D-2.23.e) — Reviews data layer.
 *
 * Scans `reviews/<commit-sha>/reviewable_change.md` files across the
 * given repo roots (the counterpart to
 * [com.eight87.strictlykeptboy.store.ReviewFeedWriter]) and emits
 * [ReviewEntry] instances for [ReviewsPane] to render.
 *
 * The task brief said "feedback/" but the on-disk schema established
 * in Phase DDD.2 / DM-Z.3 is `reviews/` — we honour the actual
 * codebase schema and read from there. Empty repos yield empty lists;
 * malformed frontmatter rows are skipped silently (the validator
 * surfaces those elsewhere).
 *
 * SOLID:
 *  - **S:** Just scans + parses. No I/O scheduling, no UI.
 *  - **D:** Takes a [List<Path>] (repo roots) rather than depending
 *    on RepoRegistry directly so tests can seed temp dirs.
 */
object ReviewFeedReader {

    /**
     * Scan all repo roots and return combined entries sorted by
     * timestamp descending (newest first). The repo-root list comes
     * from the caller — typically `AppGraph.repoRegistry.activeRoots()`.
     */
    fun scan(repoRoots: List<Path>): List<ReviewEntry> {
        val out = mutableListOf<ReviewEntry>()
        for (root in repoRoots) {
            val reviewsDir = root.resolve("reviews")
            if (!Files.isDirectory(reviewsDir)) continue
            Files.newDirectoryStream(reviewsDir).use { commits ->
                for (commitDir in commits) {
                    if (!Files.isDirectory(commitDir)) continue
                    val file = commitDir.resolve("reviewable_change.md")
                    if (!Files.isRegularFile(file)) continue
                    val entry = parseEntry(file) ?: continue
                    out += entry
                }
            }
        }
        return out.sortedByDescending { it.timestamp }
    }

    private fun parseEntry(file: Path): ReviewEntry? {
        val text = try {
            String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            return null
        }
        val doc = FrontmatterReader.parse(text)
        val fm = doc.frontmatter
        val sha = fm.getString("commit_sha") ?: return null
        val author = fm.getString("author") ?: "anonymous"
        val ts = fm.getString("timestamp") ?: ""
        val summary = fm.getString("auto_summary")
            ?: doc.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: ""
        return ReviewEntry(
            commitSha = sha,
            author = author,
            timestamp = ts,
            autoSummary = summary,
            unread = true,
        )
    }
}
