package com.eight87.skb.cli.routine

import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.commands.gitOps
import com.eight87.skb.cli.commands.nowIso
import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.IdentityResolver
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.MiniToml
import com.eight87.skb.cli.core.RepoLayout
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlTable
import com.eight87.skb.cli.core.Uuid7
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Phase XX.8 / AT-H.6 — `skb routine` command group.
 *
 * Two subcommands:
 *
 *   - `skb routine start <routine-id> [--at <iso>] [--target <calendar-id>] [--skip <entry-id>...]`
 *      Materialize a routine's atomic events into the target calendar
 *      back-to-back from `--at` (default `now`), each event using its
 *      source's duration. Source = events under the routine calendar's
 *      `events/` tree. Single git commit
 *      `materialize routine "<name>" at <start-time>`.
 *
 *   - `skb routine undo <materialized-at>`
 *      Delete every event whose frontmatter `materialized_at` exactly
 *      matches the argument. Single revert commit.
 *
 * SOLID-S/-D: this file is the CLI wiring. The slot-assignment
 * algorithm lives at `:app`'s [com.eight87.strictlykeptboy.store.RoutineMaterializer]
 * conceptually, but the CLI doesn't depend on `:app`. Here we inline a
 * pared-back walk-forward — same overlap-detection rule, same audit
 * frontmatter — so the CLI doesn't drag the Android module into its
 * classpath. (Future SOLID consolidation: extract the algorithm into a
 * shared `:core` module if a third surface needs it.)
 */
class RoutineGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "routine") {
    init { subcommands(RoutineStart(ctxOf), RoutineUndo(ctxOf)) }
    override fun run() = Unit
}

private class RoutineStart(val ctxOf: () -> CliContext) : CliktCommand(name = "start") {
    val routineArg by argument("routine-id-or-name")
    val atIso by option("--at", help = "ISO start-time (default: now)")
    val target by option("--target", help = "target calendar id or name (default: first non-routine calendar)")
    val skipEntries by option("--skip", help = "skip a source event by id; may repeat").multiple()

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()
        val store = RepoStore(root)

        val routineCal = store.resolveCalendar(routineArg)
        // Per AT-G.1 routine calendars carry `routine = true`. Refuse if not.
        val routineMeta = readCalendarMeta(routineCal.path)
        if (routineMeta.getBool("routine") != true) {
            throw CliError(
                ExitCode.USAGE,
                "calendar \"${routineCal.name}\" is not a routine (missing routine = true in calendar.toml)",
                hint = "see Phase XX.7 (AT-G) for the routine calendar contract",
            )
        }
        val routineId = routineMeta.getString("routine_id") ?: routineCal.id

        val targetCal = if (target != null) store.resolveCalendar(target!!)
        else firstNonRoutineCalendar(store)

        val start = parseAt(atIso ?: nowIso())
        val sources = readRoutineSourceEvents(root, routineCal.id)
            .filter { it.id !in skipEntries }
        if (sources.isEmpty()) {
            throw CliError(
                ExitCode.NOT_FOUND,
                "routine \"${routineCal.name}\" has no source events to materialize",
                hint = "add atomic events under calendars/${routineCal.id}/events/ first",
            )
        }

        // Overlap guard (AT-H.4) — refuse silent overwrite by default.
        val existing = readExistingSlots(root, targetCal.id)
        val plan = planSequential(sources, start, existing)
        if (plan.conflicts.isNotEmpty()) {
            // CLI surface: error with details. Sheet UI is the Compose path.
            throw CliError(
                ExitCode.CONFLICT,
                "${plan.conflicts.size} conflict(s) with existing event(s) in target calendar",
                hint = "shift target window, skip the conflicting source(s) with --skip <id>, or pick a different --target",
                details = mapOf("conflicts" to plan.conflicts.size),
            )
        }

        val materializedAt = nowIso()
        val identity = IdentityResolver.resolve(root)
        val files = mutableListOf<Path>()
        for (slot in plan.planned) {
            val newId = Uuid7.generate()
            val target = RepoLayout.event(root, targetCal.id, newId, slot.start)
            val table = TomlTable().apply {
                putString("id", newId)
                putString("kind", "event")
                putString("title", slot.title)
                putDateTime("start", slot.start)
                putDateTime("end", slot.end)
                putString("calendar_id", targetCal.id)
                if (slot.tags.isNotEmpty()) putStringArray("tags", slot.tags)
                putDateTime("created_at", materializedAt)
                putDateTime("updated_at", materializedAt)
                // Phase XX.8 audit fields LOCKED.
                putString("materialized_from", routineId)
                putString("materialized_source_event", slot.sourceEventId)
                putDateTime("materialized_at", materializedAt)
            }
            val content = Frontmatter.serialize(table, body = "")
            if (!ctx.dryRun) AtomicWriter.writeUtf8(target, content)
            files.add(target)
        }

        val msg = "materialize routine \"${routineCal.name}\" at $start"
        val sha = if (!ctx.dryRun) {
            gitOps().addAndCommit(
                repoRoot = root,
                files = files.map { root.relativize(it).toString() },
                message = msg,
                authorName = identity.displayName,
                authorEmail = identity.email,
            )
        } else null

        val relFiles = files.map { root.relativize(it).toString() }
        emitHuman(
            buildString {
                appendLine(
                    if (ctx.dryRun) "DRY RUN: would materialize ${plan.planned.size} event(s) for \"${routineCal.name}\""
                    else "✓ materialized ${plan.planned.size} event(s) for \"${routineCal.name}\"",
                )
                appendLine("  routine_id:      $routineId")
                appendLine("  target:          ${targetCal.name}")
                appendLine("  materialized_at: $materializedAt")
                for (p in relFiles) appendLine("  • $p")
                if (sha != null) append("  commit:          $sha  \"$msg\"")
                else if (ctx.dryRun) append("  commit:          (dry-run) \"$msg\"")
            }, ctx,
        )
        emitJson(
            JsonEnvelope.success(
                "routine.start",
                buildJsonObject {
                    put("routine_id", JsonPrimitive(routineId))
                    put("routine_calendar_id", JsonPrimitive(routineCal.id))
                    put("target_calendar_id", JsonPrimitive(targetCal.id))
                    put("materialized_at", JsonPrimitive(materializedAt))
                    put("count", JsonPrimitive(plan.planned.size))
                    put("dry_run", JsonPrimitive(ctx.dryRun))
                    put(
                        "events",
                        JsonArray(
                            plan.planned.zip(relFiles) { slot, path ->
                                buildJsonObject {
                                    put("title", JsonPrimitive(slot.title))
                                    put("start", JsonPrimitive(slot.start))
                                    put("end", JsonPrimitive(slot.end))
                                    put("source_event_id", JsonPrimitive(slot.sourceEventId))
                                    put("path", JsonPrimitive(path))
                                }
                            },
                        ),
                    )
                    sha?.let { put("commit", JsonPrimitive(it)) }
                },
            ),
            ctx,
        )
    }
}

private class RoutineUndo(val ctxOf: () -> CliContext) : CliktCommand(name = "undo") {
    val materializedAt by argument("materialized-at")

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()
        val store = RepoStore(root)
        val matches = mutableListOf<Path>()
        for (cal in store.listCalendars()) {
            val dir = root.resolve("calendars/${cal.id}/events")
            if (!Files.isDirectory(dir)) continue
            Files.walk(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
                    val (table, _) = Frontmatter.parse(Files.readString(f))
                    if (table.getDateTime("materialized_at") == materializedAt) matches.add(f)
                }
            }
        }
        if (matches.isEmpty()) {
            throw CliError(
                ExitCode.NOT_FOUND,
                "no events with materialized_at = $materializedAt",
                hint = "list materializations with: skb event list --json | jq '.events[] | select(.materialized_at?)' (Round 3)",
            )
        }
        val relPaths = matches.map { root.relativize(it).toString() }
        if (!ctx.dryRun) {
            for (m in matches) Files.deleteIfExists(m)
            val identity = IdentityResolver.resolve(root)
            gitOps().addAndCommit(
                repoRoot = root,
                files = relPaths,
                message = "undo materialize routine ($materializedAt)",
                authorName = identity.displayName,
                authorEmail = identity.email,
            )
        }
        emitHuman(
            buildString {
                appendLine(
                    if (ctx.dryRun) "DRY RUN: would delete ${matches.size} event(s)"
                    else "✓ deleted ${matches.size} event(s)",
                )
                for (p in relPaths) appendLine("  • $p")
            }.trimEnd(), ctx,
        )
        emitJson(
            JsonEnvelope.success(
                "routine.undo",
                buildJsonObject {
                    put("materialized_at", JsonPrimitive(materializedAt))
                    put("count", JsonPrimitive(matches.size))
                    put("dry_run", JsonPrimitive(ctx.dryRun))
                    put("files", JsonArray(relPaths.map { JsonPrimitive(it) }))
                },
            ),
            ctx,
        )
    }
}

// --- helpers ----------------------------------------------------------------

private fun readCalendarMeta(metaPath: Path): TomlTable {
    val text = Files.readString(metaPath)
    val (table, _) = if (text.trimStart().startsWith("+++")) Frontmatter.parse(text)
        else MiniToml.parse(text) to ""
    return table
}

private fun firstNonRoutineCalendar(store: RepoStore): RepoStore.CalendarRef {
    val all = store.listCalendars()
    if (all.isEmpty()) throw CliError(ExitCode.NOT_FOUND, "no calendars in this repo")
    for (cal in all) {
        if (readCalendarMeta(cal.path).getBool("routine") != true) return cal
    }
    throw CliError(
        ExitCode.NOT_FOUND,
        "every calendar in this repo is a routine; pass --target explicitly",
    )
}

internal data class SourceEvent(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val tags: List<String>,
)

private fun readRoutineSourceEvents(root: Path, calendarId: String): List<SourceEvent> {
    val dir = root.resolve("calendars/$calendarId/events")
    if (!Files.isDirectory(dir)) return emptyList()
    val out = mutableListOf<SourceEvent>()
    Files.walk(dir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
            val (table, _) = Frontmatter.parse(Files.readString(f))
            val id = table.getString("id") ?: f.fileName.toString().removeSuffix(".md")
            val title = table.getString("title") ?: return@forEach
            val start = table.getDateTime("start") ?: return@forEach
            val end = table.getDateTime("end") ?: return@forEach
            val dur = try {
                val s = OffsetDateTime.parse(start)
                val e = OffsetDateTime.parse(end)
                ((e.toEpochSecond() - s.toEpochSecond()) / 60).toInt().coerceAtLeast(1)
            } catch (_: Exception) {
                return@forEach
            }
            out.add(SourceEvent(id = id, title = title, durationMinutes = dur, tags = table.getStringArray("tags") ?: emptyList()))
        }
    }
    return out.sortedBy { it.id }
}

internal data class ExistingSlot(val start: OffsetDateTime, val end: OffsetDateTime)

private fun readExistingSlots(root: Path, calendarId: String): List<ExistingSlot> {
    val dir = root.resolve("calendars/$calendarId/events")
    if (!Files.isDirectory(dir)) return emptyList()
    val out = mutableListOf<ExistingSlot>()
    Files.walk(dir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
            val (table, _) = Frontmatter.parse(Files.readString(f))
            val s = table.getDateTime("start") ?: return@forEach
            val e = table.getDateTime("end") ?: return@forEach
            try { out.add(ExistingSlot(OffsetDateTime.parse(s), OffsetDateTime.parse(e))) } catch (_: Exception) {}
        }
    }
    return out
}

internal data class SequentialPlan(val planned: List<PlannedSlot>, val conflicts: List<PlannedSlot>)

internal data class PlannedSlot(
    val sourceEventId: String,
    val title: String,
    val start: String,
    val end: String,
    val tags: List<String>,
)

internal fun planSequential(
    entries: List<SourceEvent>,
    startIso: String,
    existing: List<ExistingSlot>,
): SequentialPlan {
    val planned = mutableListOf<PlannedSlot>()
    val conflicts = mutableListOf<PlannedSlot>()
    var cursor = OffsetDateTime.parse(startIso)
    for (e in entries) {
        val end = cursor.plusMinutes(e.durationMinutes.toLong())
        val slot = PlannedSlot(
            sourceEventId = e.id,
            title = e.title,
            start = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(cursor),
            end = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(end),
            tags = e.tags,
        )
        val collided = existing.any { it.start.isBefore(end) && cursor.isBefore(it.end) }
        if (collided) conflicts += slot
        planned += slot
        cursor = end
    }
    return SequentialPlan(planned, conflicts)
}

private fun parseAt(s: String): String {
    try { return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.parse(s)) } catch (_: Exception) {}
    // Tolerate `HH:mm` — interpret today + offset UTC.
    try {
        val t = LocalTime.parse(s)
        val today = java.time.LocalDate.now(ZoneOffset.UTC)
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(today.atTime(t).atOffset(ZoneOffset.UTC))
    } catch (_: Exception) {}
    throw CliError(ExitCode.USAGE, "--at not parseable: $s")
}
