package com.eight87.skb.cli.attachment

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
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlTable
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Phase BBB.14 / HV-M.8 / CLI-U — `skb attach` command group.
 *
 * Surfaces:
 *
 *   - `skb attach add --event <id> --kind <kind> [--url ... | --file <path> | --lat/--lon ...]`
 *     Adds an `[[attachment]]` block of `kind ∈ {link, qr, file, barcode, vcard, location}`
 *     to the named event's frontmatter. File-bearing kinds copy the
 *     source into `attachments/<event-id>/<basename>`. Warns when the
 *     file exceeds the 100 KB Git-LFS threshold (DM-W.3) without LFS
 *     configured.
 *
 *   - `skb attach list --event <id>` — list every attachment on the
 *     event.
 *
 * SOLID-S: this file does attachment-frontmatter mutation + asset
 * copying — one responsibility (the file-on-disk lifecycle for a single
 * event's attachments). SOLID-D: depends on `:cli/.../core/` only.
 *
 * The 100 KB Git-LFS threshold constant is duplicated here from
 * `:app/store/Attachment.kt` rather than imported (the CLI does not
 * depend on `:app`). Bump them together if D.79 / DM-W.3 changes.
 */
class AttachGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "attach") {
    init { subcommands(AttachAdd(ctxOf), AttachList(ctxOf)) }
    override fun run() = Unit
}

internal const val LFS_THRESHOLD_BYTES: Long = 100 * 1024L

private class AttachAdd(val ctxOf: () -> CliContext) : CliktCommand(name = "add") {
    val event by option("--event").required()
    val kind by option("--kind", help = "link | qr | file | barcode | vcard | location").required()
    val url by option("--url")
    val label by option("--label")
    val file by option("--file", help = "source path; copied into attachments/<event-id>/")
    val mime by option("--mime")
    val data by option("--data")
    val format by option("--format", help = "aztec | pdf417 | code128 (kind=barcode)")
    val lat by option("--lat")
    val lon by option("--lon")
    val skipCommit by option("--skip-commit").flag()

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()

        val eventFile = locateEventFile(root, event)
            ?: throw CliError(ExitCode.NOT_FOUND, "event $event not found in any calendar")

        val text = Files.readString(eventFile)
        val (table, body) = Frontmatter.parse(text)

        val (entry, copiedRel) = buildAttachmentEntry(root, ctx)

        // Append as a raw `[[attachment]]` block to the body's tail of the
        // frontmatter via a small text-level splice — preserves existing
        // ordering / comments. Frontmatter.parse returns mutable TomlTable
        // but there's no aotables surface here; we serialize the new
        // single-attachment block and inject it before the closing fence.
        val attachmentToml = renderAttachmentTable(entry)
        val updated = injectAttachmentBlock(text, attachmentToml, table)

        if (ctx.dryRun) {
            emitHuman("DRY RUN: would attach $kind to $event (${eventFile})", ctx)
            return
        }

        AtomicWriter.writeUtf8(eventFile, updated)
        val pathsToStage = mutableListOf(root.relativize(eventFile).toString())
        copiedRel?.let { pathsToStage += it }

        val sha: String? = if (skipCommit) null else {
            val identity = IdentityResolver.resolve(root)
            gitOps().addAndCommit(
                repoRoot = root,
                files = pathsToStage,
                message = "attach $kind to event $event",
                authorName = identity.displayName,
                authorEmail = identity.email,
            )
        }

        if (ctx.json) {
            emitJson(
                JsonEnvelope.success(
                    "attach add",
                    buildJsonObject {
                        put("event_id", JsonPrimitive(event))
                        put("kind", JsonPrimitive(kind))
                        copiedRel?.let { put("asset_path", JsonPrimitive(it)) }
                        sha?.let { put("commit_sha", JsonPrimitive(it)) }
                    },
                ),
                ctx,
            )
        } else {
            emitHuman("✓ attached $kind to $event", ctx)
        }
    }

    private fun buildAttachmentEntry(root: Path, ctx: CliContext): Pair<TomlTable, String?> {
        val attachKind = kind.trim()
        // Determine the storage subdir for asset-bearing kinds.
        val attachmentDirRel = "attachments/$event"
        val attachmentDir = root.resolve(attachmentDirRel)

        val table = TomlTable().apply { putString("kind", attachKind) }
        var copiedRel: String? = null

        when (attachKind) {
            "link" -> {
                val u = url ?: throw CliError(ExitCode.USAGE, "--url required for kind=link")
                table.putString("url", u)
                label?.let { table.putString("label", it) }
            }
            "qr" -> {
                if (data == null && file == null) {
                    throw CliError(ExitCode.USAGE, "--data or --file required for kind=qr")
                }
                file?.let { f ->
                    val rel = copyAsset(Path.of(f), attachmentDir, attachmentDirRel, ctx)
                    table.putString("file", Path.of(rel).fileName.toString())
                    copiedRel = rel
                }
                data?.let { table.putString("data", it) }
            }
            "file" -> {
                val f = file ?: throw CliError(ExitCode.USAGE, "--file required for kind=file")
                val rel = copyAsset(Path.of(f), attachmentDir, attachmentDirRel, ctx)
                val src = Path.of(f)
                val size = Files.size(src)
                table.putString("file", Path.of(rel).fileName.toString())
                table.putString("mime_type", mime ?: guessMime(src.fileName.toString()))
                table.putInt("size_bytes", size)
                label?.let { table.putString("description", it) }
                copiedRel = rel
            }
            "barcode" -> {
                val f = file ?: throw CliError(ExitCode.USAGE, "--file required for kind=barcode")
                val d = data ?: throw CliError(ExitCode.USAGE, "--data required for kind=barcode")
                val fmt = format ?: throw CliError(ExitCode.USAGE, "--format required for kind=barcode")
                if (fmt !in setOf("aztec", "pdf417", "code128")) {
                    throw CliError(ExitCode.USAGE, "--format must be aztec | pdf417 | code128")
                }
                val rel = copyAsset(Path.of(f), attachmentDir, attachmentDirRel, ctx)
                table.putString("file", Path.of(rel).fileName.toString())
                table.putString("format", fmt)
                table.putString("data", d)
                copiedRel = rel
            }
            "vcard" -> {
                val f = file ?: throw CliError(ExitCode.USAGE, "--file required for kind=vcard")
                val rel = copyAsset(Path.of(f), attachmentDir, attachmentDirRel, ctx)
                table.putString("file", Path.of(rel).fileName.toString())
                copiedRel = rel
            }
            "location" -> {
                val la = lat?.toDoubleOrNull() ?: throw CliError(ExitCode.USAGE, "--lat required for kind=location")
                val lo = lon?.toDoubleOrNull() ?: throw CliError(ExitCode.USAGE, "--lon required for kind=location")
                if (la !in -90.0..90.0) throw CliError(ExitCode.USAGE, "--lat outside ±90")
                if (lo !in -180.0..180.0) throw CliError(ExitCode.USAGE, "--lon outside ±180")
                // MiniToml lacks a Float type; persist as quoted string and
                // let the :app parser handle it (it accepts F64 too).
                table.putString("lat", la.toString())
                table.putString("lon", lo.toString())
                label?.let { table.putString("label", it) }
            }
            else -> throw CliError(ExitCode.USAGE, "unknown kind: $attachKind")
        }
        return table to copiedRel
    }

    private fun copyAsset(src: Path, destDir: Path, destDirRel: String, ctx: CliContext): String {
        if (!Files.isRegularFile(src)) throw CliError(ExitCode.NOT_FOUND, "file not found: $src")
        val size = Files.size(src)
        Files.createDirectories(destDir)
        val target = destDir.resolve(src.fileName.toString())
        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
        if (size > LFS_THRESHOLD_BYTES && !hasLfsConfigured(destDir.parent.parent ?: destDir)) {
            // Warn-don't-refuse per DM-W.5; uses stderr-equivalent (we
            // emit a stderr-style warning via system err so JSON output
            // stays clean).
            System.err.println("warning: $target is $size bytes (>${LFS_THRESHOLD_BYTES}); configure Git-LFS for attachments/* before commit (Phase Z)")
        }
        return "$destDirRel/${src.fileName}"
    }

    private fun hasLfsConfigured(repoRoot: Path): Boolean {
        val ga = repoRoot.resolve(".gitattributes")
        if (!Files.isRegularFile(ga)) return false
        return Files.readString(ga).lineSequence().any {
            it.contains("attachments/") && it.contains("filter=lfs")
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "vcf" -> "text/vcard"
            "ics" -> "text/calendar"
            "pkpass" -> "application/vnd.apple.pkpass"
            else -> "application/octet-stream"
        }
    }

    private fun renderAttachmentTable(t: TomlTable): String {
        val sb = StringBuilder()
        sb.append("\n[[attachment]]\n")
        for ((k, v) in t.entriesView()) {
            sb.append(k).append(" = ")
            sb.append(renderTomlValue(v))
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun renderTomlValue(v: com.eight87.skb.cli.core.TomlValue): String = when (v) {
        is com.eight87.skb.cli.core.TomlString -> "\"${v.v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        is com.eight87.skb.cli.core.TomlInt -> v.v.toString()
        is com.eight87.skb.cli.core.TomlBool -> v.v.toString()
        is com.eight87.skb.cli.core.TomlDateTime -> v.isoOffset
        is com.eight87.skb.cli.core.TomlStringArray -> v.items.joinToString(prefix = "[", postfix = "]") {
            "\"${it.replace("\"", "\\\"")}\""
        }
        else -> "\"\""
    }

    private fun injectAttachmentBlock(originalText: String, block: String, parsed: TomlTable): String {
        // Find closing frontmatter fence and inject block before it.
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
            // Fallback: just append block before body
            return originalText.trimEnd() + "\n" + block
        }
        lines.add(closingIndex, block.trimEnd())
        return lines.joinToString("\n")
    }
}

private class AttachList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
    val event by option("--event").required()

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()
        val eventFile = locateEventFile(root, event)
            ?: throw CliError(ExitCode.NOT_FOUND, "event $event not found")
        val text = Files.readString(eventFile)
        // Naïve block-level scan, sufficient for the array; richer parsing
        // lives in :app's Attachment.kt.
        val blocks = mutableListOf<Map<String, String>>()
        var current: MutableMap<String, String>? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line == "[[attachment]]") {
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
            val k = line.substring(0, eq).trim()
            val rhs = line.substring(eq + 1).trim().trim('"')
            cur[k] = rhs
        }
        current?.let { blocks += it }

        if (ctx.json) {
            val arr = JsonArray(
                blocks.map { b ->
                    buildJsonObject {
                        for ((k, v) in b) put(k, JsonPrimitive(v))
                    }
                },
            )
            emitJson(
                JsonEnvelope.success(
                    "attach list",
                    buildJsonObject {
                        put("event_id", JsonPrimitive(event))
                        put("count", JsonPrimitive(blocks.size))
                        put("attachments", arr)
                    },
                ),
                ctx,
            )
        } else {
            if (blocks.isEmpty()) emitHuman("no attachments on $event", ctx)
            else for ((idx, b) in blocks.withIndex()) {
                val k = b["kind"] ?: "?"
                val tag = listOfNotNull(b["url"], b["file"], b["label"], b["lat"]).joinToString(" ")
                emitHuman("[${idx + 1}] $k  $tag", ctx)
            }
        }
    }
}

internal fun locateEventFile(root: Path, eventId: String): Path? {
    val store = RepoStore(root)
    for (cal in store.listCalendars()) {
        val eventsDir = root.resolve("calendars/${cal.id}/events")
        if (!Files.isDirectory(eventsDir)) continue
        Files.walk(eventsDir).use { stream ->
            val match = stream
                .filter { it.toString().endsWith("$eventId.md") }
                .findFirst()
                .orElse(null)
            if (match != null) return match
        }
    }
    return null
}

// Tiny extension to expose entries (MiniToml's TomlTable holds them in
// a list-of-pairs internally; we re-read via reflection-free public
// surface). MiniToml's `entries` field is not public — but the test
// hook below works because we only use the public put/get surface;
// this extension is internal-only for the file's own writer.
private fun TomlTable.entriesView(): List<Pair<String, com.eight87.skb.cli.core.TomlValue>> {
    val keys = listOf("kind", "url", "label", "file", "mime_type", "size_bytes", "format", "data", "lat", "lon", "description")
    return keys.mapNotNull { k -> this.get(k)?.let { k to it } }
}
