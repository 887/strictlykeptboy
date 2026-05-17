package com.eight87.skb.cli.commands

import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlDateTime
import com.eight87.skb.cli.core.TomlString
import com.eight87.skb.cli.core.TomlTable
import com.eight87.skb.cli.core.TomlValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * `skb tz` command group. Currently surfaces `tz convert` only — see
 * Round 2.24 Phase F (D-2.24.f).
 *
 * SOLID.S: this file owns the CLI surface for tz conversion. Conversion
 * arithmetic + entity lookup are local helpers because we don't expect
 * a second caller.
 */
class TzGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "tz") {
  init {
    subcommands(TzConvert(ctxOf))
  }
  override fun run() = Unit
}

private class TzConvert(val ctxOf: () -> CliContext) : CliktCommand(name = "convert") {
  val idArg by argument("event-id")
  val newTz by argument("new-tz")
  val shiftInstant by option(
    "--shift-instant",
    help = "preserve absolute UTC instant (default preserves local clock time)",
  ).flag()

  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val store = RepoStore(root)

    // Validate the requested zone up front.
    val newZone = try {
      ZoneId.of(newTz)
    } catch (e: Exception) {
      throw CliError(
        ExitCode.USAGE,
        "invalid timezone \"$newTz\": ${e.message ?: e::class.java.simpleName}",
        hint = "use an IANA zone id (e.g. Europe/Berlin, America/New_York)",
      )
    }

    // Walk events + recurrences buckets across every calendar; match by id prefix.
    data class Match(val file: Path, val table: TomlTable, val body: String, val kind: String)
    val matches = mutableListOf<Match>()
    for (cal in store.listCalendars()) {
      val calDir = root.resolve("calendars/${cal.id}")
      for (sub in listOf("events", "recurrences")) {
        val dir = calDir.resolve(sub)
        if (!Files.isDirectory(dir)) continue
        Files.walk(dir).use { stream ->
          stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
            val name = f.fileName.toString().removeSuffix(".md")
            if (name.startsWith(idArg)) {
              val (t, b) = Frontmatter.parse(Files.readString(f))
              val id = t.getString("id") ?: name
              if (id.startsWith(idArg)) {
                matches.add(Match(f, t, b, t.getString("kind") ?: sub))
              }
            }
          }
        }
      }
    }

    when {
      matches.isEmpty() -> throw CliError(ExitCode.NOT_FOUND, "no event or recurrence matches \"$idArg\"")
      matches.size > 1 -> throw CliError(
        ExitCode.CONFLICT,
        "id prefix \"$idArg\" matches ${matches.size} entries",
        hint = "use a longer id prefix",
      )
    }
    val m = matches.single()
    val table = m.table
    val oldTzStr = table.getString("tz_id")
    val oldZone = oldTzStr?.let {
      try { ZoneId.of(it) } catch (_: Exception) { null }
    } ?: ZoneId.of("UTC")

    val isRule = m.kind == "recurrence"
    val title = table.getString("title") ?: m.file.fileName.toString().removeSuffix(".md")

    // Rewrite — preserve insertion order by walking the source table's
    // entries, swapping the timed fields + tz_id.
    val rewritten = rewriteTable(table, oldZone, newZone, shiftInstant, isRule)

    // Atomically write + commit. Reuse [atomicWriteAndCommit] for the
    // commit message format the CLI uses elsewhere.
    val mode = if (shiftInstant) "instant" else "local"
    val msg = "tz: convert \"$title\" → ${newZone.id} ($mode)"
    val res = atomicWriteAndCommit(ctx, root, m.file, rewritten, m.body, msg)
    val rel = root.relativize(m.file).toString()
    val newStart = rewritten.getDateTime("start") ?: rewritten.getDateTime("dtstart")
    emitHuman(
      buildString {
        appendLine(if (res is CommitResult.DryRun) "DRY RUN: would convert tz" else "✓ converted tz")
        appendLine("  id:       ${table.getString("id")}")
        appendLine("  title:    $title")
        appendLine("  from:     ${oldTzStr ?: "(unset / UTC)"}")
        appendLine("  to:       ${newZone.id}")
        appendLine("  mode:     $mode")
        appendLine("  file:     $rel")
        when (res) {
          is CommitResult.Committed -> res.sha?.let { append("  commit:   $it") }
          is CommitResult.DryRun -> append("  commit:   (dry-run)")
        }
      }.trimEnd(), ctx,
    )
    emitJson(
      JsonEnvelope.success(
        "tz.convert",
        buildJsonObject {
          put("id", JsonPrimitive(table.getString("id")))
          put("kind", JsonPrimitive(m.kind))
          put("old_tz", JsonPrimitive(oldTzStr))
          put("new_tz", JsonPrimitive(newZone.id))
          put("mode", JsonPrimitive(mode))
          put("path", JsonPrimitive(rel))
          newStart?.let { put("start", JsonPrimitive(it)) }
          put("dry_run", JsonPrimitive(res is CommitResult.DryRun))
          if (res is CommitResult.Committed) res.sha?.let { put("commit", JsonPrimitive(it)) }
        },
      ),
      ctx,
    )
  }

  /**
   * Builds a new TomlTable preserving the original key insertion order,
   * but with `tz_id` swapped to [newZone] and any timed fields
   * (`start`/`end` for events, `dtstart` for rules) shifted per
   * D-2.24.f.
   *
   * Default ([shiftInstant] = false): preserve the LOCAL clock time —
   * 09:00 in old tz becomes 09:00 in new tz (UTC instant shifts).
   * [shiftInstant] = true: preserve the absolute UTC instant — 09:00
   * NYC stays the same instant but renders 15:00 in Berlin.
   */
  private fun rewriteTable(
    src: TomlTable,
    oldZone: ZoneId,
    newZone: ZoneId,
    shiftInstant: Boolean,
    isRule: Boolean,
  ): TomlTable {
    val out = TomlTable()
    val timedKeys = if (isRule) setOf("dtstart") else setOf("start", "end")
    val tzKey = "tz_id"
    var sawTz = false
    for ((k, v) in src.entries()) {
      when {
        k == tzKey -> {
          out.putString(tzKey, newZone.id); sawTz = true
        }
        k in timedKeys -> {
          val converted = convertField(v, oldZone, newZone, shiftInstant, isRule, k)
          out.put(k, converted)
        }
        else -> out.put(k, v)
      }
    }
    if (!sawTz) out.putString(tzKey, newZone.id)
    return out
  }

  private fun convertField(
    raw: TomlValue,
    oldZone: ZoneId,
    newZone: ZoneId,
    shiftInstant: Boolean,
    isRule: Boolean,
    key: String,
  ): TomlValue {
    // Extract textual datetime regardless of whether MiniToml parsed it
    // as TomlDateTime or TomlString.
    val text = when (raw) {
      is TomlDateTime -> raw.isoOffset
      is TomlString -> raw.v
      else -> throw CliError(ExitCode.CORRUPT, "field \"$key\" is not a datetime: ${raw.emit()}")
    }
    val wasBare = raw is TomlDateTime

    val newText = convertDateTimeText(text, oldZone, newZone, shiftInstant, preferBare = wasBare || isRule)
    return TomlDateTime(newText)
  }

  /**
   * Convert a single datetime text from [oldZone] to [newZone].
   *
   * - [shiftInstant] = true: parse the source as an instant (using its
   *   own offset if present, else [oldZone]), then re-zone into
   *   [newZone] and emit in the new zone's wall-clock form.
   * - [shiftInstant] = false (default): take the source's wall-clock
   *   fields and emit them verbatim in [newZone], preserving the
   *   local hour/min/sec.
   *
   * Output shape:
   * - if input was bare AND caller prefers bare (rule dtstart):
   *   emit bare ISO_LOCAL_DATE_TIME.
   * - else: emit ISO_OFFSET_DATE_TIME with the new zone's offset at the
   *   target local time.
   */
  private fun convertDateTimeText(
    text: String,
    oldZone: ZoneId,
    newZone: ZoneId,
    shiftInstant: Boolean,
    preferBare: Boolean,
  ): String {
    val hasOffset = text.endsWith("Z") ||
      (text.length > 6 && (text[text.length - 6] == '+' || text[text.length - 6] == '-'))

    val localPart: LocalDateTime
    val sourceZone: ZoneId
    if (hasOffset) {
      val odt = OffsetDateTime.parse(text)
      localPart = odt.toLocalDateTime()
      sourceZone = odt.offset
    } else {
      localPart = LocalDateTime.parse(text)
      sourceZone = oldZone
    }

    val targetLocal: LocalDateTime = if (shiftInstant) {
      val srcZdt = ZonedDateTime.of(localPart, sourceZone)
      srcZdt.withZoneSameInstant(newZone).toLocalDateTime()
    } else {
      localPart
    }

    val emitBare = preferBare && !hasOffset
    return if (emitBare) {
      targetLocal.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } else {
      val zdt = ZonedDateTime.of(targetLocal, newZone)
      zdt.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
  }
}
