package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase DDD.7 / DM-Y.2..DM-Y.5 — `identity.toml` codec.
 *
 * Per D.83: praise term, pronouns, honorific-for-dom, tone register +
 * emoji density live in this single committed file at the calendar-repo
 * root. AGENTS.md carries exactly one bridge line (HV-R.4 / DM-Y.4); the
 * actual content is here.
 *
 * SOLID:
 *  - **S:** Only round-trips identity.toml — no UI / no Git wiring.
 *  - **D:** Pure mapping over [TomlTable] / [Path]; consumers compose
 *    with their own I/O and commit machinery.
 */
data class IdentityTomlData(
    val schemaVersion: Int = 1,
    val praiseTerm: String = "good boy",
    val altTerms: List<String> = emptyList(),
    val pronouns: IdentityPronouns = IdentityPronouns.HeHim,
    val pronounsExtra: List<IdentityPronouns> = emptyList(),
    val honorificForDom: String = "Sir",
    val toneRegister: String = "soft-kinky",
    val emojiDensity: String = "medium",
    /**
     * Phase 2.1.J.2 — wizard-driven alignment id (e.g. `submissive`,
     * `dominant`, `switch`, `unaligned-private`). `null` when omitted
     * from disk (LockedDefaults emits no `[alignment]` section).
     */
    val alignment: String? = null,
    /**
     * Phase 2.1.J.2 — wizard-driven lifestyle id (e.g. `single-strict`,
     * `partnered-free`). `null` when omitted from disk.
     */
    val lifestyle: String? = null,
) {
    init {
        require(praiseTerm.isNotBlank()) { "praise.term must not be blank (DM-Y.5)" }
        require(pronouns.allFilled()) { "pronouns must have subject/object/possessive/reflexive (DM-Y.5)" }
    }

    companion object {
        /** HV-R.1.3 / DM-Y.3 locked defaults. */
        val LockedDefaults: IdentityTomlData = IdentityTomlData()

        const val FILE_NAME: String = "identity.toml"
    }
}

data class IdentityPronouns(
    val subject: String,
    val obj: String,
    val possessive: String,
    val reflexive: String,
) {
    fun allFilled(): Boolean = subject.isNotBlank() && obj.isNotBlank() &&
        possessive.isNotBlank() && reflexive.isNotBlank()

    companion object {
        val HeHim = IdentityPronouns("he", "him", "his", "himself")
        val SheHer = IdentityPronouns("she", "her", "hers", "herself")
        val TheyThem = IdentityPronouns("they", "them", "theirs", "themself")
    }
}

object IdentityTomlCodec {
    fun toToml(data: IdentityTomlData): String {
        val t = TomlTable().apply {
            putInt("schema_version", data.schemaVersion)
            val praise = TomlTable().apply {
                putString("term", data.praiseTerm)
                if (data.altTerms.isNotEmpty()) putStringArray("alt_terms", data.altTerms)
            }
            sections["praise"] = praise
            val pron = TomlTable().apply {
                putString("subject", data.pronouns.subject)
                putString("object", data.pronouns.obj)
                putString("possessive", data.pronouns.possessive)
                putString("reflexive", data.pronouns.reflexive)
            }
            if (data.pronounsExtra.isNotEmpty()) {
                val list = pron.aotables.getOrPut("extra_sets") { mutableListOf() }
                data.pronounsExtra.forEach { extra ->
                    list += TomlTable().apply {
                        putString("subject", extra.subject)
                        putString("object", extra.obj)
                        putString("possessive", extra.possessive)
                        putString("reflexive", extra.reflexive)
                    }
                }
            }
            sections["pronouns"] = pron
            val hon = TomlTable().apply { putString("term", data.honorificForDom) }
            sections["honorific_for_dom"] = hon
            val tone = TomlTable().apply {
                putString("register", data.toneRegister)
                putString("emoji_density", data.emojiDensity)
            }
            sections["tone"] = tone
            // Phase 2.1.J.2 — wizard fields. Omit sections entirely when
            // null so LockedDefaults still round-trips byte-stably.
            if (data.alignment != null) {
                sections["alignment"] = TomlTable().apply { putString("value", data.alignment) }
            }
            if (data.lifestyle != null) {
                sections["lifestyle"] = TomlTable().apply { putString("value", data.lifestyle) }
            }
        }
        return TomlWriter.emit(t)
    }

    fun fromToml(input: String): IdentityTomlData {
        val table = TomlReader.parse(input)
        val schema = table.getInt("schema_version") ?: 1
        val praise = table.sections["praise"]
            ?: error("identity.toml missing [praise] (DM-Y.5)")
        val term = praise.getString("term")
            ?: error("identity.toml missing praise.term (DM-Y.5)")
        require(term.isNotBlank()) { "praise.term must not be blank (DM-Y.5)" }
        // Canonical key is `praise.alt_terms = [...]`. Back-compat: pre-2.1.J
        // wizard appendices wrote `[praise.alternates] terms = [...]` — accept
        // either when reading so existing on-disk repos still parse.
        val altTerms = praise.getStringArray("alt_terms")
            ?: praise.sections["alternates"]?.getStringArray("terms")
            ?: emptyList()

        val pron = table.sections["pronouns"]
            ?: error("identity.toml missing [pronouns] (DM-Y.5)")
        val pronouns = IdentityPronouns(
            subject = pron.getString("subject") ?: error("pronouns.subject missing"),
            obj = pron.getString("object") ?: error("pronouns.object missing"),
            possessive = pron.getString("possessive") ?: error("pronouns.possessive missing"),
            reflexive = pron.getString("reflexive") ?: error("pronouns.reflexive missing"),
        )
        val extras = pron.aotables["extra_sets"]?.map { sub ->
            IdentityPronouns(
                subject = sub.getString("subject") ?: "",
                obj = sub.getString("object") ?: "",
                possessive = sub.getString("possessive") ?: "",
                reflexive = sub.getString("reflexive") ?: "",
            )
        } ?: emptyList()

        // Honorific: canonical section is `[honorific_for_dom]`. Back-compat
        // also accepts the bare `[honorific]` section that pre-2.1.J wizard
        // appendices wrote (DDD.11 / Phase 2.1.J.2).
        val hon = table.sections["honorific_for_dom"]?.getString("term")
            ?: table.sections["honorific"]?.getString("term")
            ?: "Sir"
        val toneSection = table.sections["tone"]
        val toneReg = toneSection?.getString("register") ?: "soft-kinky"
        // emoji_density: canonical key on `[tone]`; back-compat accepts
        // the bare `[emoji] density = ...` shape from the old appendix.
        val emoji = toneSection?.getString("emoji_density")
            ?: table.sections["emoji"]?.getString("density")
            ?: "medium"

        val alignment = table.sections["alignment"]?.getString("value")
        val lifestyle = table.sections["lifestyle"]?.getString("value")

        return IdentityTomlData(
            schemaVersion = schema,
            praiseTerm = term,
            altTerms = altTerms,
            pronouns = pronouns,
            pronounsExtra = extras,
            honorificForDom = hon,
            toneRegister = toneReg,
            emojiDensity = emoji,
            alignment = alignment,
            lifestyle = lifestyle,
        )
    }

    fun write(repoRoot: Path, data: IdentityTomlData): Path {
        val target = repoRoot.resolve(IdentityTomlData.FILE_NAME)
        Files.write(target, toToml(data).toByteArray(StandardCharsets.UTF_8))
        return target
    }

    fun readOrDefault(repoRoot: Path): IdentityTomlData {
        val target = repoRoot.resolve(IdentityTomlData.FILE_NAME)
        if (!Files.isRegularFile(target)) return IdentityTomlData.LockedDefaults
        return fromToml(String(Files.readAllBytes(target), StandardCharsets.UTF_8))
    }
}
