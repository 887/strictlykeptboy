package com.eight87.strictlykeptboy.avatar

/**
 * Phase WW.1 — sticker pack data model.
 *
 * A pack groups stickers for a single species (or a custom user pack).
 * Bundled default packs live under `app/src/main/assets/avatar-packs/<species>/`
 * inside the APK; user packs are cloned into
 * `<app-private>/avatar-packs/<pack-id>/`.
 *
 * Tag taxonomy is locked at the values in [StickerTag] per D.65 / WW.1.
 * Unknown tags pass through as [StickerTag.Custom] and render unless
 * filtered. The build-time validator (WW.7 — deferred) asserts that every
 * default pack contains at least an `idle` activity.
 */
data class StickerPack(
    /** Stable pack identifier (default packs: `default-<species>`; user packs: 16-hex sha-256 of clone URL). */
    val packId: String,
    /** Human-readable pack name from `pack.toml`. */
    val name: String,
    /** Species this pack covers (e.g. `bat`, `fox`). One of the default-roster ids per D.64. */
    val species: String,
    /** Optional author string. */
    val author: String? = null,
    /** Optional SPDX license id. */
    val license: String? = null,
    /** Optional style descriptor (`flat`, `pastel`, `pixel`, ...). */
    val style: String? = null,
    /** Sticker entries indexed by `activity_id`. */
    val stickers: Map<String, StickerEntry>,
    /** Schema version of the loaded manifest; defaults to 1. */
    val schemaVersion: Int = 1,
)

/**
 * Single sticker entry inside a [StickerPack].
 *
 * `file` is a relative path within the pack root (e.g. `pushup.webp`).
 * The pack loader resolves the absolute asset / file URI when handing
 * the entry to the renderer.
 */
data class StickerEntry(
    val activityId: String,
    val file: String,
    val tags: Set<StickerTag> = emptySet(),
    val animated: Boolean = false,
)

/**
 * Locked taxonomy from D.65. Custom values pass through but are
 * surfaced as [Custom] so callers can decide whether to filter them.
 */
sealed interface StickerTag {
    val raw: String

    data object Neutral : StickerTag { override val raw = "neutral" }
    data object Kink : StickerTag { override val raw = "kink" }
    data object Hygiene : StickerTag { override val raw = "hygiene" }
    data object Workout : StickerTag { override val raw = "workout" }
    data object Meal : StickerTag { override val raw = "meal" }
    data object Work : StickerTag { override val raw = "work" }
    data object Study : StickerTag { override val raw = "study" }
    data object Posture : StickerTag { override val raw = "posture" }
    data object Rest : StickerTag { override val raw = "rest" }
    data object Idle : StickerTag { override val raw = "idle" }
    data class Custom(override val raw: String) : StickerTag

    companion object {
        /** Parse a single tag token from `pack.toml`. */
        fun of(raw: String): StickerTag = when (raw.trim().lowercase()) {
            "neutral" -> Neutral
            "kink" -> Kink
            "hygiene" -> Hygiene
            "workout" -> Workout
            "meal" -> Meal
            "work" -> Work
            "study" -> Study
            "posture" -> Posture
            "rest" -> Rest
            "idle" -> Idle
            else -> Custom(raw.trim())
        }
    }
}

/** Required activity id for the species-idle fallback rung in [StickerResolver]. */
const val IDLE_ACTIVITY_ID = "idle"

/** Default-pack id prefix used for bundled assets-packs. */
const val DEFAULT_PACK_ID_PREFIX = "default-"

/** Guaranteed-to-exist final fallback species, per D.64 / D.66 rung 6. */
const val BAT_FALLBACK_SPECIES = "bat"
