package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.commands.CommitResult
import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.commands.gitOps
import com.eight87.skb.cli.commands.nowIso
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.IdentityResolver
import com.eight87.skb.cli.core.JsonEnvelope
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Path

/**
 * Phase YY.4 / FB-D — `skb react` + `skb comment` CLI surface.
 *
 *  - `skb react add --target <gid> --reactions heart,fire,locked [--body] [--reply-to]`
 *  - `skb react remove --target <gid> [--reply-to]`
 *  - `skb react list --target <gid>` — cross-repo, respects FB-F isolation.
 *  - `skb comment add --target <gid> --body "..." [--reply-to]`
 *  - `skb comment list --target <gid>` — thread-ordered.
 *
 * Target resolution helpers (FB-D.5):
 *   `--target <repo-fp>:<uuid>` for explicit cross-repo;
 *   `--target-event <uuid>` for "this repo" + event kind; same for
 *   task/journal/bonus.
 */
class ReactGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "react") {
  init { subcommands(ReactAdd(ctxOf), ReactRemove(ctxOf), ReactList(ctxOf)) }
  override fun run() = Unit
}

class CommentGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "comment") {
  init { subcommands(CommentAdd(ctxOf), CommentList(ctxOf)) }
  override fun run() = Unit
}

/**
 * `skb repo fingerprint` (FB-A.5) — prints this repo's fingerprint.
 * Lives in its own command for discoverability separate from `repo list`.
 */
class RepoFingerprintCommand(private val ctxOf: () -> CliContext) : CliktCommand(name = "repo-fingerprint") {
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val fp = RepoFingerprint.computeOrLoad(root)
      ?: throw CliError(ExitCode.NOT_FOUND, "repo has no commits yet — fingerprint requires the first commit (FB-A.3)")
    emitHuman(fp, ctx)
    emitJson(JsonEnvelope.success("repo.fingerprint", buildJsonObject { put("fingerprint", JsonPrimitive(fp)) }), ctx)
  }
}

/**
 * `skb repo registry list|isolate|unisolate` (FB-B.5).
 */
class RepoRegistryGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "repo-registry") {
  init { subcommands(RegList(ctxOf), RegIsolate(ctxOf), RegUnisolate(ctxOf)) }
  override fun run() = Unit
}

/** `skb ref set-write-back` (FB-H.6). */
class RefSetWriteBackCommand(private val ctxOf: () -> CliContext) : CliktCommand(name = "ref-set-write-back") {
  val url by option("--reference-url").required()
  val target by option("--target")
  val disable by option("--disable").flag()
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val effective = if (disable) null else target
      ?: throw CliError(ExitCode.USAGE, "must supply --target <fingerprint> or --disable")
    try {
      ReferencesWriteBack.setWriteBack(root, url, effective)
    } catch (e: IllegalArgumentException) {
      throw CliError(ExitCode.NOT_FOUND, e.message ?: "set-write-back failed")
    }
    emitHuman(if (effective == null) "✓ disabled write_back_target for $url" else "✓ set write_back_target=$effective for $url", ctx)
    emitJson(
      JsonEnvelope.success(
        "ref.set-write-back",
        buildJsonObject {
          put("url", JsonPrimitive(url))
          put("write_back_target", effective?.let { JsonPrimitive(it) } ?: JsonPrimitive(""))
          put("disabled", JsonPrimitive(effective == null))
        },
      ),
      ctx,
    )
  }
}

// --------------------- internals ---------------------

private fun parseReactions(raw: String?): List<String> {
  if (raw.isNullOrBlank()) return emptyList()
  val items = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
  val dups = items.groupingBy { it }.eachCount().filter { it.value > 1 }
  if (dups.isNotEmpty()) {
    throw CliError(ExitCode.USAGE, "duplicate reactions: ${dups.keys}")
  }
  return items
}

private fun resolveTarget(
  ctx: CliContext,
  explicit: String?,
  event: String?,
  task: String?,
  journal: String?,
  bonus: String?,
): Pair<GlobalId, TargetKind> {
  fun thisRepoGid(uuid: String, kind: TargetKind): Pair<GlobalId, TargetKind> {
    if (!GlobalId.isValidEntityUuid(uuid)) {
      throw CliError(ExitCode.USAGE, "--target-${kind.wire} must be a UUIDv7 (got: $uuid)")
    }
    val fp = RepoFingerprint.computeOrLoad(ctx.repoRoot())
      ?: throw CliError(ExitCode.NOT_FOUND, "this repo has no commits yet — feedback requires a fingerprint (FB-A.3)")
    return GlobalId(fp, uuid) to kind
  }

  val supplied = listOfNotNull(
    explicit?.let { "explicit" },
    event?.let { "event" },
    task?.let { "task" },
    journal?.let { "journal" },
    bonus?.let { "bonus" },
  )
  if (supplied.isEmpty()) throw CliError(ExitCode.USAGE, "must supply --target or --target-event/-task/-journal/-bonus")
  if (supplied.size > 1) throw CliError(ExitCode.USAGE, "supply exactly one of --target, --target-event, --target-task, --target-journal, --target-bonus")

  return when {
    explicit != null -> {
      val gid = GlobalId.parse(explicit) ?: throw CliError(ExitCode.USAGE, "--target must be <repo-fp>:<uuid7>, got: $explicit")
      // Kind is unknown for explicit global-ids unless one of --kind-event/-task/... also given.
      // Default to EVENT — FB-D.5 LOCK: the target-kind helpers are the precise path.
      gid to TargetKind.EVENT
    }
    event != null -> thisRepoGid(event, TargetKind.EVENT)
    task != null -> thisRepoGid(task, TargetKind.TASK)
    journal != null -> thisRepoGid(journal, TargetKind.JOURNAL)
    bonus != null -> thisRepoGid(bonus, TargetKind.BONUS)
    else -> error("unreachable")
  }
}

private fun openWriter(ctx: CliContext): Pair<FeedbackWriter, Path> {
  val root = ctx.repoRoot()
  val fp = RepoFingerprint.computeOrLoad(root)
    ?: throw CliError(ExitCode.NOT_FOUND, "repo has no commits yet — feedback requires a fingerprint (FB-A.3)")
  val identity = IdentityResolver.resolve(root)
  val writer = FeedbackWriter(
    repoRoot = root,
    authorPersonId = identity.id,
    authorRepoFingerprint = fp,
    authorName = identity.displayName,
    authorEmail = identity.email,
    gitOps = gitOps(),
    clock = ::nowIso,
  )
  return writer to root
}

// --------------------- subcommands ---------------------

private class ReactAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
  val target by option("--target")
  val targetEvent by option("--target-event")
  val targetTask by option("--target-task")
  val targetJournal by option("--target-journal")
  val targetBonus by option("--target-bonus")
  val reactionsOpt by option("--reactions")
  val body by option("--body")
  val replyTo by option("--reply-to")

  override fun run() {
    val ctx = ctxOf()
    val (gid, kind) = resolveTarget(ctx, target, targetEvent, targetTask, targetJournal, targetBonus)
    val reactions = parseReactions(reactionsOpt)
    val (writer, root) = openWriter(ctx)
    val outcome = writer.react(
      target = gid,
      targetKind = kind,
      reactions = reactions,
      body = body ?: "",
      replyTo = replyTo,
      dryRun = ctx.dryRun,
    )
    val rel = root.relativize(outcome.file).toString()
    val verb = if (outcome.replaced) "updated" else "added"
    emitHuman(if (ctx.dryRun) "DRY RUN: would write $rel" else "✓ $verb feedback at $rel", ctx)
    emitJson(
      JsonEnvelope.success(
        "react.add",
        buildJsonObject {
          put("file", JsonPrimitive(rel))
          put("feedback_id", JsonPrimitive(outcome.feedbackId))
          put("target", JsonPrimitive(gid.toString()))
          put("target_kind", JsonPrimitive(kind.wire))
          put("replaced", JsonPrimitive(outcome.replaced))
          put("dry_run", JsonPrimitive(ctx.dryRun))
          outcome.commitSha?.let { put("commit", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
    @Suppress("UNUSED_VARIABLE") val ensureSealed: CommitResult? = null
  }
}

private class ReactRemove(val ctxOf: () -> CliContext) : CliktCommand(name = "remove") {
  val target by option("--target")
  val targetEvent by option("--target-event")
  val targetTask by option("--target-task")
  val targetJournal by option("--target-journal")
  val targetBonus by option("--target-bonus")
  val replyTo by option("--reply-to")

  override fun run() {
    val ctx = ctxOf()
    val (gid, _) = resolveTarget(ctx, target, targetEvent, targetTask, targetJournal, targetBonus)
    val (writer, root) = openWriter(ctx)
    val outcome = writer.remove(gid, replyTo, dryRun = ctx.dryRun)
    if (outcome == null) {
      emitHuman("no feedback to remove (no-op)", ctx)
      emitJson(
        JsonEnvelope.success("react.remove", buildJsonObject {
          put("target", JsonPrimitive(gid.toString()))
          put("removed", JsonPrimitive(false))
        }),
        ctx,
      )
      return
    }
    val rel = root.relativize(outcome.file).toString()
    emitHuman(if (ctx.dryRun) "DRY RUN: would remove $rel" else "✓ removed feedback at $rel", ctx)
    emitJson(
      JsonEnvelope.success(
        "react.remove",
        buildJsonObject {
          put("file", JsonPrimitive(rel))
          put("target", JsonPrimitive(gid.toString()))
          put("removed", JsonPrimitive(true))
          outcome.commitSha?.let { put("commit", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
  }
}

private class ReactList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  val target by option("--target")
  val targetEvent by option("--target-event")
  val targetTask by option("--target-task")
  val targetJournal by option("--target-journal")
  val targetBonus by option("--target-bonus")

  override fun run() {
    val ctx = ctxOf()
    val (gid, _) = resolveTarget(ctx, target, targetEvent, targetTask, targetJournal, targetBonus)
    val registry = RepoRegistry.openAt(RepoRegistry.defaultStorePath(ctx.envAsMap()))
    val viewerFp = RepoFingerprint.computeOrLoad(ctx.repoRoot()) ?: throw CliError(ExitCode.NOT_FOUND, "viewer repo has no fingerprint yet")
    // Ensure viewer is registered so allVisibleTo always includes it.
    registry.register(viewerFp, ctx.repoRoot(), ctx.repoRoot().fileName?.toString() ?: viewerFp)
    val resolver = FeedbackResolver(registry, viewerFp)
    val pairs = resolver.listFeedback(gid)
    val items = pairs.map { (repo, fb) ->
      buildJsonObject {
        put("repo_fingerprint", JsonPrimitive(repo.fingerprint))
        put("repo_display_name", JsonPrimitive(repo.displayName))
        put("author", JsonPrimitive(fb.author))
        put("feedback_id", JsonPrimitive(fb.id))
        put("reactions", JsonArray(fb.reactions.map { JsonPrimitive(it) }))
        put("created", JsonPrimitive(fb.created))
        fb.updated?.let { put("updated", JsonPrimitive(it)) }
        put("body", JsonPrimitive(fb.body))
        fb.replyTo?.let { put("reply_to", JsonPrimitive(it)) }
      }
    }
    emitHuman(
      buildString {
        if (items.isEmpty()) appendLine("(no feedback)") else {
          appendLine("${items.size} feedback file(s) for $gid:")
          for ((repo, fb) in pairs) {
            appendLine("  ${fb.reactions.joinToString(",").ifEmpty { "(comment)" }}  by ${fb.author}  from \"${repo.displayName}\"  [${fb.created}]")
          }
        }
      }.trimEnd(),
      ctx,
    )
    emitJson(JsonEnvelope.success("react.list", buildJsonObject { put("items", JsonArray(items)) }), ctx)
  }
}

private class CommentAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
  val target by option("--target")
  val targetEvent by option("--target-event")
  val targetTask by option("--target-task")
  val targetJournal by option("--target-journal")
  val targetBonus by option("--target-bonus")
  val body by option("--body").required()
  val replyTo by option("--reply-to")

  override fun run() {
    val ctx = ctxOf()
    val (gid, kind) = resolveTarget(ctx, target, targetEvent, targetTask, targetJournal, targetBonus)
    val (writer, root) = openWriter(ctx)
    val outcome = writer.react(
      target = gid,
      targetKind = kind,
      reactions = emptyList(),
      body = body,
      replyTo = replyTo,
      dryRun = ctx.dryRun,
    )
    val rel = root.relativize(outcome.file).toString()
    emitHuman(if (ctx.dryRun) "DRY RUN: would write $rel" else "✓ wrote comment at $rel", ctx)
    emitJson(
      JsonEnvelope.success(
        "comment.add",
        buildJsonObject {
          put("file", JsonPrimitive(rel))
          put("feedback_id", JsonPrimitive(outcome.feedbackId))
          put("target", JsonPrimitive(gid.toString()))
          outcome.commitSha?.let { put("commit", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
  }
}

private class CommentList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  val target by option("--target")
  val targetEvent by option("--target-event")
  val targetTask by option("--target-task")
  val targetJournal by option("--target-journal")
  val targetBonus by option("--target-bonus")

  override fun run() {
    val ctx = ctxOf()
    val (gid, _) = resolveTarget(ctx, target, targetEvent, targetTask, targetJournal, targetBonus)
    val registry = RepoRegistry.openAt(RepoRegistry.defaultStorePath(ctx.envAsMap()))
    val viewerFp = RepoFingerprint.computeOrLoad(ctx.repoRoot()) ?: throw CliError(ExitCode.NOT_FOUND, "viewer repo has no fingerprint yet")
    registry.register(viewerFp, ctx.repoRoot(), ctx.repoRoot().fileName?.toString() ?: viewerFp)
    val resolver = FeedbackResolver(registry, viewerFp)
    val agg = resolver.aggregate(gid)
    emitHuman(
      buildString {
        if (agg.threads.isEmpty()) appendLine("(no comments)")
        for (t in agg.threads) {
          appendLine("• ${t.root.author.personId} from \"${t.root.author.sourceRepoDisplayName}\"  [${t.root.created}]")
          if (t.root.body.isNotBlank()) appendLine("    ${t.root.body.replace("\n", "\n    ")}")
          for (r in t.replies) {
            appendLine("  ↳ ${r.author.personId} from \"${r.author.sourceRepoDisplayName}\"  [${r.created}]")
            if (r.body.isNotBlank()) appendLine("      ${r.body.replace("\n", "\n      ")}")
          }
        }
      }.trimEnd(),
      ctx,
    )
    val items = agg.threads.map { t ->
      buildJsonObject {
        put("root_id", JsonPrimitive(t.root.feedbackId))
        put("author", JsonPrimitive(t.root.author.personId))
        put("source_repo", JsonPrimitive(t.root.author.sourceRepoDisplayName))
        put("body", JsonPrimitive(t.root.body))
        put("created", JsonPrimitive(t.root.created))
        put("replies", JsonArray(t.replies.map { r ->
          buildJsonObject {
            put("id", JsonPrimitive(r.feedbackId))
            put("author", JsonPrimitive(r.author.personId))
            put("source_repo", JsonPrimitive(r.author.sourceRepoDisplayName))
            put("body", JsonPrimitive(r.body))
            put("created", JsonPrimitive(r.created))
            put("reply_to", JsonPrimitive(r.replyTo ?: ""))
          }
        }))
      }
    }
    emitJson(JsonEnvelope.success("comment.list", buildJsonObject { put("threads", JsonArray(items)) }), ctx)
  }
}

private class RegList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  override fun run() {
    val ctx = ctxOf()
    val registry = RepoRegistry.openAt(RepoRegistry.defaultStorePath(ctx.envAsMap()))
    val entries = registry.list()
    emitHuman(
      buildString {
        if (entries.isEmpty()) appendLine("(no registered repos)") else {
          appendLine("${entries.size} registered repo(s):")
          for (e in entries) {
            appendLine("  ${e.fingerprint}  ${e.displayName}  ${e.localPath}")
            if (e.isolateFrom.isNotEmpty()) appendLine("    isolate_from: ${e.isolateFrom.joinToString(", ")}")
          }
        }
      }.trimEnd(),
      ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "repo-registry.list",
        buildJsonObject {
          put("repos", JsonArray(entries.map { e ->
            buildJsonObject {
              put("fingerprint", JsonPrimitive(e.fingerprint))
              put("display_name", JsonPrimitive(e.displayName))
              put("local_path", JsonPrimitive(e.localPath.toString()))
              put("last_seen", JsonPrimitive(e.lastSeen))
              put("isolate_from", JsonArray(e.isolateFrom.map { JsonPrimitive(it) }))
            }
          }))
        },
      ),
      ctx,
    )
  }
}

private class RegIsolate(val ctxOf: () -> CliContext) : CliktCommand(name = "isolate") {
  val viewer by option("--from").required()
  val hidden by option("--hide").required()
  override fun run() {
    val ctx = ctxOf()
    val registry = RepoRegistry.openAt(RepoRegistry.defaultStorePath(ctx.envAsMap()))
    try {
      registry.isolate(viewer, hidden)
    } catch (e: IllegalArgumentException) {
      throw CliError(ExitCode.USAGE, e.message ?: "isolate failed")
    }
    emitHuman("✓ $viewer now hides $hidden from its view", ctx)
    emitJson(
      JsonEnvelope.success("repo-registry.isolate", buildJsonObject {
        put("from", JsonPrimitive(viewer))
        put("hide", JsonPrimitive(hidden))
      }),
      ctx,
    )
  }
}

private class RegUnisolate(val ctxOf: () -> CliContext) : CliktCommand(name = "unisolate") {
  val viewer by option("--from").required()
  val hidden by option("--hide").required()
  override fun run() {
    val ctx = ctxOf()
    val registry = RepoRegistry.openAt(RepoRegistry.defaultStorePath(ctx.envAsMap()))
    registry.unisolate(viewer, hidden)
    emitHuman("✓ removed isolation: $viewer no longer hides $hidden", ctx)
    emitJson(
      JsonEnvelope.success("repo-registry.unisolate", buildJsonObject {
        put("from", JsonPrimitive(viewer))
        put("hide", JsonPrimitive(hidden))
      }),
      ctx,
    )
  }
}

/** Extension so the feedback commands can reach the env via CliContext without exposing it broadly. */
internal fun CliContext.envAsMap(): Map<String, String> = System.getenv()
