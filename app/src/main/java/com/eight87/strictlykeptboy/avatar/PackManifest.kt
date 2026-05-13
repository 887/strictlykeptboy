package com.eight87.strictlykeptboy.avatar

import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlValue

/**
 * Phase WW.1 — parse `pack.toml` into a typed [StickerPack] (D.65).
 *
 * Schema (subset of D.65 — all fields optional except `species` and at
 * least one `[[sticker]]` entry):
 *
 * ```toml
 * schema_version = 1
 * name = "Default Bat"
 * species = "bat"
 * author = "eight87"
 * license = "Apache-2.0"
 * style = "flat"
 *
 * [[sticker]]
 * activity_id = "idle"
 * file = "idle.webp"
 * tags = ["idle", "neutral"]
 * animated = false
 * ```
 *
 * Pure function over a TOML string + caller-supplied [packId]. No
 * Android dependencies; can be unit-tested under plain JUnit.
 */
object PackManifest {

    /** Parse a manifest TOML string, throwing [PackManifestException] on schema error. */
    @Throws(PackManifestException::class)
    fun parse(toml: String, packId: String): StickerPack {
        val root: TomlTable = try {
            TomlReader.parse(toml)
        } catch (t: Throwable) {
            throw PackManifestException("invalid TOML: ${t.message}", cause = t)
        }

        val species = root.getString("species")
            ?: throw PackManifestException("missing `species`")
        val name = root.getString("name") ?: "Pack $species"
        val author = root.getString("author")
        val license = root.getString("license")
        val style = root.getString("style")
        val schemaVersion = root.getInt("schema_version") ?: 1

        val entries = root.aotables["sticker"].orEmpty()
        if (entries.isEmpty()) {
            throw PackManifestException("at least one [[sticker]] entry is required")
        }

        val stickers = LinkedHashMap<String, StickerEntry>(entries.size)
        for ((index, t) in entries.withIndex()) {
            val activityId = t.getString("activity_id")
                ?: throw PackManifestException("[[sticker]] $index missing `activity_id`")
            val file = t.getString("file")
                ?: throw PackManifestException("[[sticker]] $index ($activityId) missing `file`")
            val animated = t.getBool("animated") ?: false
            val tagStrings: List<String> = (t.scalars["tags"] as? TomlValue.Arr)
                ?.items
                ?.mapNotNull { (it as? TomlValue.Str)?.value }
                ?: emptyList()
            val tags: Set<StickerTag> = tagStrings.mapTo(LinkedHashSet()) { StickerTag.of(it) }
            stickers[activityId] = StickerEntry(
                activityId = activityId,
                file = file,
                tags = tags,
                animated = animated,
            )
        }

        return StickerPack(
            packId = packId,
            name = name,
            species = species.lowercase(),
            author = author,
            license = license,
            style = style,
            stickers = stickers,
            schemaVersion = schemaVersion,
        )
    }
}

class PackManifestException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
