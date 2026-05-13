package com.eight87.strictlykeptboy.store

import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Phase XX.4 / AT-D — atomic-activity template parser. Phase AAA extends
 * it with the lifestyle templates surface (household / travel-prep /
 * flight-day / vacation-daily / ADHD anchors / medication / menstrual-
 * cycle / leisure) per `draft-household-travel-vacation.md` HV-A..HV-P.
 *
 * Reads `templates/<id>.toml` (shipped as an asset) into a typed
 * [AtomicTemplate]. The wizard (XX.7+) walks the parsed entries; the CLI
 * `skb routine start` / `skb template apply` (Phase AAA) materializes
 * them into the target calendar.
 *
 * Pure parser (SOLID-S, SOLID-D): no Android imports; callers hand a
 * stream — for example
 *   `context.assets.open("templates/atomic-self-care.toml")` — and the
 * loader returns the typed shape. Round-trip-able via [TomlReader] /
 * [TomlWriter].
 *
 * Schema additions in Phase AAA (all OPTIONAL, default-null/empty):
 *  - `neutral_title` per HV-A.1: shown in unaligned-private / neutral
 *    mode in place of the kink-coded `title`.
 *  - `lead_offset_days` per HV-B: travel-prep entries are back-filled
 *    from `trip.start_date - lead_offset_days`.
 *  - `offset_minutes_from_departure` / `offset_minutes_from_arrival`
 *    per HV-C: flight-day entries are timestamped relative to the
 *    parameterized flight times.
 *  - `privacy_flag` per HV-M / K-2: per-entry default for events that
 *    must NEVER surface on lockscreen.
 *  - `default_weekday` per HV-A: weekday string for weekly cadences.
 *  - `parameterized` (top-level) per HV-B.1 / HV-C.1: signals the wizard
 *    MUST resolve parameters before materialization.
 *  - `[[variant]]` (top-level array) per HV-A.17: additive kink-coded
 *    title overrides keyed by `kink_variant_of`.
 *
 * Forward-compat: unknown fields are silently ignored.
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
            parameterized = table.getBool("parameterized") ?: false,
            entries = table.aotables["entry"]
                ?.map { parseEntry(it) }
                ?: emptyList(),
            variants = table.aotables["variant"]
                ?.map { parseVariant(it) }
                ?: emptyList(),
        )
    }

    private fun parseEntry(t: TomlTable): AtomicTemplateEntry {
        return AtomicTemplateEntry(
            id = t.getString("id") ?: error("entry missing id"),
            title = t.getString("title") ?: error("entry missing title"),
            neutralTitle = t.getString("neutral_title"),
            durationMinutes = t.getInt("duration_minutes") ?: error("entry missing duration_minutes"),
            stickerId = t.getString("sticker_id"),
            category = t.getString("category"),
            defaultCadence = t.getString("default_cadence"),
            defaultTimes = t.getStringArray("default_times") ?: emptyList(),
            defaultWeekday = t.getString("default_weekday"),
            tags = t.getStringArray("tags") ?: emptyList(),
            sets = t.getInt("sets"),
            reps = t.getInt("reps"),
            holdSeconds = t.getInt("hold_seconds"),
            leadOffsetDays = t.getInt("lead_offset_days"),
            offsetMinutesFromDeparture = t.getInt("offset_minutes_from_departure"),
            offsetMinutesFromArrival = t.getInt("offset_minutes_from_arrival"),
            privacyFlag = t.getBool("privacy_flag") ?: false,
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

    private fun parseVariant(t: TomlTable): AtomicTemplateVariant = AtomicTemplateVariant(
        kinkVariantOf = t.getString("kink_variant_of") ?: error("variant missing kink_variant_of"),
        title = t.getString("title") ?: error("variant missing title"),
        tags = t.getStringArray("tags") ?: emptyList(),
    )
}

data class AtomicTemplate(
    val schemaVersion: Int,
    val templateId: String,
    val displayName: String,
    val category: String,
    val neutralSafe: Boolean,
    val parameterized: Boolean = false,
    val entries: List<AtomicTemplateEntry>,
    val variants: List<AtomicTemplateVariant> = emptyList(),
) {
    /** Lookup by entry id; null if absent. */
    fun entry(id: String): AtomicTemplateEntry? = entries.firstOrNull { it.id == id }

    /** Variant override for an entry, if one exists. K-mode renderer uses this. */
    fun variantFor(entryId: String): AtomicTemplateVariant? =
        variants.firstOrNull { it.kinkVariantOf == entryId }
}

data class AtomicTemplateEntry(
    val id: String,
    val title: String,
    val neutralTitle: String? = null,
    val durationMinutes: Int,
    val stickerId: String? = null,
    val category: String? = null,
    val defaultCadence: String? = null,
    val defaultTimes: List<String> = emptyList(),
    val defaultWeekday: String? = null,
    val tags: List<String> = emptyList(),
    val sets: Int? = null,
    val reps: Int? = null,
    val holdSeconds: Int? = null,
    val leadOffsetDays: Int? = null,
    val offsetMinutesFromDeparture: Int? = null,
    val offsetMinutesFromArrival: Int? = null,
    val privacyFlag: Boolean = false,
    val subbeats: List<AtomicTemplateSubbeat> = emptyList(),
) {
    /** AT-D.4 invariant: sub-beat sum ≤ duration_minutes * 60. */
    fun subbeatTotalSeconds(): Int = subbeats.sumOf { it.durationSeconds }
    fun subbeatTotalFitsEnvelope(): Boolean =
        subbeats.isEmpty() || subbeatTotalSeconds() <= durationMinutes * 60

    /** HV-A.17: render the kink-coded title when K-mode + variant present;
     *  otherwise the base `title`; in neutral mode prefer `neutralTitle`. */
    fun renderTitle(neutralMode: Boolean, variant: AtomicTemplateVariant? = null): String {
        if (neutralMode) return neutralTitle ?: title
        return variant?.title ?: title
    }

    /** D.76: `nonSuperseable` tag = vacation overlay cannot pause this entry. */
    val nonSuperseable: Boolean get() = "nonSuperseable" in tags
}

data class AtomicTemplateSubbeat(
    val label: String,
    val durationSeconds: Int,
    val stickerId: String? = null,
)

data class AtomicTemplateVariant(
    val kinkVariantOf: String,
    val title: String,
    val tags: List<String> = emptyList(),
)
