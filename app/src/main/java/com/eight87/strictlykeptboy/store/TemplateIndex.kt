package com.eight87.strictlykeptboy.store

import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Phase FFF / EC-C.2 — template-picker index types + parsers.
 *
 * Three sources feed the picker per EC-C.2:
 *  - **SHIPPED** — entries listed in `assets/templates/index.toml`.
 *  - **USER**    — toml files under `templates/` in the active repo (Phase EC-D save-as-template).
 *  - **PACK**    — entries from any active custom template-pack (Phase S.7).
 *
 * Pure (SOLID-D): no Android imports.
 */
sealed class TemplateSource {
    object Shipped : TemplateSource()
    object User : TemplateSource()
    data class Pack(val packId: String) : TemplateSource()
}

data class TemplateEntry(
    val templateId: String,
    val displayName: String,
    val category: String,
    val tags: List<String>,
    val aliases: List<String>,
    val neutralSafe: Boolean,
    val source: TemplateSource,
    val assetFile: String? = null,
)

object TemplateIndexLoader {
    fun load(stream: InputStream): List<TemplateEntry> =
        stream.use { parse(String(it.readBytes(), StandardCharsets.UTF_8)) }

    fun parse(text: String): List<TemplateEntry> {
        val table = TomlReader.parse(text)
        return table.aotables["template"]?.map { parseEntry(it) } ?: emptyList()
    }

    private fun parseEntry(t: TomlTable): TemplateEntry = TemplateEntry(
        templateId = t.getString("template_id") ?: error("template missing template_id"),
        displayName = t.getString("display_name") ?: error("template missing display_name"),
        category = t.getString("category") ?: "uncategorized",
        tags = t.getStringArray("tags") ?: emptyList(),
        aliases = t.getStringArray("aliases") ?: emptyList(),
        neutralSafe = t.getBool("neutral_safe") ?: false,
        source = TemplateSource.Shipped,
        assetFile = t.getString("asset_file"),
    )
}

object TemplateMerger {
    /**
     * Merge SHIPPED + USER + PACK; resolve duplicates by id with
     * USER > PACK > SHIPPED priority.
     */
    fun merge(
        shipped: List<TemplateEntry>,
        user: List<TemplateEntry>,
        packs: List<TemplateEntry>,
    ): List<TemplateEntry> {
        val byId = LinkedHashMap<String, TemplateEntry>()
        for (e in shipped) byId[e.templateId] = e
        for (e in packs) byId[e.templateId] = e
        for (e in user.sortedBy { it.displayName.lowercase() }) byId[e.templateId] = e
        return byId.values.toList()
    }

    /** Filter by query + neutral-mode (D.58: hide `kink` tag). */
    fun filter(entries: List<TemplateEntry>, query: String, neutralMode: Boolean): List<TemplateEntry> {
        val q = query.trim().lowercase()
        return entries.filter { e ->
            if (neutralMode && e.tags.any { it.equals("kink", ignoreCase = true) }) return@filter false
            if (q.isEmpty()) return@filter true
            e.displayName.lowercase().contains(q) ||
                e.tags.any { it.lowercase().contains(q) } ||
                e.aliases.any { it.lowercase().contains(q) }
        }
    }

    fun group(entries: List<TemplateEntry>): List<TemplateSection> {
        val selfCare = entries.filter { it.category == "self-care" && it.source == TemplateSource.Shipped }
        val workout = entries.filter { it.category == "workout" && it.source == TemplateSource.Shipped }
        val routine = entries.filter { it.category == "routine" && it.source == TemplateSource.Shipped }
        val kink = entries.filter { it.category == "kink" && it.source == TemplateSource.Shipped }
        val user = entries.filter { it.source == TemplateSource.User }
        val packGroups = entries
            .mapNotNull { e -> (e.source as? TemplateSource.Pack)?.let { it.packId to e } }
            .groupBy({ it.first }, { it.second })
        val sections = mutableListOf<TemplateSection>()
        if (selfCare.isNotEmpty()) sections += TemplateSection(SectionKind.SelfCare, null, selfCare)
        if (workout.isNotEmpty()) sections += TemplateSection(SectionKind.Workout, null, workout)
        if (routine.isNotEmpty()) sections += TemplateSection(SectionKind.Routine, null, routine)
        if (kink.isNotEmpty()) sections += TemplateSection(SectionKind.Kink, null, kink)
        if (user.isNotEmpty()) sections += TemplateSection(SectionKind.User, null, user)
        for ((packId, list) in packGroups) {
            sections += TemplateSection(SectionKind.Pack, packId, list)
        }
        return sections
    }
}

enum class SectionKind { SelfCare, Workout, Routine, Kink, User, Pack }

data class TemplateSection(
    val kind: SectionKind,
    val packId: String?,
    val entries: List<TemplateEntry>,
)
