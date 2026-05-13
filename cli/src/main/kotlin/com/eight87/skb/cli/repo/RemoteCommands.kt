package com.eight87.skb.cli.repo

import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.MiniToml
import com.eight87.skb.cli.core.TomlTable
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase ZZ.H / MO-H — `skb remote add|remove|list|set-primary|set-policy|rename`.
 *
 * Per-repo remote metadata is persisted at
 * `.strictlykeptboy/remotes.toml` (CLI-only; the Android app uses its own
 * per-device `RepoConfig` in EncryptedSharedPreferences). The file is a
 * flat list:
 *
 * ```toml
 * primary = "origin"
 *
 * [[remote]]
 * name = "origin"
 * url = "git@github.com:me/repo.git"
 * push_policy = "push"
 * fetch_enabled = true
 * read_only = false
 * ```
 *
 * The CLI invokes `git remote add|remove|rename` via [com.eight87.skb.cli.commands.gitOps]
 * so the on-disk git config and the metadata file stay in lockstep.
 *
 * Locked decisions (SOLID.S — naming policy in fields, not in `name`):
 * - First remote added auto-becomes primary.
 * - Removing primary promotes the next remote (by add-order) to primary.
 * - `set-policy` accepts `push | push_lazy | never` (case-insensitive).
 * - Reserved names: `HEAD`. (No restriction on `origin`; it is the
 *   conventional primary name and renaming it is allowed.)
 * - Per ZZ.E, default policy for the first remote is `push`, additional
 *   are `push_lazy`. CLI honours that on `remote add` when no `--policy`
 *   is given.
 */
class RemoteGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "remote") {
  init {
    subcommands(
      RemoteAdd(ctxOf),
      RemoteRemove(ctxOf),
      RemoteList(ctxOf),
      RemoteSetPrimary(ctxOf),
      RemoteSetPolicy(ctxOf),
      RemoteRename(ctxOf),
    )
  }
  override fun run() = Unit
}

// ---- data model + persistence -----------------------------------------------

internal data class CliRemote(
  val name: String,
  val url: String,
  val pushPolicy: String, // push | push_lazy | never
  val fetchEnabled: Boolean,
  val readOnly: Boolean,
)

internal data class RemotesFile(
  val primary: String?,
  val remotes: List<CliRemote>,
) {
  fun toToml(): String = buildString {
    if (primary != null) {
      appendLine("primary = \"${primary.escape()}\"")
      appendLine()
    }
    for (r in remotes) {
      appendLine("[[remote]]")
      appendLine("name = \"${r.name.escape()}\"")
      appendLine("url = \"${r.url.escape()}\"")
      appendLine("push_policy = \"${r.pushPolicy}\"")
      appendLine("fetch_enabled = ${r.fetchEnabled}")
      appendLine("read_only = ${r.readOnly}")
      appendLine()
    }
  }

  companion object {
    fun parse(text: String): RemotesFile {
      var primary: String? = null
      val out = mutableListOf<MutableMap<String, String>>()
      var current: MutableMap<String, String>? = null
      for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        if (line == "[[remote]]") {
          current = mutableMapOf<String, String>().also { out.add(it) }
          continue
        }
        val eq = line.indexOf('='); if (eq < 0) continue
        val k = line.substring(0, eq).trim()
        val v = line.substring(eq + 1).trim().trim('"')
        if (current == null) {
          if (k == "primary") primary = v
        } else {
          current!![k] = v
        }
      }
      return RemotesFile(
        primary = primary,
        remotes = out.map {
          CliRemote(
            name = it["name"] ?: "",
            url = it["url"] ?: "",
            pushPolicy = (it["push_policy"] ?: "push").lowercase(),
            fetchEnabled = (it["fetch_enabled"] ?: "true") == "true",
            readOnly = (it["read_only"] ?: "false") == "true",
          )
        }.filter { it.name.isNotEmpty() },
      )
    }
  }
}

private fun String.escape() = replace("\\", "\\\\").replace("\"", "\\\"")

private fun remotesPath(root: Path): Path = root.resolve(".strictlykeptboy/remotes.toml")

internal fun loadRemotes(root: Path): RemotesFile {
  val p = remotesPath(root)
  if (!Files.isRegularFile(p)) return RemotesFile(null, emptyList())
  return RemotesFile.parse(Files.readString(p))
}

internal fun writeRemotes(root: Path, file: RemotesFile) {
  AtomicWriter.writeUtf8(remotesPath(root), file.toToml())
}

private fun normalizePolicy(raw: String): String = when (raw.lowercase().replace('-', '_')) {
  "push" -> "push"
  "push_lazy", "lazy" -> "push_lazy"
  "never", "fetch_only", "fetch-only" -> "never"
  else -> throw CliError(
    ExitCode.USAGE,
    "unknown push policy '$raw'",
    hint = "expected one of: push, push_lazy, never",
  )
}

private fun guardName(name: String) {
  if (name.isBlank()) throw CliError(ExitCode.USAGE, "remote name cannot be blank")
  if (name == "HEAD") throw CliError(ExitCode.USAGE, "'HEAD' is a reserved ref name")
  if (name.any { it.isWhitespace() || it == '/' }) {
    throw CliError(ExitCode.USAGE, "remote name '$name' contains illegal characters")
  }
}

private fun runGit(root: Path, args: List<String>): String {
  val pb = ProcessBuilder(args).directory(root.toFile()).redirectErrorStream(true)
  val proc = pb.start()
  val out = proc.inputStream.bufferedReader().readText()
  val rc = proc.waitFor()
  if (rc != 0) {
    throw CliError(
      ExitCode.INTERNAL,
      "git ${args.drop(1).joinToString(" ")} failed (rc=$rc)",
      hint = out.lines().firstOrNull(),
    )
  }
  return out
}

// ---- commands ---------------------------------------------------------------

private class RemoteAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
  val name by argument("name")
  val url by argument("url")
  val policyOpt by option("--policy", help = "push | push_lazy | never")
  val noFetch by option("--no-fetch", help = "disable fetching from this remote").flag()

  override fun run() {
    val ctx = ctxOf()
    guardName(name)
    val root = ctx.repoRoot()
    val current = loadRemotes(root)
    if (current.remotes.any { it.name == name }) {
      throw CliError(ExitCode.CONFLICT, "remote '$name' already exists")
    }
    val resolvedPolicy = policyOpt?.let { normalizePolicy(it) }
      ?: if (current.remotes.isEmpty()) "push" else "push_lazy"
    val nextRemote = CliRemote(name, url, resolvedPolicy, fetchEnabled = !noFetch, readOnly = false)
    val nextPrimary = current.primary ?: name
    val nextFile = current.copy(primary = nextPrimary, remotes = current.remotes + nextRemote)

    if (ctx.dryRun) {
      emitHuman("DRY RUN: would add remote $name → $url (policy=$resolvedPolicy, primary=$nextPrimary)", ctx)
      emitJson(JsonEnvelope.success("remote.add", payloadFor(nextFile, focus = name)), ctx)
      return
    }
    // git remote add
    runGit(root, listOf("git", "remote", "add", name, url))
    writeRemotes(root, nextFile)
    emitHuman("✓ remote $name → $url (policy=$resolvedPolicy, primary=$nextPrimary)", ctx)
    emitJson(JsonEnvelope.success("remote.add", payloadFor(nextFile, focus = name)), ctx)
  }
}

private class RemoteRemove(val ctxOf: () -> CliContext) : CliktCommand(name = "remove") {
  val name by argument("name")
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val current = loadRemotes(root)
    val existing = current.remotes.firstOrNull { it.name == name }
      ?: throw CliError(ExitCode.NOT_FOUND, "remote '$name' is not configured")
    val remaining = current.remotes.filter { it.name != name }
    val nextPrimary = when {
      remaining.isEmpty() -> null
      current.primary == name -> remaining.first().name
      else -> current.primary
    }
    if (ctx.dryRun) {
      emitHuman("DRY RUN: would remove remote $name", ctx)
      return
    }
    runGit(root, listOf("git", "remote", "remove", name))
    writeRemotes(root, RemotesFile(nextPrimary, remaining))
    emitHuman("✓ removed remote $name (was ${existing.url})", ctx)
    emitJson(JsonEnvelope.success("remote.remove", buildJsonObject {
      put("removed", JsonPrimitive(name))
      put("primary", nextPrimary?.let(::JsonPrimitive) ?: JsonPrimitive(""))
    }), ctx)
  }
}

private class RemoteList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val current = loadRemotes(root)
    if (current.remotes.isEmpty()) {
      emitHuman("(no remotes configured — this is a local-only repo)", ctx)
    } else {
      val lines = current.remotes.joinToString("\n") { r ->
        val primary = if (r.name == current.primary) " *primary*" else ""
        "  ${r.name}  ${r.url}  policy=${r.pushPolicy}  fetch=${r.fetchEnabled}$primary"
      }
      emitHuman("${current.remotes.size} remote(s):\n$lines", ctx)
    }
    emitJson(JsonEnvelope.success("remote.list", payloadFor(current, focus = null)), ctx)
  }
}

private class RemoteSetPrimary(val ctxOf: () -> CliContext) : CliktCommand(name = "set-primary") {
  val name by argument("name")
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val current = loadRemotes(root)
    if (current.remotes.none { it.name == name }) {
      throw CliError(ExitCode.NOT_FOUND, "remote '$name' is not configured")
    }
    if (ctx.dryRun) {
      emitHuman("DRY RUN: would set primary to $name", ctx)
      return
    }
    val next = current.copy(primary = name)
    writeRemotes(root, next)
    emitHuman("✓ primary remote is now $name", ctx)
    emitJson(JsonEnvelope.success("remote.set-primary", payloadFor(next, focus = name)), ctx)
  }
}

private class RemoteSetPolicy(val ctxOf: () -> CliContext) : CliktCommand(name = "set-policy") {
  val name by argument("name")
  val policy by argument("policy")
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val current = loadRemotes(root)
    if (current.remotes.none { it.name == name }) {
      throw CliError(ExitCode.NOT_FOUND, "remote '$name' is not configured")
    }
    val normalized = normalizePolicy(policy)
    if (ctx.dryRun) {
      emitHuman("DRY RUN: would set policy of $name to $normalized", ctx)
      return
    }
    val next = current.copy(
      remotes = current.remotes.map {
        if (it.name == name) it.copy(pushPolicy = normalized) else it
      },
    )
    writeRemotes(root, next)
    emitHuman("✓ policy of $name is now $normalized", ctx)
    emitJson(JsonEnvelope.success("remote.set-policy", payloadFor(next, focus = name)), ctx)
  }
}

private class RemoteRename(val ctxOf: () -> CliContext) : CliktCommand(name = "rename") {
  val from by argument("from")
  val to by argument("to")
  override fun run() {
    val ctx = ctxOf()
    guardName(to)
    val root = ctx.repoRoot()
    val current = loadRemotes(root)
    if (current.remotes.none { it.name == from }) {
      throw CliError(ExitCode.NOT_FOUND, "remote '$from' is not configured")
    }
    if (current.remotes.any { it.name == to }) {
      throw CliError(ExitCode.CONFLICT, "remote '$to' already exists")
    }
    if (ctx.dryRun) {
      emitHuman("DRY RUN: would rename remote $from → $to", ctx)
      return
    }
    runGit(root, listOf("git", "remote", "rename", from, to))
    val next = RemotesFile(
      primary = if (current.primary == from) to else current.primary,
      remotes = current.remotes.map { if (it.name == from) it.copy(name = to) else it },
    )
    writeRemotes(root, next)
    emitHuman("✓ renamed remote $from → $to", ctx)
    emitJson(JsonEnvelope.success("remote.rename", payloadFor(next, focus = to)), ctx)
  }
}

private fun payloadFor(file: RemotesFile, focus: String?) = buildJsonObject {
  put("primary", file.primary?.let(::JsonPrimitive) ?: JsonPrimitive(""))
  if (focus != null) put("focus", JsonPrimitive(focus))
  put(
    "remotes",
    JsonArray(
      file.remotes.map { r ->
        buildJsonObject {
          put("name", JsonPrimitive(r.name))
          put("url", JsonPrimitive(r.url))
          put("push_policy", JsonPrimitive(r.pushPolicy))
          put("fetch_enabled", JsonPrimitive(r.fetchEnabled))
          put("read_only", JsonPrimitive(r.readOnly))
          put("is_primary", JsonPrimitive(r.name == file.primary))
        }
      },
    ),
  )
}

// Force-import to keep MiniToml symbol referenced for future array-parsing reuse.
@Suppress("unused")
private val ___keep_minitoml_reference = MiniToml::class
@Suppress("unused")
private val ___keep_tomltable_reference = TomlTable::class
