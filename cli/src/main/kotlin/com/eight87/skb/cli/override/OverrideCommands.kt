package com.eight87.skb.cli.override

import com.eight87.skb.cli.commands.CommitResult
import com.eight87.skb.cli.commands.atomicWriteAndCommit
import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.commands.nowIso
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlTable
import com.eight87.skb.cli.core.Uuid7
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

/**
 * Phase BBB.14 / HV-E.8 / CLI-U — `skb override` command group.
 *
 * Surfaces:
 *
 *   - `skb override add --calendar <id> --event <id> --date <yyyy-mm-dd> [--from <date> --to <date>] [--id <uuid7>]`
 *
 *     Writes `overrides/<calendar-id>/<event-id>/<yyyy-mm-dd>.md` per
 *     DM-AA.3 / D.77. Two `override_kind` values:
 *     `force-show` (single date) and `force-show-for-range` (range).
 *
 *   - `skb override list --calendar <id>` — list every override file
 *     scoped to a calendar.
 *
 * SOLID-S: the override-files lifecycle is the file's only reason to
 * change. SOLID-D: depends on `:cli/.../core/` primitives only.
 */
class OverrideGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "override") {
    init { subcommands(OverrideAdd(ctxOf), OverrideList(ctxOf)) }
    override fun run() = Unit
}

private class OverrideAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
    val calendar by option("--calendar", help = "superseded calendar id (where supersedence is being opted-out)").required()
    val event by option("--event", help = "event id").required()
    val date by option("--date", help = "instance date yyyy-mm-dd").required()
    val rangeFrom by option("--from", help = "start of range (kind = force-show-for-range)")
    val rangeTo by option("--to", help = "end of range (kind = force-show-for-range)")
    val explicitId by option("--id")

    override fun run() {
        val ctx = ctxOf()
        val instanceDate = LocalDate.parse(date)
        val rf = rangeFrom?.let { LocalDate.parse(it) }
        val rt = rangeTo?.let { LocalDate.parse(it) }
        if ((rf == null) != (rt == null)) {
            throw CliError(ExitCode.USAGE, "--from and --to must both be set or both absent")
        }
        rf?.let { from -> rt?.let { to ->
            if (to.isBefore(from)) throw CliError(ExitCode.USAGE, "--to is before --from")
        } }
        val kind = if (rf != null) "force-show-for-range" else "force-show"
        val id = validateUuid7(explicitId) ?: Uuid7.generate()

        val root = ctx.repoRoot()
        val store = RepoStore(root)
        // Validate calendar exists (we still write under the literal id).
        runCatching { store.resolveCalendar(calendar) }
            .onFailure { throw CliError(ExitCode.NOT_FOUND, "calendar $calendar not found") }
        val target = root
            .resolve("overrides")
            .resolve(calendar)
            .resolve(event)
            .resolve("$date.md")

        val table = TomlTable().apply {
            putString("id", id)
            putString("kind", "override")
            putString("override_kind", kind)
            putString("superseded_calendar_id", calendar)
            putString("event_id", event)
            putString("instance_date", instanceDate.toString())
            rf?.let { putString("from", it.toString()) }
            rt?.let { putString("to", it.toString()) }
            putDateTime("created_at", nowIso())
            putDateTime("updated_at", nowIso())
        }
        val msg = "override $kind for $event on $date"
        val res = atomicWriteAndCommit(ctx, root, target, table, "", msg)

        if (ctx.json) {
            val payload = buildJsonObject {
                put("override_id", JsonPrimitive(id))
                put("calendar_id", JsonPrimitive(calendar))
                put("event_id", JsonPrimitive(event))
                put("instance_date", JsonPrimitive(instanceDate.toString()))
                put("override_kind", JsonPrimitive(kind))
                put("path", JsonPrimitive(root.relativize(target).toString()))
                if (res is CommitResult.Committed) res.sha?.let { put("commit_sha", JsonPrimitive(it)) }
                if (res is CommitResult.DryRun) put("dry_run", JsonPrimitive(true))
            }
            emitJson(JsonEnvelope.success("override add", payload), ctx)
        } else {
            val prefix = if (res is CommitResult.DryRun) "DRY RUN: would write" else "✓ wrote"
            emitHuman("$prefix override $kind for $event on $date", ctx)
        }
    }
}

private class OverrideList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
    val calendar by option("--calendar").required()

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()
        val base = root.resolve("overrides").resolve(calendar)
        if (!java.nio.file.Files.isDirectory(base)) {
            if (ctx.json) {
                emitJson(JsonEnvelope.success("override list", buildJsonObject {
                    put("calendar_id", JsonPrimitive(calendar))
                    put("count", JsonPrimitive(0))
                }), ctx)
            } else {
                emitHuman("no overrides for $calendar", ctx)
            }
            return
        }
        val rows = mutableListOf<Triple<String, String, String>>() // event, date, kind
        java.nio.file.Files.walk(base).use { stream ->
            stream.filter { it.toString().endsWith(".md") }.forEach { p ->
                val text = java.nio.file.Files.readString(p)
                val eventId = base.relativize(p).getName(0).toString()
                val date = p.fileName.toString().removeSuffix(".md")
                val kind = Regex("""override_kind\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1) ?: "force-show"
                rows += Triple(eventId, date, kind)
            }
        }
        if (ctx.json) {
            val arr = kotlinx.serialization.json.JsonArray(rows.map { (e, d, k) ->
                buildJsonObject {
                    put("event_id", JsonPrimitive(e))
                    put("instance_date", JsonPrimitive(d))
                    put("override_kind", JsonPrimitive(k))
                }
            })
            emitJson(JsonEnvelope.success("override list", buildJsonObject {
                put("calendar_id", JsonPrimitive(calendar))
                put("count", JsonPrimitive(rows.size))
                put("overrides", arr)
            }), ctx)
        } else {
            for ((e, d, k) in rows) emitHuman("$d  $e  ($k)", ctx)
        }
    }
}

internal fun validateUuid7(raw: String?): String? {
    if (raw == null) return null
    if (!raw.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))) {
        throw CliError(ExitCode.USAGE, "--id must be a lowercase UUIDv7 (got: $raw)")
    }
    return raw
}
