package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.GitOps
import com.eight87.skb.cli.core.Uuid7
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase YY.3 / FB-C — feedback file writer with validation + auto-commit.
 *
 * One file per (author, target, reply_to) tuple — re-reacting overwrites
 * the same file. Deletion = `git rm`. No tombstones.
 *
 * Validation (FB-C.4):
 *  - target must parse as `<16-hex>:<uuid7>`
 *  - reactions must be unique
 *  - refuse no-op: empty reactions AND empty body AND no reply_to
 *  - cross-repo replies forbidden (parent must live in this repo)
 *
 * SOLID.S: validation + write + commit-message composition only.
 * SOLID.D: callers inject [GitOps]; production wiring is the same as
 * the rest of the CLI (`commands/Common.kt::gitOps()`).
 */
class FeedbackWriter(
  private val repoRoot: Path,
  private val authorPersonId: String,
  private val authorRepoFingerprint: String,
  private val authorName: String,
  private val authorEmail: String,
  private val gitOps: GitOps,
  private val clock: () -> String,
) {

  data class Outcome(
    val file: Path,
    val feedbackId: String,
    val replaced: Boolean,
    val commitSha: String?,
  )

  /**
   * FB-D.1 — create or update the active-identity's feedback file for
   * the target. Returns the absolute path + commit SHA.
   */
  fun react(
    target: GlobalId,
    targetKind: TargetKind,
    reactions: List<String>,
    body: String,
    replyTo: String?,
    dryRun: Boolean = false,
  ): Outcome {
    validateInputs(target, reactions, body, replyTo)

    val existing = findExistingByAuthor(target, replyTo)
    val now = clock()
    val id = existing?.id ?: Uuid7.generate()
    val created = existing?.created ?: now
    val updated = if (existing != null) now else null
    val feedback = FeedbackFile(
      id = id,
      target = target,
      targetKind = targetKind,
      author = authorPersonId,
      authorRepo = authorRepoFingerprint,
      created = created,
      updated = updated,
      reactions = reactions,
      replyTo = replyTo,
      body = body,
    )
    val file = FeedbackPath.file(repoRoot, target, id)
    val content = feedback.serialize()

    if (dryRun) {
      return Outcome(file, id, existing != null, null)
    }

    Files.createDirectories(file.parent)
    AtomicWriter.writeUtf8(file, content)
    val verb = if (existing != null) "update" else "add"
    val short = "${target.repoFingerprint.take(8)}:${target.entityUuid.take(8)}"
    val sha = gitOps.addAndCommit(
      repoRoot = repoRoot,
      files = listOf(repoRoot.relativize(file).toString()),
      message = "$verb feedback for $short",
      authorName = authorName,
      authorEmail = authorEmail,
    )
    return Outcome(file, id, existing != null, sha)
  }

  /** FB-D.2 — `git rm` the active-identity's feedback file. No-op if absent. */
  fun remove(target: GlobalId, replyTo: String?, dryRun: Boolean = false): Outcome? {
    val existing = findExistingByAuthor(target, replyTo) ?: return null
    val file = FeedbackPath.file(repoRoot, target, existing.id)
    if (dryRun) return Outcome(file, existing.id, replaced = true, commitSha = null)
    Files.deleteIfExists(file)
    val short = "${target.repoFingerprint.take(8)}:${target.entityUuid.take(8)}"
    val sha = gitOps.addAndCommit(
      repoRoot = repoRoot,
      files = listOf(repoRoot.relativize(file).toString()),
      message = "remove feedback for $short",
      authorName = authorName,
      authorEmail = authorEmail,
    )
    return Outcome(file, existing.id, replaced = true, commitSha = sha)
  }

  // -------- internals --------

  private fun validateInputs(target: GlobalId, reactions: List<String>, body: String, replyTo: String?) {
    if (reactions.size != reactions.distinct().size) {
      throw CliError(ExitCode.USAGE, "reactions list contains duplicates: $reactions")
    }
    if (reactions.isEmpty() && body.isBlank() && replyTo == null) {
      throw CliError(ExitCode.USAGE, "refusing to write empty feedback (no reactions, no body, no reply_to)")
    }
    // FB-C.4 LOCK: cross-repo replies forbidden. The parent must live
    // in *this* repo. We check by scanning this repo's feedback tree
    // for any file with id == replyTo.
    if (replyTo != null && findByIdInThisRepo(replyTo) == null) {
      throw CliError(
        ExitCode.NOT_FOUND,
        "reply_to feedback id $replyTo not found in this repo",
        hint = "replies must live in the same repo as their parent (FB-C.4 LOCK)",
      )
    }
    @Suppress("UNUSED_EXPRESSION") target
  }

  /**
   * Find the existing feedback file owned by [authorPersonId] for this
   * (target, reply_to) tuple. There can only be one per FB-C LOCK.
   */
  internal fun findExistingByAuthor(target: GlobalId, replyTo: String?): FeedbackFile? {
    val dir = FeedbackPath.directory(repoRoot, target)
    if (!Files.isDirectory(dir)) return null
    return Files.list(dir).use { stream ->
      stream
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
        .map { runCatching { FeedbackFile.parse(Files.readString(it)) }.getOrNull() }
        .filter { it != null && it.author == authorPersonId && it.replyTo == replyTo }
        .findFirst()
        .orElse(null)
    }
  }

  internal fun findByIdInThisRepo(feedbackId: String): FeedbackFile? {
    return FeedbackPath.walkAll(repoRoot)
      .mapNotNull { runCatching { FeedbackFile.parse(Files.readString(it)) }.getOrNull() }
      .firstOrNull { it.id == feedbackId }
  }
}

@Suppress("unused") private fun anchorImports() { Frontmatter }
