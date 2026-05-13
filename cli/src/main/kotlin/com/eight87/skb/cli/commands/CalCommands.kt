package com.eight87.skb.cli.commands

import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.IdentityResolver
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoLayout
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlTable
import com.eight87.skb.cli.core.Uuid7
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files

class CalGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "cal") {
  init { subcommands(CalAdd(ctxOf), CalList(ctxOf)) }
  override fun run() = Unit
}

private class CalAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
  val name by option("--name").required()
  val tz by option("--tz")
  val color by option("--color")
  val explicitId by option("--id")

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val id = validateId(explicitId) ?: Uuid7.generate()
    val target = RepoLayout.calendarToml(root, id)
    if (Files.exists(target)) throw CliError(ExitCode.CONFLICT, "calendar $id already exists")
    val table = TomlTable().apply {
      putString("id", id)
      putString("kind", "calendar_meta")
      putString("name", name)
      putString("tz_id", tz ?: "UTC")
      color?.let { putString("color", it) }
      putDateTime("created_at", nowIso())
    }
    val content = com.eight87.skb.cli.core.Frontmatter.serialize(table, "")
    val msg = "add calendar \"$name\""
    if (ctx.dryRun) {
      emitHuman("DRY RUN: would add calendar \"$name\" id=$id\n--- preview ---\n$content", ctx)
      emitJson(
        JsonEnvelope.success(
          "cal.add",
          buildJsonObject {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(name))
            put("dry_run", JsonPrimitive(true))
            put("preview", JsonPrimitive(content))
          },
        ),
        ctx,
      )
      return
    }
    AtomicWriter.writeUtf8(target, content)
    val identity = IdentityResolver.resolve(root)
    val sha = gitOps().addAndCommit(
      repoRoot = root,
      files = listOf(root.relativize(target).toString()),
      message = msg,
      authorName = identity.displayName,
      authorEmail = identity.email,
    )
    val rel = root.relativize(target).toString()
    emitHuman(
      buildString {
        appendLine("✓ added calendar \"$name\"")
        appendLine("  id:    $id")
        appendLine("  tz:    ${tz ?: "UTC"}")
        appendLine("  file:  $rel")
        sha?.let { append("  commit: $it  \"$msg\"") }
      }, ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "cal.add",
        buildJsonObject {
          put("id", JsonPrimitive(id))
          put("name", JsonPrimitive(name))
          put("tz_id", JsonPrimitive(tz ?: "UTC"))
          put("path", JsonPrimitive(rel))
          sha?.let { put("commit", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
  }
}

private class CalList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val store = RepoStore(root)
    val cals = store.listCalendars()
    emitHuman(
      buildString {
        appendLine("${cals.size} calendar(s)")
        for (c in cals) appendLine("  ${c.id}  ${c.name}")
      }.trimEnd(),
      ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "cal.list",
        buildJsonObject {
          put("count", JsonPrimitive(cals.size))
          put(
            "calendars",
            JsonArray(
              cals.map {
                buildJsonObject {
                  put("id", JsonPrimitive(it.id))
                  put("name", JsonPrimitive(it.name))
                  put("path", JsonPrimitive(root.relativize(it.path).toString()))
                }
              },
            ),
          )
        },
      ),
      ctx,
    )
  }
}
