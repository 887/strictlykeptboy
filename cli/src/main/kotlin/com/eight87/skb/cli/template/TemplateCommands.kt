package com.eight87.skb.cli.template

import com.eight87.skb.cli.commands.atomicWriteAndCommit
import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.commands.gitOps
import com.eight87.skb.cli.commands.nowIso
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.commands.CommitResult
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.IdentityResolver
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoLayout
import com.eight87.skb.cli.core.RepoStore
import com.eight87.skb.cli.core.TomlTable
import com.eight87.skb.cli.core.Uuid7
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.flag
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Phase AAA / CLI-U — `skb template` command group.
 *
 * Three subcommands:
 *
 *   - `skb template list [--json]` — list every lifestyle template
 *     shipped under `app/src/main/assets/templates/`. AAA introduces 8
 *     new families on top of the AT-D/E/F set.
 *
 *   - `skb template apply <template-id> [--target <calendar-id>] [--at <iso>] [--dry-run]`
 *     Materialize each `[[entry]]` from the template into the target
 *     calendar as a one-off event scheduled at `--at` (default `now`),
 *     stacking entries back-to-back by their `duration_minutes`. Each
 *     event carries `template_origin:cli` + `template_slot:<tid>/<eid>`
 *     tags (Phase AAA convention; see
 *     `:app/.../store/TemplateOrigin.kt`). Idempotent: if an event
 *     under this calendar already carries the matching `template_slot:`
 *     tag, that entry is skipped.
 *
 *   - `skb template reset <template-id> [--target <calendar-id>] [--dry-run]`
 *     Inverse: deletes every event whose tags contain
 *     `template_slot:<template-id>/<entry-id>`. Wizard-scaffolded entries
 *     (origin=`wizard` / `wizard-rerun`) are preserved by default; pass
 *     `--include-wizard` to delete those too.
 *
 * SOLID-S: this file is the CLI wiring for template ops. SOLID-D: it
 * depends on `:cli/.../core/` only — no `:app` import. The template TOML
 * is parsed via a small line-scan (sufficient for the CLI's scaffold
 * needs); rich parsing (sub-beats, kink variants, parameter resolution
 * for HV-B/C) lives in `:app`'s AtomicTemplateLoader for the in-app
 * surfaces.
 */
class TemplateGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "template") {
    init { subcommands(TemplateList(ctxOf), TemplateApply(ctxOf), TemplateReset(ctxOf)) }
    override fun run() = Unit
}

// ─────────────────────────── shared helpers ──────────────────────────

/**
 * Shipped template manifest. Mirrors `:app:src/main/assets/templates/`.
 * Hardcoded so `skb template list` works headless against any user-data
 * repo (which does NOT carry the asset templates — those ship in the
 * APK).
 */
internal val SHIPPED_TEMPLATES: List<String> = listOf(
    "atomic-self-care",
    "atomic-kink-self-care",
    "atomic-workout",
    "atomic-household",
    "atomic-travel-prep",
    "atomic-flight-day",
    "atomic-vacation-daily",
    "atomic-adhd-anchors",
    "atomic-medication",
    "atomic-menstrual-cycle",
    "atomic-leisure",
)

/** Always-scaffolded templates per D.87 (leisure first-class). */
internal val ALWAYS_SCAFFOLDED: Set<String> = setOf("atomic-leisure")

/**
 * Locate a shipped template by id and return the asset bytes. The CLI
 * looks at a small set of relative paths so dev (running gradle from
 * `:cli`) + headless agents (running from repo root or worktree) both
 * resolve. Tests can override via [overrideAssetLoader].
 */
@Volatile
private var assetLoaderOverride: ((String) -> ByteArray?)? = null

internal fun overrideAssetLoader(loader: ((String) -> ByteArray?)?) {
    assetLoaderOverride = loader
}

internal fun loadTemplateAsset(templateId: String): ByteArray? {
    assetLoaderOverride?.let { return it(templateId) }
    val relPath = "templates/$templateId.toml"
    val cwd = Path.of(System.getProperty("user.dir"))
    val candidates = listOf(
        cwd.resolve("app/src/main/assets/$relPath"),
        cwd.resolve("../app/src/main/assets/$relPath"),
        cwd.resolve("../../app/src/main/assets/$relPath"),
    )
    for (c in candidates) {
        if (Files.exists(c)) return Files.readAllBytes(c)
    }
    return null
}

/**
 * Line-scan extractor for the `[[entry]]` blocks the CLI scaffolder
 * needs. Returns (template_id, entries). Reads:
 *   id              (required)
 *   title           (required)
 *   neutral_title   (optional)
 *   duration_minutes (required)
 *   privacy_flag    (optional, default false)
 *   tags            (optional, string array)
 */
internal data class TemplateEntryLite(
    val id: String,
    val title: String,
    val neutralTitle: String?,
    val durationMinutes: Int,
    val privacyFlag: Boolean,
    val tags: List<String>,
)

internal fun parseTemplateEntries(text: String): Pair<String, List<TemplateEntryLite>> {
    val lines = text.lines()
    var templateId: String? = null
    val out = mutableListOf<TemplateEntryLite>()

    var inEntry = false
    var inSubbeat = false
    var id: String? = null
    var title: String? = null
    var neutralTitle: String? = null
    var durationMinutes: Int? = null
    var privacyFlag = false
    var tags: List<String> = emptyList()

    fun flush() {
        if (id != null && title != null && durationMinutes != null) {
            out += TemplateEntryLite(
                id = id!!, title = title!!, neutralTitle = neutralTitle,
                durationMinutes = durationMinutes!!, privacyFlag = privacyFlag,
                tags = tags,
            )
        }
        id = null; title = null; neutralTitle = null; durationMinutes = null
        privacyFlag = false; tags = emptyList()
    }

    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        if (line.startsWith("[[entry.subbeat]]")) { inSubbeat = true; continue }
        if (line.startsWith("[[entry]]")) {
            if (inEntry) flush()
            inEntry = true; inSubbeat = false
            continue
        }
        if (line.startsWith("[[") || line.startsWith("[")) {
            if (inEntry) flush()
            inEntry = false; inSubbeat = false
            continue
        }
        if (inSubbeat) continue
        val eq = line.indexOf('=')
        if (eq < 0) continue
        val key = line.substring(0, eq).trim()
        val rhs = line.substring(eq + 1).trim()
        if (!inEntry) {
            if (key == "template_id") templateId = unquote(rhs)
            continue
        }
        when (key) {
            "id" -> id = unquote(rhs)
            "title" -> title = unquote(rhs)
            "neutral_title" -> neutralTitle = unquote(rhs)
            "duration_minutes" -> durationMinutes = rhs.trim().toIntOrNull()
            "privacy_flag" -> privacyFlag = rhs.trim() == "true"
            "tags" -> tags = parseStringArray(rhs)
        }
    }
    if (inEntry) flush()
    return (templateId ?: "") to out
}

private fun unquote(s: String): String {
    val t = s.trim()
    if (t.length >= 2 && t.first() == '"' && t.last() == '"') {
        return t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
    }
    return t
}

private fun parseStringArray(rhs: String): List<String> {
    val t = rhs.trim()
    if (!t.startsWith("[") || !t.endsWith("]")) return emptyList()
    val inner = t.substring(1, t.length - 1)
    if (inner.isBlank()) return emptyList()
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var inQuotes = false
    for (c in inner) {
        when {
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> {
                val v = buf.toString().trim().trim('"')
                if (v.isNotEmpty()) out += v
                buf.clear()
            }
            else -> buf.append(c)
        }
    }
    val last = buf.toString().trim().trim('"')
    if (last.isNotEmpty()) out += last
    return out
}

// ─────────────────────────── list ────────────────────────────────────

private class TemplateList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
    override fun run() {
        val ctx = ctxOf()
        val rows = SHIPPED_TEMPLATES.map { tid ->
            val bytes = loadTemplateAsset(tid)
            val entries = bytes?.let {
                parseTemplateEntries(String(it, StandardCharsets.UTF_8)).second
            } ?: emptyList()
            Triple(tid, entries.size, tid in ALWAYS_SCAFFOLDED)
        }
        if (ctx.json) {
            val arr = JsonArray(rows.map { (id, count, always) ->
                buildJsonObject {
                    put("template_id", JsonPrimitive(id))
                    put("entry_count", JsonPrimitive(count))
                    put("always_scaffolded", JsonPrimitive(always))
                }
            })
            emitJson(
                JsonEnvelope.success("template list", buildJsonObject { put("templates", arr) }),
                ctx,
            )
        } else {
            for ((id, count, always) in rows) {
                val tag = if (always) " [always-scaffolded]" else ""
                emitHuman("$id  ($count entries)$tag", ctx)
            }
        }
    }
}

// ─────────────────────────── apply ───────────────────────────────────

private class TemplateApply(val ctxOf: () -> CliContext) : CliktCommand(name = "apply") {
    val templateArg by argument("template-id")
    val target by option("--target", help = "target calendar id or name (default: first calendar)")
    val atIso by option("--at", help = "ISO start-time of the first entry (default: now)")

    override fun run() {
        val ctx = ctxOf()
        val bytes = loadTemplateAsset(templateArg)
            ?: throw CliError(ExitCode.USAGE, "unknown template: $templateArg")
        val (parsedId, entries) = parseTemplateEntries(String(bytes, StandardCharsets.UTF_8))
        val templateId = parsedId.ifBlank { templateArg }

        val root = ctx.repoRoot()
        val store = RepoStore(root)
        val cals = store.listCalendars()
        if (cals.isEmpty()) throw CliError(ExitCode.USAGE, "repo has no calendars")
        val targetCal = if (target != null) store.resolveCalendar(target!!) else cals.first()

        val existingSlots: Set<String> = collectExistingTemplateSlots(root, targetCal.id)

        val startBase = atIso?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC).withNano(0)

        val written = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var cursor = startBase
        val firstSha: String? = null
        for (entry in entries) {
            val slot = "template_slot:$templateId/${entry.id}"
            val start = cursor
            cursor = cursor.plusMinutes(entry.durationMinutes.toLong())
            if (slot in existingSlots) { skipped += entry.id; continue }
            written += entry.id
            if (ctx.dryRun) continue
            writeEventFile(root, ctx, targetCal.id, templateId, entry, start)
        }
        if (ctx.json) {
            emitJson(
                JsonEnvelope.success(
                    "template apply",
                    buildJsonObject {
                        put("template_id", JsonPrimitive(templateId))
                        put("target_calendar", JsonPrimitive(targetCal.id))
                        put("written", JsonArray(written.map { JsonPrimitive(it) }))
                        put("skipped", JsonArray(skipped.map { JsonPrimitive(it) }))
                        put("dry_run", JsonPrimitive(ctx.dryRun))
                    },
                ),
                ctx,
            )
        } else {
            emitHuman("template $templateId → ${targetCal.id}", ctx)
            emitHuman("  ${written.size} written, ${skipped.size} skipped (already-present)", ctx)
        }
    }
}

// ─────────────────────────── reset ───────────────────────────────────

private class TemplateReset(val ctxOf: () -> CliContext) : CliktCommand(name = "reset") {
    val templateArg by argument("template-id")
    val target by option("--target", help = "target calendar id or name (default: first calendar)")
    val includeWizard by option("--include-wizard", help = "also delete wizard-scaffolded entries").flag()

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()
        val store = RepoStore(root)
        val cals = store.listCalendars()
        if (cals.isEmpty()) throw CliError(ExitCode.USAGE, "repo has no calendars")
        val targetCal = if (target != null) store.resolveCalendar(target!!) else cals.first()
        val eventsDir = RepoLayout.calendarDir(root, targetCal.id).resolve("events")

        val deleted = mutableListOf<String>()
        if (Files.exists(eventsDir)) {
            Files.walk(eventsDir).use { stream ->
                for (p in stream.filter { it.toString().endsWith(".md") }) {
                    val text = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                    val (fm, _) = Frontmatter.parse(text)
                    val tags = fm.getStringArray("tags") ?: emptyList()
                    val slotPrefix = "template_slot:$templateArg/"
                    val matches = tags.any { it.startsWith(slotPrefix) }
                    if (!matches) continue
                    if (!includeWizard) {
                        if (tags.any {
                                it == "template_origin:wizard" ||
                                it == "template_origin:wizard-rerun"
                            }) continue
                    }
                    val rel = root.relativize(p).toString()
                    if (!ctx.dryRun) Files.delete(p)
                    deleted += rel
                }
            }
            if (!ctx.dryRun && deleted.isNotEmpty()) {
                val identity = IdentityResolver.resolve(root)
                gitOps().addAndCommit(
                    repoRoot = root,
                    files = deleted,
                    message = "template reset $templateArg → ${targetCal.id} (${deleted.size} entries)",
                    authorName = identity.displayName,
                    authorEmail = identity.email,
                )
            }
        }
        if (ctx.json) {
            emitJson(
                JsonEnvelope.success(
                    "template reset",
                    buildJsonObject {
                        put("template_id", JsonPrimitive(templateArg))
                        put("target_calendar", JsonPrimitive(targetCal.id))
                        put("deleted", JsonArray(deleted.map { JsonPrimitive(it) }))
                        put("dry_run", JsonPrimitive(ctx.dryRun))
                    },
                ),
                ctx,
            )
        } else {
            emitHuman("template reset $templateArg → ${targetCal.id}", ctx)
            emitHuman("  ${deleted.size} entries deleted", ctx)
        }
    }
}

// ─────────────────────────── internal io ─────────────────────────────

private fun collectExistingTemplateSlots(root: Path, calendarId: String): Set<String> {
    val eventsDir = RepoLayout.calendarDir(root, calendarId).resolve("events")
    if (!Files.exists(eventsDir)) return emptySet()
    val out = mutableSetOf<String>()
    Files.walk(eventsDir).use { stream ->
        for (p in stream.filter { it.toString().endsWith(".md") }) {
            val text = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
            val (fm, _) = Frontmatter.parse(text)
            val tags = fm.getStringArray("tags") ?: emptyList()
            for (tag in tags) if (tag.startsWith("template_slot:")) out += tag
        }
    }
    return out
}

private fun writeEventFile(
    root: Path,
    ctx: CliContext,
    calendarId: String,
    templateId: String,
    entry: TemplateEntryLite,
    start: OffsetDateTime,
) {
    val id = Uuid7.generate().toString()
    val ts = nowIso()
    val startIso = start.toString()
    val tagsList = buildList<String> {
        add("template")
        add("template_origin:cli")
        add("template_slot:$templateId/${entry.id}")
        addAll(entry.tags)
    }
    val table = TomlTable().apply {
        putInt("schema_version", 1)
        putString("id", id)
        putString("kind", "event")
        putString("title", entry.title)
        if (entry.neutralTitle != null) putString("neutral_title", entry.neutralTitle)
        putString("start", startIso)
        putString("duration", "PT${entry.durationMinutes}M")
        putString("calendar_id", calendarId)
        putString("created_at", ts)
        putString("updated_at", ts)
        if (entry.privacyFlag) putBool("private", true)
        putStringArray("tags", tagsList)
    }
    val target = RepoLayout.event(root, calendarId, id, startIso)
    val res = atomicWriteAndCommit(
        ctx = ctx,
        root = root,
        target = target,
        table = table,
        body = "",
        commitMessage = "template apply $templateId/${entry.id} → $calendarId",
    )
    // CommitResult variants handled centrally; nothing to do here.
    @Suppress("UNUSED_VARIABLE") val _r: CommitResult = res
}
