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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.core.subcommands
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class EventGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "event") {
  init {
    subcommands(EventAdd(ctxOf), EventList(ctxOf), EventShow(ctxOf))
  }
  override fun run() = Unit
}

private class EventAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
  val title by option("--title").required()
  val start by option("--start").required()
  val end by option("--end")
  val duration by option("--duration")
  val calendar by option("--calendar")
  val location by option("--location")
  val tags by option("--tag").multiple()
  val body by option("--body")
  val explicitId by option("--id")

  override fun run() {
    val ctx = ctxOf()
    val startIso = parseStart(start)
    val endIso = computeEnd(startIso, end, duration)
    val root = ctx.repoRoot()
    val store = RepoStore(root)
    val cal = resolveCalendar(store, calendar)
    val id = validateId(explicitId) ?: Uuid7.generate()
    val target = RepoLayout.event(root, cal.id, id, startIso)
    val table = TomlTable().apply {
      putString("id", id)
      putString("kind", "event")
      putString("title", title)
      putDateTime("start", startIso)
      putDateTime("end", endIso)
      putString("calendar_id", cal.id)
      location?.let { putString("location", it) }
      if (tags.isNotEmpty()) putStringArray("tags", tags)
      putDateTime("created_at", nowIso())
      putDateTime("updated_at", nowIso())
    }
    val msg = "add event \"$title\" in ${cal.name}"
    if (Files.exists(target) && explicitId != null) {
      throw CliError(
        ExitCode.CONFLICT,
        "event $id already exists",
        hint = "edit it via `skb event edit` (Round 2 follow-up) or drop --id",
      )
    }
    val res = atomicWriteAndCommit(ctx, root, target, table, body ?: "", msg)
    val rel = root.relativize(target).toString()
    emitHuman(
      buildString {
        appendLine(if (res is CommitResult.DryRun) "DRY RUN: would add event \"$title\"" else "✓ added event \"$title\"")
        appendLine("  id:       $id")
        appendLine("  when:     $startIso → $endIso")
        appendLine("  calendar: ${cal.name}")
        appendLine("  file:     $rel")
        when (res) {
          is CommitResult.Committed -> res.sha?.let { append("  commit:   $it  \"$msg\"") }
          is CommitResult.DryRun -> append("  commit:   (dry-run) \"$msg\"")
        }
      }, ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "event.add",
        buildJsonObject {
          put("id", JsonPrimitive(id))
          put("kind", JsonPrimitive("event"))
          put("title", JsonPrimitive(title))
          put("start", JsonPrimitive(startIso))
          put("end", JsonPrimitive(endIso))
          put("calendar_id", JsonPrimitive(cal.id))
          put("calendar_name", JsonPrimitive(cal.name))
          put("path", JsonPrimitive(rel))
          put("dry_run", JsonPrimitive(res is CommitResult.DryRun))
          if (res is CommitResult.Committed) res.sha?.let { put("commit", JsonPrimitive(it)) }
          if (res is CommitResult.DryRun) put("preview", JsonPrimitive(res.preview))
          put("created", JsonPrimitive(true))
        },
      ),
      ctx,
    )
  }
}

private class EventList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
val calendar by option("--calendar")
  val from by option("--from")
  val to by option("--to")
  val limit by option("--limit")

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val store = RepoStore(root)
    val calsToScan = if (calendar != null) listOf(store.resolveCalendar(calendar!!)) else store.listCalendars()
    val fromDate = from?.let(LocalDate::parse) ?: LocalDate.now().minusDays(30)
    val toDate = to?.let(LocalDate::parse) ?: LocalDate.now().plusDays(90)
    val cap = limit?.toIntOrNull() ?: 100
    data class EventRow(val id: String, val title: String, val start: String, val end: String, val calId: String, val calName: String, val path: String)
    val rows = mutableListOf<EventRow>()
    for (cal in calsToScan) {
      val evDir = root.resolve("calendars/${cal.id}/events")
      if (!Files.isDirectory(evDir)) continue
      Files.walk(evDir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
          val (t, _) = Frontmatter.parse(Files.readString(f))
          val startStr = t.getDateTime("start") ?: return@forEach
          val startDate = try { OffsetDateTime.parse(startStr).toLocalDate() } catch (_: Exception) { return@forEach }
          if (startDate.isBefore(fromDate) || startDate.isAfter(toDate)) return@forEach
          rows.add(
            EventRow(
              id = t.getString("id") ?: f.fileName.toString().removeSuffix(".md"),
              title = t.getString("title") ?: "",
              start = startStr,
              end = t.getDateTime("end") ?: startStr,
              calId = cal.id,
              calName = cal.name,
              path = root.relativize(f).toString(),
            ),
          )
        }
      }
    }
    val sorted = rows.sortedBy { it.start }.take(cap)
    emitHuman(
      buildString {
        appendLine("${sorted.size} event(s)")
        for (r in sorted) appendLine("  ${r.start}  ${r.title.padEnd(40)}  [${r.calName}]  ${r.id}")
      }.trimEnd(),
      ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "event.list",
        buildJsonObject {
          put("count", JsonPrimitive(sorted.size))
          put(
            "events",
            JsonArray(
              sorted.map {
                buildJsonObject {
                  put("id", JsonPrimitive(it.id))
                  put("title", JsonPrimitive(it.title))
                  put("start", JsonPrimitive(it.start))
                  put("end", JsonPrimitive(it.end))
                  put("calendar_id", JsonPrimitive(it.calId))
                  put("calendar_name", JsonPrimitive(it.calName))
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

private class EventShow(val ctxOf: () -> CliContext) : CliktCommand(name = "show") {
  val idArg by argument("event-id")

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val store = RepoStore(root)
    val matches = mutableListOf<Pair<java.nio.file.Path, TomlTable>>()
    for (cal in store.listCalendars()) {
      val evDir = root.resolve("calendars/${cal.id}/events")
      if (!Files.isDirectory(evDir)) continue
      Files.walk(evDir).use { stream ->
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
      matches.isEmpty() -> throw CliError(ExitCode.NOT_FOUND, "no event matches \"$idArg\"")
      matches.size > 1 -> throw CliError(
        ExitCode.CONFLICT,
        "id prefix \"$idArg\" matches ${matches.size} events",
        hint = "use a longer id prefix",
      )
    }
    val (file, table) = matches.single()
    val rel = root.relativize(file).toString()
    emitHuman(
      buildString {
        appendLine("event ${table.getString("id")}")
        appendLine("  title:    ${table.getString("title")}")
        appendLine("  start:    ${table.getDateTime("start")}")
        appendLine("  end:      ${table.getDateTime("end")}")
        appendLine("  file:     $rel")
      }.trimEnd(),
      ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "event.show",
        buildJsonObject {
          put("id", JsonPrimitive(table.getString("id")))
          put("title", JsonPrimitive(table.getString("title")))
          put("start", JsonPrimitive(table.getDateTime("start")))
          put("end", JsonPrimitive(table.getDateTime("end")))
          put("path", JsonPrimitive(rel))
        },
      ),
      ctx,
    )
  }
}

private fun parseStart(s: String): String {
  try { return OffsetDateTime.parse(s).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) } catch (_: Exception) {}
  // Tolerate `YYYY-MM-DDTHH:MM` local — assume UTC.
  try {
    val ldt = java.time.LocalDateTime.parse(s)
    return ldt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  } catch (_: Exception) {}
  throw CliError(ExitCode.USAGE, "--start not parseable as RFC 3339: $s")
}

private fun computeEnd(start: String, end: String?, duration: String?): String {
  if (end != null && duration != null) throw CliError(ExitCode.USAGE, "--end and --duration are mutually exclusive")
  if (end != null) {
    val s = OffsetDateTime.parse(start); val e = try { OffsetDateTime.parse(end) } catch (_: Exception) { throw CliError(ExitCode.USAGE, "--end not parseable: $end") }
    if (!e.isAfter(s)) throw CliError(ExitCode.USAGE, "--end must be after --start")
    return e.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }
  if (duration != null) {
    val d = try { Duration.parse(duration) } catch (_: Exception) { throw CliError(ExitCode.USAGE, "--duration not parseable as ISO 8601: $duration") }
    return OffsetDateTime.parse(start).plus(d).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }
  // default 1h
  return OffsetDateTime.parse(start).plusHours(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

private fun resolveCalendar(store: RepoStore, query: String?): RepoStore.CalendarRef {
  if (query != null) return store.resolveCalendar(query)
  val all = store.listCalendars()
  return when (all.size) {
    0 -> throw CliError(ExitCode.NOT_FOUND, "no calendars in this repo", hint = "create one: skb cal add --name <name>")
    1 -> all.single()
    else -> throw CliError(
      ExitCode.USAGE,
      "repo has ${all.size} calendars; pass --calendar to disambiguate",
      hint = "list: skb cal list",
    )
  }
}
