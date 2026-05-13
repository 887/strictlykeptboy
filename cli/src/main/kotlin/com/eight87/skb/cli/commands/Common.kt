package com.eight87.skb.cli.commands

import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.GitOps
import com.eight87.skb.cli.core.IdentityResolver
import com.eight87.skb.cli.core.SystemGitOps
import com.eight87.skb.cli.core.TomlTable
import com.github.ajalt.clikt.core.CliktCommand
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Resolver for the global GitOps. Tests can replace via [overrideGitOps].
 * SOLID.D: callers depend on the interface, the static here is only
 * the default wiring (production composition root for the CLI).
 */
@Volatile
private var gitOpsOverride: GitOps? = null

fun gitOps(): GitOps = gitOpsOverride ?: SystemGitOps

internal fun overrideGitOps(ops: GitOps?) { gitOpsOverride = ops }

fun nowIso(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC))

sealed class CommitResult {
  data class Committed(val file: Path, val sha: String?) : CommitResult()
  data class DryRun(val file: Path, val preview: String, val commitMessage: String) : CommitResult()
}

/**
 * Final step of every write command. Performs atomic write + auto-commit
 * unless --dry-run, in which case prints a preview and returns null
 * for the commit SHA. This is the X.4 + X.6 cross-cutting hook.
 */
fun atomicWriteAndCommit(
  ctx: CliContext,
  root: Path,
  target: Path,
  table: TomlTable,
  body: String,
  commitMessage: String,
): CommitResult {
  val content = Frontmatter.serialize(table, body)
  if (ctx.dryRun) {
    return CommitResult.DryRun(target, content, commitMessage)
  }
  AtomicWriter.writeUtf8(target, content)
  val identity = IdentityResolver.resolve(root)
  val sha = gitOps().addAndCommit(
    repoRoot = root,
    files = listOf(root.relativize(target).toString()),
    message = commitMessage,
    authorName = identity.displayName,
    authorEmail = identity.email,
  )
  return CommitResult.Committed(target, sha)
}

/** CLI-C.7 — print human stdout iff not --quiet and not --json. */
fun CliktCommand.emitHuman(line: String, ctx: CliContext) {
  if (!ctx.json && !ctx.quiet) echo(line)
}

fun CliktCommand.emitJson(envelope: String, ctx: CliContext) {
  if (ctx.json) echo(envelope)
}

/** Validates an explicit --id flag is a 36-char lowercase UUIDv7-shaped string. */
fun validateId(raw: String?): String? {
  if (raw == null) return null
  if (!raw.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))) {
    throw CliError(ExitCode.USAGE, "--id must be a lowercase UUIDv7 (got: $raw)")
  }
  return raw
}
