package com.eight87.strictlykeptboy.ui.reviews

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Round 2.23 Phase E (D-2.23.e) — pin the cross-repo Reviews scanner.
 *
 * Seeds two temp repos with reviewable_change.md files written in the
 * exact frontmatter shape `ReviewFeedWriter` emits, then asserts the
 * combined list is returned (newest first).
 */
class ReviewFeedReaderTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun scans_two_repos_returns_combined_list_newest_first() {
        val repoA = tmp.newFolder("repoA").toPath()
        val repoB = tmp.newFolder("repoB").toPath()
        seed(repoA, "sha-aaa", "alice", "2026-05-15T10:00:00Z", "alice added 1 event")
        seed(repoA, "sha-bbb", "alice", "2026-05-16T10:00:00Z", "alice edited 1 event")
        seed(repoB, "sha-ccc", "bob",   "2026-05-14T10:00:00Z", "bob moved 1 event")

        val out = ReviewFeedReader.scan(listOf(repoA, repoB))

        assertEquals(3, out.size)
        // Newest first.
        assertEquals("sha-bbb", out[0].commitSha)
        assertEquals("sha-aaa", out[1].commitSha)
        assertEquals("sha-ccc", out[2].commitSha)
        assertEquals("alice edited 1 event", out[0].autoSummary)
        assertTrue(out[0].unread)
    }

    @Test fun missing_reviews_dir_yields_empty() {
        val repo = tmp.newFolder("emptyRepo").toPath()
        assertEquals(emptyList<ReviewEntry>(), ReviewFeedReader.scan(listOf(repo)))
    }

    @Test fun malformed_frontmatter_is_skipped() {
        val repo = tmp.newFolder("repo").toPath()
        val dir = repo.resolve("reviews/sha-zzz")
        Files.createDirectories(dir)
        Files.write(
            dir.resolve("reviewable_change.md"),
            "not a frontmatter document".toByteArray(StandardCharsets.UTF_8),
        )
        assertEquals(emptyList<ReviewEntry>(), ReviewFeedReader.scan(listOf(repo)))
    }

    private fun seed(
        root: java.nio.file.Path,
        sha: String,
        author: String,
        ts: String,
        summary: String,
    ) {
        val dir = root.resolve("reviews/$sha")
        Files.createDirectories(dir)
        val body = buildString {
            append("+++\n")
            append("""kind = "reviewable_change"""").append('\n')
            append("""commit_sha = "$sha"""").append('\n')
            append("""author = "$author"""").append('\n')
            append("""timestamp = "$ts"""").append('\n')
            append("""auto_summary = "$summary"""").append('\n')
            append("+++\n\n")
            append(summary).append('\n')
        }
        Files.write(dir.resolve("reviewable_change.md"), body.toByteArray(StandardCharsets.UTF_8))
    }
}
