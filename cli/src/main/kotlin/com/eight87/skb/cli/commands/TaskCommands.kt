package com.eight87.skb.cli.commands

import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoLayout
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlTable
import com.eight87.skb.cli.core.Uuid7
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime

class TaskGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "task") {
  init { subcommands(TaskAdd(ctxOf), TaskList(ctxOf), TaskShow(ctxOf)) }
  override fun run() = Unit
}

private class TaskAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
  val title by option("--title").required()
  val list by option("--list")
  val due by option("--due")
  val priority by option("--priority")
  val tags by option("--tag").multiple()
  val body by option("--body")
  val explicitId by option("--id")

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val store = RepoStore(root)
    val tl = resolveTodolist(store, list)
    val id = validateId(explicitId) ?: Uuid7.generate()
    val standing = due == null
    val target = if (standing) {
      RepoLayout.standingTask(root, tl.id, id)
    } else {
      // Normalize due to ISO offset datetime so bucket works.
      val isoDue = normalizeDue(due!!)
      RepoLayout.datedTask(root, tl.id, id, isoDue)
    }
    val table = TomlTable().apply {
      putString("id", id)
      putString("kind", if (standing) "standing_task" else "task")
      putString("title", title)
      due?.let { putDateTime("due", normalizeDue(it)) }
      putString("todolist_id", tl.id)
      priority?.toIntOrNull()?.let { putInt("priority", it) }
      if (tags.isNotEmpty()) putStringArray("tags", tags)
      putDateTime("created_at", nowIso())
      putDateTime("updated_at", nowIso())
      putBool("completed", false)
    }
    val msg = "add task \"$title\" in ${tl.name}"
    if (Files.exists(target) && explicitId != null) {
      throw CliError(ExitCode.CONFLICT, "task $id already exists")
    }
    val res = atomicWriteAndCommit(ctx, root, target, table, body ?: "", msg)
    val rel = root.relativize(target).toString()
    emitHuman(
      buildString {
        appendLine(if (res is CommitResult.DryRun) "DRY RUN: would add task \"$title\"" else "✓ added task \"$title\"")
        appendLine("  id:       $id")
        due?.let { appendLine("  due:      ${normalizeDue(it)}") }
        appendLine("  list:     ${tl.name}")
        appendLine("  file:     $rel")
        when (res) {
          is CommitResult.Committed -> res.sha?.let { append("  commit:   $it  \"$msg\"") }
          is CommitResult.DryRun -> append("  commit:   (dry-run) \"$msg\"")
        }
      }, ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "task.add",
        buildJsonObject {
          put("id", JsonPrimitive(id))
          put("kind", JsonPrimitive(if (standing) "standing_task" else "task"))
          put("title", JsonPrimitive(title))
          put("todolist_id", JsonPrimitive(tl.id))
          put("todolist_name", JsonPrimitive(tl.name))
          put("path", JsonPrimitive(rel))
          put("dry_run", JsonPrimitive(res is CommitResult.DryRun))
          if (res is CommitResult.Committed) res.sha?.let { put("commit", JsonPrimitive(it)) }
          if (res is CommitResult.DryRun) put("preview", JsonPrimitive(res.preview))
        },
      ),
      ctx,
    )
  }
}

private class TaskList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  val list by option("--list")
  val standingOnly by option("--standing-only").flag()

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val store = RepoStore(root)
    val lists = if (list != null) listOf(store.resolveTodolist(list!!)) else store.listTodolists()
    data class Row(val id: String, val title: String, val due: String?, val tlId: String, val tlName: String, val path: String, val standing: Boolean, val completed: Boolean)
    val rows = mutableListOf<Row>()
    for (tl in lists) {
      val baseDir = root.resolve("todolists/${tl.id}")
      if (!Files.isDirectory(baseDir)) continue
      Files.walk(baseDir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
          val (t, _) = Frontmatter.parse(Files.readString(f))
          val kind = t.getString("kind") ?: return@forEach
          if (kind != "task" && kind != "standing_task") return@forEach
          val isStanding = kind == "standing_task"
          if (standingOnly && !isStanding) return@forEach
          rows.add(
            Row(
              id = t.getString("id") ?: f.fileName.toString().removeSuffix(".md"),
              title = t.getString("title") ?: "",
              due = t.getDateTime("due"),
              tlId = tl.id,
              tlName = tl.name,
              path = root.relativize(f).toString(),
              standing = isStanding,
              completed = t.getBool("completed") ?: false,
            ),
          )
        }
      }
    }
    val sorted = rows.sortedWith(compareBy({ it.due ?: "9999" }, { it.title }))
    emitHuman(
      buildString {
        appendLine("${sorted.size} task(s)")
        for (r in sorted) {
          val check = if (r.completed) "[x]" else "[ ]"
          val due = r.due ?: "standing"
          appendLine("  $check $due  ${r.title.padEnd(40)}  [${r.tlName}]  ${r.id}")
        }
      }.trimEnd(),
      ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "task.list",
        buildJsonObject {
          put("count", JsonPrimitive(sorted.size))
          put(
            "tasks",
            JsonArray(
              sorted.map {
                buildJsonObject {
                  put("id", JsonPrimitive(it.id))
                  put("title", JsonPrimitive(it.title))
                  put("due", it.due?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                  put("todolist_id", JsonPrimitive(it.tlId))
                  put("todolist_name", JsonPrimitive(it.tlName))
                  put("standing", JsonPrimitive(it.standing))
                  put("completed", JsonPrimitive(it.completed))
                  put("path", JsonPrimitive(it.path))
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

private class TaskShow(val ctxOf: () -> CliContext) : CliktCommand(name = "show") {
  val idArg by argument("task-id")

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val store = RepoStore(root)
    val matches = mutableListOf<Pair<java.nio.file.Path, TomlTable>>()
    for (tl in store.listTodolists()) {
      val dir = root.resolve("todolists/${tl.id}")
      if (!Files.isDirectory(dir)) continue
      Files.walk(dir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
          val name = f.fileName.toString().removeSuffix(".md")
          if (name.startsWith(idArg)) {
            val (t, _) = Frontmatter.parse(Files.readString(f))
            matches.add(f to t)
          }
        }
      }
    }
    when {
      matches.isEmpty() -> throw CliError(ExitCode.NOT_FOUND, "no task matches \"$idArg\"")
      matches.size > 1 -> throw CliError(ExitCode.CONFLICT, "id prefix \"$idArg\" matches ${matches.size} tasks")
    }
    val (file, table) = matches.single()
    val rel = root.relativize(file).toString()
    emitHuman(
      buildString {
        appendLine("task ${table.getString("id")}")
        appendLine("  title:    ${table.getString("title")}")
        table.getDateTime("due")?.let { appendLine("  due:      $it") }
        appendLine("  completed: ${table.getBool("completed") ?: false}")
        appendLine("  file:     $rel")
      }.trimEnd(),
      ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "task.show",
        buildJsonObject {
          put("id", JsonPrimitive(table.getString("id")))
          put("title", JsonPrimitive(table.getString("title")))
          put("due", table.getDateTime("due")?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
          put("completed", JsonPrimitive(table.getBool("completed") ?: false))
          put("path", JsonPrimitive(rel))
        },
      ),
      ctx,
    )
  }
}

private fun resolveTodolist(store: RepoStore, query: String?): RepoStore.TodolistRef {
  if (query != null) return store.resolveTodolist(query)
  val all = store.listTodolists()
  return when (all.size) {
    0 -> throw CliError(ExitCode.NOT_FOUND, "no todolists in this repo", hint = "create one: skb list add --name <name> (CLI-A.5; deferred)")
    1 -> all.single()
    else -> throw CliError(ExitCode.USAGE, "repo has ${all.size} todolists; pass --list to disambiguate")
  }
}

private fun normalizeDue(raw: String): String {
  // Try ISO offset datetime first, then plain date.
  try { return OffsetDateTime.parse(raw).toString() } catch (_: Exception) {}
  try {
    val d = LocalDate.parse(raw)
    // 17:00 local UTC for due-at-end-of-day-ish; sortable bucket year/month derived from this.
    return d.atTime(17, 0).atOffset(java.time.ZoneOffset.UTC).toString()
  } catch (_: Exception) {}
  throw CliError(ExitCode.USAGE, "--due not parseable as date or RFC 3339: $raw")
}
