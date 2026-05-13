package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

/**
 * Phase DDD.2 / DM-Z.1 / DM-Z.3 — review-feed writer.
 *
 * Materializes `reviews/<commit-sha>/reviewable_change.md` (boy's repo,
 * strictly-kept mode only) — auto-summary + collapsed Markdown diff
 * hunks + empty `responses/` subdir. Register-aware auto-summary uses
 * the current `identity.toml` praise term.
 *
 * SOLID:
 *  - **S:** This file only writes the review entry; the diff-hunk
 *    collection + the JGit post-commit callback live with the caller.
 *  - **L:** [PathFamily] sealed enumeration is total over the changed-
 *    path family enumeration in [classifyPath].
 *  - **D:** Takes [IdentityTomlData] by value; no I/O dependencies.
 */
object ReviewFeedWriter {

    /** DM-Z.3 changed-path families — total over all paths in our schema. */
    enum class PathFamily {
        EventAdd, EventEdit, EventDelete,
        Recurrence, Deviation, Override,
        Attachment, ModeFlip, IdentityEdit,
        Calendar, Todolist, Task,
        Other,
    }

    data class ChangedPath(val path: String, val family: PathFamily)

    /**
     * @param commitSha the commit being reviewed (boy's commit)
     * @param author display label for the boy (typically the praise term)
     * @param timestamp ISO-8601 OffsetDateTime
     * @param changedPaths classification of every changed path
     * @param diffHunks already-rendered hunks; the writer emits them inside
     *                  a collapsed Markdown `<details>` block
     */
    data class Entry(
        val commitSha: String,
        val author: String,
        val timestamp: String,
        val changedPaths: List<ChangedPath>,
        val diffHunks: String,
    )

    /**
     * DM-Z.3 — maps changed-path families to register-aware blurbs using
     * the boy's praise term.
     */
    fun autoSummary(entry: Entry, identity: IdentityTomlData): String {
        val term = identity.praiseTerm
        if (entry.changedPaths.isEmpty()) return "$term made an empty commit."
        // Aggregate count by family so the blurb reads naturally.
        val byFamily = entry.changedPaths.groupingBy { it.family }.eachCount()
        val pieces = mutableListOf<String>()
        byFamily.forEach { (family, n) ->
            val plural = if (n == 1) "" else "s"
            pieces += when (family) {
                PathFamily.EventAdd -> "added $n event$plural"
                PathFamily.EventEdit -> "moved/edited $n event$plural"
                PathFamily.EventDelete -> "deleted $n event$plural"
                PathFamily.Recurrence -> "edited $n recurrence rule$plural"
                PathFamily.Deviation -> "logged $n deviation$plural"
                PathFamily.Override -> "added $n override$plural"
                PathFamily.Attachment -> "attached $n file$plural"
                PathFamily.ModeFlip -> "flipped the mode"
                PathFamily.IdentityEdit -> "tweaked $term identity"
                PathFamily.Calendar -> "touched $n calendar$plural"
                PathFamily.Todolist -> "touched $n todolist$plural"
                PathFamily.Task -> "touched $n task$plural"
                PathFamily.Other -> "touched $n file$plural"
            }
        }
        val joined = when {
            pieces.size == 1 -> pieces[0]
            pieces.size == 2 -> "${pieces[0]} and ${pieces[1]}"
            else -> pieces.dropLast(1).joinToString(", ") + ", and " + pieces.last()
        }
        return "$term $joined."
    }

    /** Classify a changed path into a [PathFamily]. */
    fun classifyPath(path: String): PathFamily = when {
        path == ModeTomlData.FILE_NAME -> PathFamily.ModeFlip
        path == IdentityTomlData.FILE_NAME -> PathFamily.IdentityEdit
        path.startsWith("calendars/") && path.endsWith("calendar.toml") -> PathFamily.Calendar
        path.startsWith("todolists/") && path.endsWith("todolist.toml") -> PathFamily.Todolist
        path.contains("/events/") -> PathFamily.EventEdit
        path.contains("/recurrences/") -> PathFamily.Recurrence
        path.contains("/deviations/") -> PathFamily.Deviation
        path.contains("/overrides/") -> PathFamily.Override
        path.startsWith("attachments/") -> PathFamily.Attachment
        path.contains("/tasks/") -> PathFamily.Task
        else -> PathFamily.Other
    }

    /**
     * Write the `reviews/<commitSha>/reviewable_change.md` + ensure
     * `responses/` subdir exists. Returns the path of the written entry.
     */
    fun writeReviewableChange(
        repoRoot: Path,
        entry: Entry,
        identity: IdentityTomlData,
    ): Path {
        val dir = repoRoot.resolve("reviews/${entry.commitSha}")
        Files.createDirectories(dir.resolve("responses"))
        val target = dir.resolve("reviewable_change.md")

        val frontmatter = TomlTable().apply {
            putString("kind", "reviewable_change")
            putString("commit_sha", entry.commitSha)
            putString("author", entry.author)
            putOffsetDateTime("timestamp", entry.timestamp)
            putStringArray("changed_paths", entry.changedPaths.map { it.path })
            putString("auto_summary", autoSummary(entry, identity))
        }
        val body = buildString {
            append('\n')
            append(autoSummary(entry, identity))
            append("\n\n")
            append("<details><summary>diff</summary>\n\n```diff\n")
            append(entry.diffHunks)
            if (!entry.diffHunks.endsWith("\n")) append('\n')
            append("```\n\n</details>\n")
        }
        val doc = FrontmatterDoc(frontmatter, body)
        Files.write(target, FrontmatterWriter.serialize(doc).toByteArray(StandardCharsets.UTF_8))
        return target
    }
}

/**
 * Phase DDD.3 / DM-Z.2 — dom-response writer.
 *
 * The dom writes responses in their OWN repo per HV-Q.2.4:
 * `reviews/<commit-sha>/responses/<dom-fingerprint>-<timestamp>.md`.
 * Cross-repo resolver (Phase YY) surfaces these back to the boy's
 * per-commit feedback feed.
 */
object ReviewResponseWriter {

    /**
     * DM-Z.2 + UI-SS.2 canonical reaction set. Ordered to match the
     * 9-token reaction picker layout.
     */
    val REACTIONS: List<String> = listOf(
        "locked",
        "collar",
        "good-boy",
        "paw",
        "heart",
        "fire",
        "thumbsup",
        "🦇",
        "smirk",
    )

    data class Response(
        val commitSha: String,
        val reactions: List<String>,
        val responderFingerprint: String,
        val responderLabel: String,
        val created: String = OffsetDateTime.now().withNano(0).toString(),
        val bodyMarkdown: String = "",
    ) {
        init {
            reactions.forEach {
                require(it in REACTIONS) { "Unknown reaction: $it (DM-Z.2 / UI-SS.2)" }
            }
            require(responderFingerprint.isNotBlank()) { "responderFingerprint required" }
        }

        /** DM-Z.4 cute-coded LGTM: empty body + `good-boy` reaction only. */
        val isCuteCodedLgtm: Boolean
            get() = bodyMarkdown.isBlank() && reactions == listOf("good-boy")
    }

    fun write(domRepoRoot: Path, response: Response): Path {
        val dir = domRepoRoot.resolve("reviews/${response.commitSha}/responses")
        Files.createDirectories(dir)
        // Replace ':' / '+' in timestamp for filename-safety while keeping ordering.
        val tsSafe = response.created.replace(":", "").replace("+", "p")
        val fname = "${response.responderFingerprint}-$tsSafe.md"
        val target = dir.resolve(fname)
        val fm = TomlTable().apply {
            putString("kind", "review_response")
            putString("commit_sha", response.commitSha)
            putStringArray("reactions", response.reactions)
            putString("responder_fingerprint", response.responderFingerprint)
            putString("responder_label", response.responderLabel)
            putOffsetDateTime("created", response.created)
            if (response.isCuteCodedLgtm) putBool("cute_coded_lgtm", true)
        }
        val doc = FrontmatterDoc(fm, body = "\n" + response.bodyMarkdown.ifBlank { "" } + "\n")
        Files.write(target, FrontmatterWriter.serialize(doc).toByteArray(StandardCharsets.UTF_8))
        return target
    }
}
