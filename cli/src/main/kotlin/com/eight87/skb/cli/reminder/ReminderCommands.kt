package com.eight87.skb.cli.reminder

import com.eight87.skb.cli.attachment.locateEventFile
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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.time.Duration

/**
 * Phase BBB.14 / CLI-U — `skb reminder` command group.
 *
 * Surfaces:
 *
 *   - `skb reminder add --event <id> --offset <iso-8601-or-"0"> --kind <kind> [--channel <ch>] [--lockscreen <vis>]`
 *
 *   - `skb reminder rm --event <id> --offset <offset> --kind <kind>`
 *
 *   - `skb reminder list --event <id>`
 *
 * `kind` is one of `heads_up`, `all_day_banner`, `tomorrow_briefing`,
 * `pre_event`, `at_start`, `post_event_checkin` (DM-X.3).
 *
 * SOLID-S: reminder-array codec for a single event — its only
 * responsibility. SOLID-D: depends only on `:cli/.../core/` and the
 * sibling locator from `:cli/attachment/`.
 */
class ReminderGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "reminder") {
    init { subcommands(ReminderAdd(ctxOf), ReminderRm(ctxOf), ReminderList(ctxOf)) }
    override fun run() = Unit
}

private val ALLOWED_KINDS = setOf(
    "heads_up", "all_day_banner", "tomorrow_briefing",
    "pre_event", "at_start", "post_event_checkin",
)

private fun validateOffset(s: String) {
    val trimmed = s.trim()
    if (trimmed == "0" || trimmed == "PT0S" || trimmed == "P0D") return
    try {
        Duration.parse(trimmed)
    } catch (e: Exception) {
        throw CliError(ExitCode.USAGE, "--offset must be ISO-8601 duration or \"0\" (got: $s)")
    }
}

private fun validateKind(s: String) {
    if (s !in ALLOWED_KINDS) {
        throw CliError(ExitCode.USAGE, "--kind must be one of ${ALLOWED_KINDS.joinToString(", ")}")
    }
}

private class ReminderAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
    val event by option("--event").required()
    val offset by option("--offset").required()
    val kind by option("--kind").required()
    val channel by option("--channel")
    val lockscreen by option("--lockscreen", help = "public | private | secret")
    val skipCommit by option("--skip-commit").flag()

    override fun run() {
        val ctx = ctxOf()
        validateOffset(offset)
        validateKind(kind)
        lockscreen?.let {
            if (it !in setOf("public", "private", "secret"))
                throw CliError(ExitCode.USAGE, "--lockscreen must be public | private | secret")
        }
        val root = ctx.repoRoot()
        val eventFile = locateEventFile(root, event)
            ?: throw CliError(ExitCode.NOT_FOUND, "event $event not found")
        val text = Files.readString(eventFile)

        // Build [[reminder]] block (string-level).
        val sb = StringBuilder("\n[[reminder]]\n")
        sb.append("offset = \"$offset\"\n")
        sb.append("kind = \"$kind\"\n")
        channel?.let { sb.append("channel = \"$it\"\n") }
        lockscreen?.let { sb.append("lockscreen_visibility = \"$it\"\n") }

        val updated = injectBlockBeforeClosingFence(text, sb.toString())

        if (ctx.dryRun) {
            emitHuman("DRY RUN: would add reminder $kind @$offset to $event", ctx)
            return
        }
        AtomicWriter.writeUtf8(eventFile, updated)
        val sha: String? = if (skipCommit) null else {
            val id = IdentityResolver.resolve(root)
            gitOps().addAndCommit(
                repoRoot = root,
                files = listOf(root.relativize(eventFile).toString()),
                message = "reminder add $kind @$offset to event $event",
                authorName = id.displayName,
                authorEmail = id.email,
            )
        }
        if (ctx.json) {
            emitJson(
                JsonEnvelope.success(
                    "reminder add",
                    buildJsonObject {
                        put("event_id", JsonPrimitive(event))
                        put("offset", JsonPrimitive(offset))
                        put("kind", JsonPrimitive(kind))
                        sha?.let { put("commit_sha", JsonPrimitive(it)) }
                    },
                ),
                ctx,
            )
        } else {
            emitHuman("✓ reminder added $kind @$offset to $event", ctx)
        }
    }
}

private class ReminderRm(val ctxOf: () -> CliContext) : CliktCommand(name = "rm") {
    val event by option("--event").required()
    val offset by option("--offset").required()
    val kind by option("--kind").required()
    val skipCommit by option("--skip-commit").flag()

    override fun run() {
        val ctx = ctxOf()
        validateOffset(offset); validateKind(kind)
        val root = ctx.repoRoot()
        val eventFile = locateEventFile(root, event)
            ?: throw CliError(ExitCode.NOT_FOUND, "event $event not found")
        val text = Files.readString(eventFile)

        // Remove the matching [[reminder]] block.
        val lines = text.split('\n')
        val out = mutableListOf<String>()
        var i = 0
        var removed = false
        while (i < lines.size) {
            if (lines[i].trim() == "[[reminder]]") {
                val block = mutableListOf(lines[i])
                var j = i + 1
                while (j < lines.size && !lines[j].trim().startsWith("[[") && lines[j].trim() != "+++" && !lines[j].trim().startsWith("[")) {
                    block += lines[j]; j++
                }
                val blockText = block.joinToString("\n")
                val matches = blockText.contains("offset = \"$offset\"") && blockText.contains("kind = \"$kind\"")
                if (matches && !removed) {
                    removed = true
                    i = j
                    continue
                }
                out += block
                i = j
            } else {
                out += lines[i]; i++
            }
        }
        if (!removed) {
            throw CliError(ExitCode.NOT_FOUND, "no reminder matching offset=$offset kind=$kind on $event")
        }
        val updated = out.joinToString("\n")
        if (ctx.dryRun) {
            emitHuman("DRY RUN: would remove reminder $kind @$offset from $event", ctx)
            return
        }
        AtomicWriter.writeUtf8(eventFile, updated)
        val sha: String? = if (skipCommit) null else {
            val id = IdentityResolver.resolve(root)
            gitOps().addAndCommit(
                repoRoot = root,
                files = listOf(root.relativize(eventFile).toString()),
                message = "reminder rm $kind @$offset from event $event",
                authorName = id.displayName,
                authorEmail = id.email,
            )
        }
        if (ctx.json) {
            emitJson(
                JsonEnvelope.success(
                    "reminder rm",
                    buildJsonObject {
                        put("event_id", JsonPrimitive(event))
                        put("offset", JsonPrimitive(offset))
                        put("kind", JsonPrimitive(kind))
                        sha?.let { put("commit_sha", JsonPrimitive(it)) }
                    },
                ),
                ctx,
            )
        } else {
            emitHuman("✓ removed reminder $kind @$offset from $event", ctx)
        }
    }
}

private class ReminderList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
    val event by option("--event").required()

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()
        val eventFile = locateEventFile(root, event)
            ?: throw CliError(ExitCode.NOT_FOUND, "event $event not found")
        val text = Files.readString(eventFile)

        val blocks = mutableListOf<Map<String, String>>()
        var current: MutableMap<String, String>? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line == "[[reminder]]") {
                current?.let { blocks += it }
                current = mutableMapOf()
                continue
            }
            if (line.startsWith("[[") || line.startsWith("[") || line == "+++") {
                current?.let { blocks += it }
                current = null
                continue
            }
            val cur = current ?: continue
            val eq = line.indexOf('=')
            if (eq < 0) continue
            cur[line.substring(0, eq).trim()] = line.substring(eq + 1).trim().trim('"')
        }
        current?.let { blocks += it }
        if (ctx.json) {
            val arr = JsonArray(blocks.map { b ->
                buildJsonObject { for ((k, v) in b) put(k, JsonPrimitive(v)) }
            })
            emitJson(
                JsonEnvelope.success(
                    "reminder list",
                    buildJsonObject {
                        put("event_id", JsonPrimitive(event))
                        put("count", JsonPrimitive(blocks.size))
                        put("reminders", arr)
                    },
                ),
                ctx,
            )
        } else {
            if (blocks.isEmpty()) emitHuman("no reminders on $event", ctx)
            else for ((idx, b) in blocks.withIndex()) {
                val k = b["kind"] ?: "?"
                val off = b["offset"] ?: "?"
                emitHuman("[${idx + 1}] $k  $off", ctx)
            }
        }
    }
}

private fun injectBlockBeforeClosingFence(originalText: String, block: String): String {
    val lines = originalText.split('\n').toMutableList()
    var inFront = false
    var closingIndex = -1
    for ((idx, line) in lines.withIndex()) {
        if (line.trim() == "+++") {
            if (!inFront) inFront = true
            else { closingIndex = idx; break }
        }
    }
    if (closingIndex <= 0) {
        return originalText.trimEnd() + "\n" + block
    }
    lines.add(closingIndex, block.trimEnd())
    return lines.joinToString("\n")
}
