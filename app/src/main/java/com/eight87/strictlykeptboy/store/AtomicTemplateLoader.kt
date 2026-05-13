package com.eight87.strictlykeptboy.store

import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Phase XX.4 / AT-D — atomic-activity template parser.
 *
 * Reads `templates/<id>.toml` (shipped as an asset; see
 * `app/src/main/assets/templates/atomic-self-care.toml`) into a typed
 * [AtomicTemplate]. The wizard (XX.7+) walks the parsed entries; the CLI
 * `skb routine start` (XX.8) materializes them into the target calendar.
 *
 * Pure parser (SOLID-S, SOLID-D): no Android imports; callers hand a
 * stream — for example
 *   `context.assets.open("templates/atomic-self-care.toml")` — and the
 * loader returns the typed shape. Round-trip-able via [TomlReader] /
 * [TomlWriter].
 */
object AtomicTemplateLoader {

    fun load(stream: InputStream): AtomicTemplate =
        stream.use { parse(String(it.readBytes(), StandardCharsets.UTF_8)) }

    fun parse(text: String): AtomicTemplate {
        val table = TomlReader.parse(text)
        return AtomicTemplate(
            schemaVersion = table.getInt("schema_version") ?: 1,
            templateId = table.getString("template_id") ?: error("missing template_id"),
            displayName = table.getString("display_name") ?: error("missing display_name"),
            category = table.getString("category") ?: "uncategorized",
            neutralSafe = table.getBool("neutral_safe") ?: false,
            entries = table.aotables["entry"]
                ?.map { parseEntry(it) }
                ?: emptyList(),
        )
    }

    private fun parseEntry(t: TomlTable): AtomicTemplateEntry {
        return AtomicTemplateEntry(
            id = t.getString("id") ?: error("entry missing id"),
            title = t.getString("title") ?: error("entry missing title"),
            durationMinutes = t.getInt("duration_minutes") ?: error("entry missing duration_minutes"),
            stickerId = t.getString("sticker_id"),
            category = t.getString("category"),
            defaultCadence = t.getString("default_cadence"),
            defaultTimes = t.getStringArray("default_times") ?: emptyList(),
            tags = t.getStringArray("tags") ?: emptyList(),
            sets = t.getInt("sets"),
            reps = t.getInt("reps"),
            holdSeconds = t.getInt("hold_seconds"),
            subbeats = t.aotables["subbeat"]
                ?.map { parseSubbeat(it) }
                ?: emptyList(),
        )
    }

    private fun parseSubbeat(t: TomlTable): AtomicTemplateSubbeat = AtomicTemplateSubbeat(
        label = t.getString("label") ?: error("subbeat missing label"),
        durationSeconds = t.getInt("duration_seconds") ?: error("subbeat missing duration_seconds"),
        stickerId = t.getString("sticker_id"),
    )
}

data class AtomicTemplate(
    val schemaVersion: Int,
    val templateId: String,
    val displayName: String,
    val category: String,
    val neutralSafe: Boolean,
    val entries: List<AtomicTemplateEntry>,
)

data class AtomicTemplateEntry(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val stickerId: String? = null,
    val category: String? = null,
    val defaultCadence: String? = null,
    val defaultTimes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val sets: Int? = null,
    val reps: Int? = null,
    val holdSeconds: Int? = null,
    val subbeats: List<AtomicTemplateSubbeat> = emptyList(),
) {
    /** AT-D.4 invariant: sub-beat sum ≤ duration_minutes * 60. */
    fun subbeatTotalSeconds(): Int = subbeats.sumOf { it.durationSeconds }
    fun subbeatTotalFitsEnvelope(): Boolean =
        subbeats.isEmpty() || subbeatTotalSeconds() <= durationMinutes * 60
}

data class AtomicTemplateSubbeat(
    val label: String,
    val durationSeconds: Int,
    val stickerId: String? = null,
)
