package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.MiniToml
import com.eight87.skb.cli.core.TomlTable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase YY.3 / FB-C — feedback file schema, path builder, parser.
 *
 * On-disk path in the feedbacker's repo:
 *   `feedback/<target-fingerprint>/<target-entity-uuid>/<feedback-uuid>.md`
 *
 * Frontmatter shape (FB-C.1 LOCK):
 *   schema_version, id, target, target_kind, author, author_repo,
 *   created, [updated], reactions, [reply_to], plus markdown body.
 *
 * SOLID.S: this file is pure schema (data class + path builder + (de)
 * serialization). The atomic-write + commit hooks live in
 * [FeedbackWriter]; indexing lives in [FeedbackIndex].
 */
data class FeedbackFile(
  val id: String,                       // UUIDv7
  val target: GlobalId,                 // <repo-fp>:<entity-uuid>
  val targetKind: TargetKind,
  val author: String,                   // identities/<author>.md
  val authorRepo: String,               // this repo's fingerprint (16 hex)
  val created: String,                  // ISO-8601 offset
  val updated: String? = null,
  val reactions: List<String> = emptyList(),
  val replyTo: String? = null,          // feedback-uuid of parent
  val body: String = "",
) {
  init {
    require(GlobalId.isValidEntityUuid(id)) { "feedback id must be a UUIDv7: $id" }
    require(GlobalId.isValidFingerprint(authorRepo)) { "author_repo must be a 16-hex fingerprint: $authorRepo" }
    if (replyTo != null) require(GlobalId.isValidEntityUuid(replyTo)) { "reply_to must be a UUIDv7: $replyTo" }
  }

  fun serialize(): String {
    val t = TomlTable().apply {
      putInt("schema_version", 1)
      putString("id", id)
      putString("target", target.toString())
      putString("target_kind", targetKind.wire)
      putString("author", author)
      putString("author_repo", authorRepo)
      putDateTime("created", created)
      updated?.let { putDateTime("updated", it) }
      putStringArray("reactions", reactions)
      replyTo?.let { putString("reply_to", it) }
    }
    return Frontmatter.serialize(t, body)
  }

  companion object {
    fun parse(text: String): FeedbackFile? {
      val (table, body) = Frontmatter.parse(text)
      val id = table.getString("id") ?: return null
      val target = GlobalId.parse(table.getString("target") ?: return null) ?: return null
      val tkind = TargetKind.fromWire(table.getString("target_kind") ?: return null) ?: return null
      val author = table.getString("author") ?: return null
      val authorRepo = table.getString("author_repo") ?: return null
      val created = table.getDateTime("created") ?: table.getString("created") ?: return null
      val updated = table.getDateTime("updated") ?: table.getString("updated")
      val reactions = table.getStringArray("reactions") ?: emptyList()
      val replyTo = table.getString("reply_to")
      // Frontmatter serializer always emits a trailing newline; the
      // serialized body is exactly `<body>\n` so we strip a single
      // trailing newline on parse for a deterministic round-trip.
      val normalizedBody = if (body.endsWith("\n")) body.dropLast(1) else body
      return runCatching {
        FeedbackFile(id, target, tkind, author, authorRepo, created, updated, reactions, replyTo, normalizedBody)
      }.getOrNull()
    }
  }
}

/**
 * FB-C target kinds. Sealed-style enum (Kotlin enum here suffices —
 * each variant is a label, not behaviour-bearing). New target kinds get
 * added by extending the enum + wire mapping; consumers `when`-exhaust.
 *
 * SOLID.O: feedback-targeting is open to new kinds via new enum entries
 * (e.g. recurring-exception). Adding one does NOT change existing
 * consumers' code path because the wire string lookup is centralised.
 */
enum class TargetKind(val wire: String) {
  EVENT("event"),
  TASK("task"),
  RECURRENCE("recurrence"),
  EXCEPTION("exception"),
  JOURNAL("journal"),
  BONUS("bonus"),
  FEEDBACK("feedback"); // reply

  companion object {
    fun fromWire(s: String): TargetKind? = values().firstOrNull { it.wire == s }
  }
}

/**
 * FB-C path builder. The feedback file lives in the FEEDBACKER's repo
 * (i.e., the one writing the reaction), addressed by the target
 * fingerprint and target entity UUID.
 */
object FeedbackPath {
  fun directory(repoRoot: Path, target: GlobalId): Path =
    repoRoot.resolve("feedback/${target.repoFingerprint}/${target.entityUuid}")

  fun file(repoRoot: Path, target: GlobalId, feedbackId: String): Path =
    directory(repoRoot, target).resolve("$feedbackId.md")

  /** Walk all feedback files under a repo. Returns absolute paths in stable lexical order. */
  fun walkAll(repoRoot: Path): List<Path> {
    val base = repoRoot.resolve("feedback")
    if (!Files.isDirectory(base)) return emptyList()
    return Files.walk(base).use { stream ->
      stream
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
        .sorted()
        .toList()
    }
  }
}

/**
 * Keep MiniToml import alive even if a refactor removes the only
 * reference inside this file. (Defensive; the parser uses TomlTable
 * downstream.)
 */
@Suppress("unused")
private fun anchorImports() { MiniToml; TomlTable() }
