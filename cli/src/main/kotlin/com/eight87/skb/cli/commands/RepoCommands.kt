package com.eight87.skb.cli.commands

import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoDiscovery
import com.eight87.skb.cli.core.RepoLayout
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlTable
import com.eight87.skb.cli.core.Uuid7
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RepoGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "repo") {
  init { subcommands(RepoInit(ctxOf), RepoList(ctxOf)) }
  override fun run() = Unit
}

private class RepoInit(val ctxOf: () -> CliContext) : CliktCommand(name = "init") {
  val pathArg by argument("path")
  val name by option("--name")
  val defaultCalendar by option("--default-calendar")
  val defaultList by option("--default-list")
  val defaultTz by option("--default-tz")
  val authorName by option("--author-name")
  val authorEmail by option("--author-email")

  override fun run() {
    val ctx = ctxOf()
    val target: Path = Paths.get(pathArg).let { if (it.isAbsolute) it else Paths.get("").toAbsolutePath().resolve(it) }.normalize()
    Files.createDirectories(target)
    val marker = target.resolve(RepoDiscovery.MARKER_DIR)
    if (Files.exists(marker)) throw CliError(
      ExitCode.CONFLICT,
      "directory is already a strictlykeptboy repo: $target",
      hint = "remove the existing $marker first if you really want to re-init",
    )

    val repoId = Uuid7.generate()
    val identityId = Uuid7.generate()
    val now = nowIso()
    val tz = defaultTz ?: "UTC"
    val repoName = name ?: target.fileName.toString()
    val idDisplayName = authorName ?: "skb-cli"
    val idEmail = authorEmail ?: "$identityId@strictlykeptboy.local"

    if (ctx.dryRun) {
      emitHuman("DRY RUN: would init repo at $target (name=$repoName, id=$repoId)", ctx)
      emitJson(
        JsonEnvelope.success(
          "repo.init",
          buildJsonObject {
            put("path", JsonPrimitive(target.toString()))
            put("repo_id", JsonPrimitive(repoId))
            put("identity_id", JsonPrimitive(identityId))
            put("dry_run", JsonPrimitive(true))
          },
        ),
        ctx,
      )
      return
    }

    Files.createDirectories(target.resolve(".strictlykeptboy"))
    Files.createDirectories(target.resolve("identities"))
    Files.createDirectories(target.resolve("calendars"))
    Files.createDirectories(target.resolve("todolists"))

    // .strictlykeptboy/schema.toml + repo.toml
    AtomicWriter.writeUtf8(
      target.resolve(".strictlykeptboy/schema.toml"),
      TomlTable().apply {
        putInt("schema_version", 1)
        putString("kind", "schema_meta")
      }.emit(),
    )
    AtomicWriter.writeUtf8(
      target.resolve(".strictlykeptboy/repo.toml"),
      TomlTable().apply {
        putInt("schema_version", 1)
        putString("id", repoId)
        putString("kind", "repo_meta")
        putString("name", repoName)
        putString("default_identity", identityId)
        putString("default_tz", tz)
        putDateTime("created_at", now)
      }.emit(),
    )

    // identity file
    AtomicWriter.writeUtf8(
      target.resolve("identities/$identityId.md"),
      Frontmatter.serialize(
        TomlTable().apply {
          putString("id", identityId)
          putString("kind", "identity")
          putString("display_name", idDisplayName)
          putString("email", idEmail)
          putBool("default_author", true)
          putDateTime("created_at", now)
        },
        body = "Initial identity scaffolded by `skb repo init`.\n",
      ),
    )

    // AGENTS.md stub
    AtomicWriter.writeUtf8(
      target.resolve("AGENTS.md"),
      "# Agents in this repo\n\nThis is a strictlykeptboy data repo. Entities are TOML-frontmatter Markdown files; see schema.toml. CLI: `skb`.\n",
    )

    val seededCalIds = mutableMapOf<String, String>()
    val seededListIds = mutableMapOf<String, String>()
    defaultCalendar?.let { calName ->
      val calId = Uuid7.generate()
      AtomicWriter.writeUtf8(
        RepoLayout.calendarToml(target, calId),
        TomlTable().apply {
          putString("id", calId)
          putString("kind", "calendar_meta")
          putString("name", calName)
          putString("tz_id", tz)
          putDateTime("created_at", now)
        }.emit(),
      )
      seededCalIds[calName] = calId
    }
    defaultList?.let { listName ->
      val tlId = Uuid7.generate()
      AtomicWriter.writeUtf8(
        RepoLayout.todolistToml(target, tlId),
        TomlTable().apply {
          putString("id", tlId)
          putString("kind", "todolist_meta")
          putString("name", listName)
          putDateTime("created_at", now)
        }.emit(),
      )
      seededListIds[listName] = tlId
    }

    // git init + first commit
    gitOps().init(target)
    val sha = gitOps().addAndCommit(
      repoRoot = target,
      files = listOf("."),
      message = "initial scaffold for \"$repoName\"",
      authorName = idDisplayName,
      authorEmail = idEmail,
    )

    emitHuman(
      buildString {
        appendLine("✓ initialized repo at $target")
        appendLine("  repo id:       $repoId")
        appendLine("  identity:      $idDisplayName ($identityId)")
        seededCalIds.forEach { (n, i) -> appendLine("  calendar:      $n ($i)") }
        seededListIds.forEach { (n, i) -> appendLine("  todolist:      $n ($i)") }
        appendLine("  default tz:    $tz")
        sha?.let { append("  commit:        $it  \"initial scaffold for \\\"$repoName\\\"\"") }
      }, ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "repo.init",
        buildJsonObject {
          put("path", JsonPrimitive(target.toString()))
          put("repo_id", JsonPrimitive(repoId))
          put("repo_name", JsonPrimitive(repoName))
          put("identity_id", JsonPrimitive(identityId))
          put("default_tz", JsonPrimitive(tz))
          put(
            "seeded",
            buildJsonObject {
              put(
                "calendars",
                JsonArray(seededCalIds.map { (n, i) -> buildJsonObject { put("name", JsonPrimitive(n)); put("id", JsonPrimitive(i)) } }),
              )
              put(
                "todolists",
                JsonArray(seededListIds.map { (n, i) -> buildJsonObject { put("name", JsonPrimitive(n)); put("id", JsonPrimitive(i)) } }),
              )
            },
          )
          sha?.let { put("commit", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
  }
}

private class RepoList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  override fun run() {
    val ctx = ctxOf()
    // Phase X scope: list the discovered active repo only.
    // CLI-E.3's ~/.skb/config.toml multi-repo config lands later.
    val root = ctx.repoRoot()
    val meta = root.resolve(".strictlykeptboy/repo.toml")
    val table = if (Files.isRegularFile(meta)) com.eight87.skb.cli.core.MiniToml.parse(Files.readString(meta)) else TomlTable()
    val store = RepoStore(root)
    val cals = store.listCalendars().size
    val tls = store.listTodolists().size
    emitHuman(
      buildString {
        appendLine("1 repo (active)")
        appendLine("  ${table.getString("name") ?: root.fileName}  $root")
        appendLine("    id:        ${table.getString("id") ?: "<unknown>"}")
        appendLine("    calendars: $cals")
        appendLine("    todolists: $tls")
      }.trimEnd(),
      ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "repo.list",
        buildJsonObject {
          put(
            "repos",
            JsonArray(
              listOf(
                buildJsonObject {
                  put("name", JsonPrimitive(table.getString("name") ?: root.fileName.toString()))
                  put("path", JsonPrimitive(root.toString()))
                  put("id", JsonPrimitive(table.getString("id") ?: ""))
                  put("calendars", JsonPrimitive(cals))
                  put("todolists", JsonPrimitive(tls))
                  put("active", JsonPrimitive(true))
                },
              ),
            ),
          )
        },
      ),
      ctx,
    )
  }
}
