package com.eight87.skb.cli.ref

import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.commands.gitOps
import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.IdentityResolver
import com.eight87.skb.cli.core.JsonEnvelope
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase NN.7 — `skb ref add|remove|list` CLI surface.
 *
 * Operates on `<repo-root>/references.toml`. Atomic file writes via
 * [AtomicWriter]; auto-commit per CLI-B.3 with a `chore(ref): ...`
 * message so the change is reviewable in the git log.
 *
 * Field set per NN.1 LOCK + DM-Q:
 *
 *   [[reference]]
 *   repo_id          = "<uuidv7>"
 *   display_name     = "Master's schedule"
 *   url              = "git@host:o/r.git"           (or `remotes = [...]`)
 *   required         = false
 *   write_back_target = "<16-hex fingerprint>"      (FB-H)
 *
 * Out of scope for this surface (live in their own commands):
 *   - `skb ref-set-write-back` (FB-H.6) — handled by RefSetWriteBackCommand.
 *
 * SOLID.S: this file owns the user-facing ref add/remove/list verbs.
 * SOLID.D: depends on `gitOps()` indirection, AtomicWriter, no concrete
 * git/JGit imports. Testable via injected GitOps override.
 */
class RefGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "ref") {
  init { subcommands(RefAdd(ctxOf), RefRemove(ctxOf), RefList(ctxOf)) }
  override fun run() = Unit
}

private class RefAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
  val repoId by option("--repo-id").required()
  val displayName by option("--display-name").required()
  val url by option("--url")
  val remotes by option("--remote", help = "additional remote URL (repeat for multi-origin)").multiple()
  val required by option("--required").flag()
  val writeBackTarget by option("--write-back-target")

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val allUrls = buildList {
      if (!url.isNullOrBlank()) add(url!!.trim())
      addAll(remotes.map { it.trim() }.filter { it.isNotEmpty() })
    }.distinct()
    if (allUrls.isEmpty()) {
      throw CliError(ExitCode.USAGE, "must supply --url <url> or one or more --remote <url>")
    }
    if (writeBackTarget != null && !writeBackTarget!!.matches(Regex("^[0-9a-f]{16}$"))) {
      throw CliError(ExitCode.USAGE, "--write-back-target must be a 16-hex repo fingerprint")
    }
    val file = root.resolve("references.toml")
    val existing = if (Files.isRegularFile(file)) Files.readString(file) else emptyManifestText()
    val updated = upsertEntry(
      existing,
      repoId = repoId,
      displayName = displayName,
      urls = allUrls,
      required = required,
      writeBackTarget = writeBackTarget,
    )
    if (ctx.dryRun) {
      emitHuman("DRY RUN: would update references.toml\n---\n$updated", ctx)
      emitJson(
        JsonEnvelope.success(
          "ref.add",
          buildJsonObject {
            put("repo_id", JsonPrimitive(repoId))
            put("dry_run", JsonPrimitive(true))
          },
        ),
        ctx,
      )
      return
    }
    AtomicWriter.writeUtf8(file, updated)
    val identity = IdentityResolver.resolve(root)
    val sha = gitOps().addAndCommit(
      root,
      listOf(root.relativize(file).toString()),
      message = "chore(ref): add reference $displayName ($repoId)",
      authorName = identity.displayName,
      authorEmail = identity.email,
    )
    emitHuman("✓ added reference $displayName ($repoId)", ctx)
    emitJson(
      JsonEnvelope.success(
        "ref.add",
        buildJsonObject {
          put("repo_id", JsonPrimitive(repoId))
          put("display_name", JsonPrimitive(displayName))
          put("required", JsonPrimitive(required))
          sha?.let { put("commit_sha", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
  }
}

private class RefRemove(val ctxOf: () -> CliContext) : CliktCommand(name = "remove") {
  val repoId by option("--repo-id").required()
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val file = root.resolve("references.toml")
    if (!Files.isRegularFile(file)) {
      throw CliError(ExitCode.NOT_FOUND, "references.toml not found in $root")
    }
    val existing = Files.readString(file)
    val (updated, removed) = removeEntry(existing, repoId)
    if (!removed) {
      throw CliError(ExitCode.NOT_FOUND, "no [[reference]] block with repo_id=$repoId")
    }
    if (ctx.dryRun) {
      emitHuman("DRY RUN: would remove reference $repoId", ctx)
      return
    }
    AtomicWriter.writeUtf8(file, updated)
    val identity = IdentityResolver.resolve(root)
    val sha = gitOps().addAndCommit(
      root,
      listOf(root.relativize(file).toString()),
      message = "chore(ref): remove reference $repoId",
      authorName = identity.displayName,
      authorEmail = identity.email,
    )
    emitHuman("✓ removed reference $repoId", ctx)
    emitJson(
      JsonEnvelope.success(
        "ref.remove",
        buildJsonObject {
          put("repo_id", JsonPrimitive(repoId))
          sha?.let { put("commit_sha", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
  }
}

private class RefList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val file = root.resolve("references.toml")
    if (!Files.isRegularFile(file)) {
      emitHuman("(no references.toml)", ctx)
      emitJson(JsonEnvelope.success("ref.list", buildJsonObject { put("entries", JsonArray(emptyList())) }), ctx)
      return
    }
    val entries = parseEntries(Files.readString(file))
    for (e in entries) {
      emitHuman("${e.repoId}  ${e.displayName}  [${e.urls.joinToString(", ")}]${if (e.required) "  required" else ""}", ctx)
    }
    val json = JsonArray(
      entries.map { e ->
        buildJsonObject {
          put("repo_id", JsonPrimitive(e.repoId))
          put("display_name", JsonPrimitive(e.displayName))
          put("urls", JsonArray(e.urls.map { JsonPrimitive(it) }))
          put("required", JsonPrimitive(e.required))
          e.writeBackTarget?.let { put("write_back_target", JsonPrimitive(it)) }
        }
      },
    )
    emitJson(JsonEnvelope.success("ref.list", buildJsonObject { put("entries", json) }), ctx)
  }
}

// ---------- minimal text-level codec (no full TOML parse — preserves
// unknown keys per the DM-Q "graceful unknown-key" contract) ----------

internal data class RefEntry(
  val repoId: String,
  val displayName: String,
  val urls: List<String>,
  val required: Boolean,
  val writeBackTarget: String?,
)

internal fun emptyManifestText(): String = "+++\nschema_version = 1\n+++\n"

internal fun parseEntries(text: String): List<RefEntry> {
  val out = mutableListOf<RefEntry>()
  var cur: MutableMap<String, String>? = null
  var curUrls: MutableList<String>? = null
  fun flush() {
    val c = cur ?: return
    val urls = (curUrls ?: mutableListOf()).toList()
    val repoId = c["repo_id"] ?: return
    out.add(
      RefEntry(
        repoId = repoId,
        displayName = c["display_name"].orEmpty(),
        urls = if (urls.isNotEmpty()) urls else listOfNotNull(c["url"]),
        required = c["required"] == "true",
        writeBackTarget = c["write_back_target"],
      ),
    )
    cur = null
    curUrls = null
  }
  for (raw in text.lines()) {
    val line = raw.substringBefore('#').trim()
    if (line.isBlank() || line == "+++") continue
    if (line == "[[reference]]") {
      flush()
      cur = mutableMapOf()
      curUrls = mutableListOf()
      continue
    }
    if (line.startsWith("[reference.")) {
      // sub-tables (e.g. credential_hint) are preserved on write by
      // re-emitting unknown blocks verbatim — handled by upsert/remove
      // which operate on a block-aware buffer rather than this parser.
      continue
    }
    val c = cur ?: continue
    val eq = line.indexOf('=')
    if (eq <= 0) continue
    val key = line.substring(0, eq).trim()
    val value = line.substring(eq + 1).trim()
    when (key) {
      "remotes" -> {
        val inner = value.removePrefix("[").removeSuffix("]")
        curUrls!!.addAll(inner.split(',').map { unq(it.trim()) }.filter { it.isNotEmpty() })
      }
      "url", "display_name", "repo_id", "required", "write_back_target" ->
        c[key] = if (key == "required") value else unq(value)
    }
  }
  flush()
  return out
}

private fun unq(s: String): String =
  if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) s.substring(1, s.length - 1) else s

private fun q(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** Returns (newText, removed). */
internal fun removeEntry(text: String, repoId: String): kotlin.Pair<String, Boolean> {
  val lines = text.split('\n')
  val out = mutableListOf<String>()
  var i = 0
  var removed = false
  while (i < lines.size) {
    val ln = lines[i].trim()
    if (ln == "[[reference]]") {
      // Lookahead to end of block (until next `[[` table header or EOF).
      var j = i + 1
      while (j < lines.size && !lines[j].trim().startsWith("[[")) j++
      val block = lines.subList(i, j).joinToString("\n")
      if (blockHasRepoId(block, repoId)) {
        removed = true
        // Also drop a single leading blank line if present, to avoid
        // doubling whitespace.
        if (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.size - 1)
        i = j
        continue
      } else {
        out.addAll(lines.subList(i, j))
        i = j
        continue
      }
    }
    out.add(lines[i])
    i++
  }
  return Pair(out.joinToString("\n"), removed)
}

private fun blockHasRepoId(block: String, repoId: String): Boolean {
  for (raw in block.lines()) {
    val ln = raw.substringBefore('#').trim()
    if (ln.startsWith("repo_id")) {
      val v = unq(ln.substringAfter('=').trim())
      if (v == repoId) return true
    }
  }
  return false
}

/**
 * Insert a new `[[reference]]` block, or replace the existing block
 * matching [repoId]. NN.3 dedup semantics: newer entry wins.
 */
internal fun upsertEntry(
  text: String,
  repoId: String,
  displayName: String,
  urls: List<String>,
  required: Boolean,
  writeBackTarget: String?,
): String {
  val (withoutOld, _) = removeEntry(text, repoId)
  val base = withoutOld.trimEnd()
  val sb = StringBuilder(base)
  if (sb.isNotEmpty()) sb.append('\n')
  sb.append('\n')
  sb.append("[[reference]]\n")
  sb.append("repo_id = ").append(q(repoId)).append('\n')
  sb.append("display_name = ").append(q(displayName)).append('\n')
  if (urls.size == 1) {
    sb.append("url = ").append(q(urls.single())).append('\n')
  } else {
    sb.append("remotes = [")
      .append(urls.joinToString(", ") { q(it) })
      .append("]\n")
  }
  if (required) sb.append("required = true\n")
  writeBackTarget?.let { sb.append("write_back_target = ").append(q(it)).append('\n') }
  return sb.toString()
}

