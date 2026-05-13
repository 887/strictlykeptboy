package com.eight87.skb.cli.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Read-side view of a repo: discovers calendars and todolists by
 * scanning the bucket-directories (which ARE the index per D.3 — never
 * write a manifest).
 *
 * SOLID.I: each named getter returns the narrow view a single command
 * needs; nobody asks for "the whole repo" except `repo list`.
 */
class RepoStore(val root: Path) {

  data class CalendarRef(val id: String, val name: String, val path: Path)
  data class TodolistRef(val id: String, val name: String, val path: Path)

  fun listCalendars(): List<CalendarRef> = listEntities("calendars", "calendar.toml") { id, table, file ->
    CalendarRef(id = id, name = table.getString("name") ?: id, path = file)
  }

  fun listTodolists(): List<TodolistRef> = listEntities("todolists", "todolist.toml") { id, table, file ->
    TodolistRef(id = id, name = table.getString("name") ?: id, path = file)
  }

  fun resolveCalendar(nameOrId: String): CalendarRef = resolveOne(
    nameOrId, listCalendars(), { it.id }, { it.name }, "calendar",
  )

  fun resolveTodolist(nameOrId: String): TodolistRef = resolveOne(
    nameOrId, listTodolists(), { it.id }, { it.name }, "todolist",
  )

  private fun <T> resolveOne(
    query: String,
    pool: List<T>,
    id: (T) -> String,
    name: (T) -> String,
    kind: String,
  ): T {
    val byId = pool.firstOrNull { id(it) == query }
    if (byId != null) return byId
    val byName = pool.filter { name(it).equals(query, ignoreCase = true) }
    if (byName.size == 1) return byName.single()
    if (byName.size > 1) throw CliError(
      ExitCode.CONFLICT,
      "$kind name \"$query\" is ambiguous (matches ${byName.size} entries)",
      hint = "pass the UUIDv7 id instead (skb $kind list)",
      details = mapOf("kind" to kind, "query" to query),
    )
    val suggestions = pool
      .map(name)
      .filter { editDistance(it.lowercase(), query.lowercase()) <= 3 }
      .take(3)
    throw CliError(
      ExitCode.NOT_FOUND,
      "$kind \"$query\" not found",
      hint = if (suggestions.isNotEmpty()) "did you mean: ${suggestions.joinToString(", ") { "\"$it\"" }}? (try: skb $kind list)"
      else "list with: skb $kind list",
      details = mapOf("kind" to kind, "query" to query, "suggestions" to suggestions),
    )
  }

  private fun <T> listEntities(
    subdir: String,
    metaFileName: String,
    factory: (id: String, table: TomlTable, file: Path) -> T,
  ): List<T> {
    val dir = root.resolve(subdir)
    if (!Files.isDirectory(dir)) return emptyList()
    val out = mutableListOf<T>()
    Files.newDirectoryStream(dir).use { stream ->
      for (sub in stream) {
        if (!Files.isDirectory(sub)) continue
        val meta = sub.resolve(metaFileName)
        if (!Files.isRegularFile(meta)) continue
        val text = Files.readString(meta)
        val (table, _) = if (text.trimStart().startsWith("+++")) Frontmatter.parse(text) else MiniToml.parse(text) to ""
        out.add(factory(sub.fileName.toString(), table, meta))
      }
    }
    return out.sortedBy { it.toString() }
  }

  private fun editDistance(a: String, b: String): Int {
    val m = a.length; val n = b.length
    if (m == 0) return n
    if (n == 0) return m
    val dp = IntArray(n + 1) { it }
    for (i in 1..m) {
      var prev = dp[0]
      dp[0] = i
      for (j in 1..n) {
        val tmp = dp[j]
        dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
        prev = tmp
      }
    }
    return dp[n]
  }
}
