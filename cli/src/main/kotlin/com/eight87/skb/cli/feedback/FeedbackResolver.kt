package com.eight87.skb.cli.feedback

import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase YY.5 / FB-E — cross-repo feedback aggregator.
 *
 * On render of an event/task/journal/bonus, the viewer's repo asks this
 * resolver: "what feedback exists for [targetGlobalId] across every repo
 * I'm allowed to see?". The answer pulls from EVERY registered repo
 * that passes [RepoRegistry.allVisibleTo] (the chokepoint enforcing
 * FB-F isolation).
 *
 * FB-F.2 LOCK: aggregation count masking is FORBIDDEN. Isolated sources
 * are structurally invisible — never "N hidden". This resolver only
 * sees visible-to-viewer repos; isolated repos don't even appear in the
 * iteration set, so there's nothing to count-mask.
 *
 * SOLID.S: this file aggregates feedback only. Memoization +
 * cache-invalidation lives outside (the Android indexer hook, or a
 * caller-supplied memo). The Phase YY.5 LOCK says memoize on
 * `(targetGlobalId, set-of-visible-repo-HEADs)` — that's the caller's
 * job because the head-set is provided by the host (CLI does no
 * memoization in-process; the Android `:app` does).
 *
 * SOLID.D: dependencies are passed in — [RepoRegistry] + the [viewerFp].
 * Concrete file IO lives behind [FeedbackPath].
 */
class FeedbackResolver(
  private val registry: RepoRegistry,
  private val viewerFp: String,
) {

  /**
   * Aggregated feedback for one target. Reactions are grouped by token;
   * comments are returned in `created` order; threads carry the
   * `reply_to` linkage (linear within a thread per FB-E.2 LOCK).
   */
  data class AggregatedFeedback(
    val target: GlobalId,
    val reactionsByToken: Map<String, List<Author>>,
    val comments: List<Comment>,
    val threads: List<Thread>,
  )

  data class Author(
    val personId: String,
    val sourceRepoFingerprint: String,
    /**
     * FB-E.5 LOCK: the *receiving* repo's local label for the source
     * repo. Privacy-preserving — the sub can label dom's repo whatever
     * they like and the dom never sees the label.
     */
    val sourceRepoDisplayName: String,
  )

  data class Comment(
    val feedbackId: String,
    val author: Author,
    val body: String,
    val created: String,
    val replyTo: String?,
  )

  /** A root comment plus its (linear) replies in `created` ascending order. */
  data class Thread(val root: Comment, val replies: List<Comment>)

  fun aggregate(target: GlobalId): AggregatedFeedback {
    val visible = registry.allVisibleTo(viewerFp)
    val all = mutableListOf<Pair<RepoRegistry.RegisteredRepo, FeedbackFile>>()
    for (repo in visible) {
      val dir = FeedbackPath.directory(repo.localPath, target)
      if (!Files.isDirectory(dir)) continue
      Files.list(dir).use { stream ->
        stream
          .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
          .forEach { p ->
            val fb = runCatching { FeedbackFile.parse(Files.readString(p)) }.getOrNull() ?: return@forEach
            if (fb.target == target) all.add(repo to fb)
          }
      }
    }

    // Group reactions by token.
    val reactionsByToken = mutableMapOf<String, MutableList<Author>>()
    val comments = mutableListOf<Comment>()
    for ((repo, fb) in all) {
      val author = Author(fb.author, repo.fingerprint, repo.displayName)
      for (token in fb.reactions) {
        reactionsByToken.getOrPut(token) { mutableListOf() }.add(author)
      }
      if (fb.body.isNotBlank() || fb.replyTo != null) {
        comments.add(Comment(fb.id, author, fb.body, fb.created, fb.replyTo))
      }
    }
    val sortedComments = comments.sortedBy { it.created }

    // Thread roots = comments with replyTo == null. Replies grouped under the parent id (linear).
    val roots = sortedComments.filter { it.replyTo == null }
    val byParent = sortedComments.filter { it.replyTo != null }.groupBy { it.replyTo!! }
    val threads = roots.map { root ->
      Thread(root, replies = byParent[root.feedbackId].orEmpty())
    }

    return AggregatedFeedback(
      target = target,
      reactionsByToken = reactionsByToken.mapValues { it.value.toList() },
      comments = sortedComments,
      threads = threads,
    )
  }

  /**
   * FB-D.3 — list ALL feedback files for the target across visible
   * repos. Used by `skb react list --target ...`.
   *
   * Returns pairs of (repo entry, feedback file) so the CLI can render
   * `from "<display>"` chips per FB-E.5.
   */
  fun listFeedback(target: GlobalId): List<Pair<RepoRegistry.RegisteredRepo, FeedbackFile>> {
    val visible = registry.allVisibleTo(viewerFp)
    val out = mutableListOf<Pair<RepoRegistry.RegisteredRepo, FeedbackFile>>()
    for (repo in visible) {
      val dir = FeedbackPath.directory(repo.localPath, target)
      if (!Files.isDirectory(dir)) continue
      Files.list(dir).use { stream ->
        stream
          .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
          .forEach { p ->
            val fb = runCatching { FeedbackFile.parse(Files.readString(p)) }.getOrNull() ?: return@forEach
            if (fb.target == target) out.add(repo to fb)
          }
      }
    }
    return out.sortedBy { it.second.created }
  }
}

/** Find a feedback file by id across a repo's own feedback tree. */
internal fun findFeedbackByIdInRepo(repoRoot: Path, feedbackId: String): FeedbackFile? =
  FeedbackPath.walkAll(repoRoot)
    .mapNotNull { runCatching { FeedbackFile.parse(Files.readString(it)) }.getOrNull() }
    .firstOrNull { it.id == feedbackId }
