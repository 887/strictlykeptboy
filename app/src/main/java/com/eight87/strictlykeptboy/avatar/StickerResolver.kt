package com.eight87.strictlykeptboy.avatar

/**
 * Phase WW.2 — narrow port for sticker resolution.
 *
 * Single method; consumers (now-card, top-bar avatar, repos-list rows)
 * only need this one operation and should not depend on the concrete
 * [DefaultStickerResolver] / pack-store / cache wiring (ISP + DIP).
 *
 * Pure with respect to its inputs: the same [Request] always returns the
 * same [Resolution] given a stable pack store. Memoization happens
 * inside the impl on `(activity_id, sub-beat index, pack id, neutral-
 * mode)` per D.66.
 */
fun interface StickerResolver {
    fun resolve(request: Request): Resolution

    /**
     * Inputs the resolver examines. Optional fields default to `null`
     * so callers only fill in what they have.
     */
    data class Request(
        /** Species the active repo / user picked. Required. */
        val species: String,
        /** Current activity id (e.g. `pushup`, `shower`). `null` → species idle. */
        val activityId: String? = null,
        /** Currently-active sub-beat index, if any. `null` if not in a sub-beat. */
        val subbeatIndex: Int? = null,
        /** Sub-beat-specific override (D.68 `sticker_id` on `[[subbeat]]`). */
        val subbeatStickerId: String? = null,
        /** Per-event override (D.66 rung 1 — `sticker_id` on event frontmatter). */
        val perEventOverride: String? = null,
        /** Neutral-mode toggle (D.58 / D.66): filters `kink`-tagged stickers. */
        val neutralMode: Boolean = false,
    )

    /**
     * Output. Sealed so callers exhaustively handle the bat-fallback case
     * without a string-sentinel sniffing pattern.
     */
    sealed interface Resolution {
        /** Which rung in the D.66 chain matched. Useful for debug overlays + tests. */
        val rung: Rung

        /**
         * Concrete sticker entry, with the pack id needed to resolve its
         * binary location.
         */
        data class Found(
            val packId: String,
            val entry: StickerEntry,
            override val rung: Rung,
        ) : Resolution

        /**
         * Bat fallback (D.66 rung 6). The renderer should draw the bundled
         * `R.drawable.about_bat` resource. Guaranteed-to-exist so callers
         * never see a `null` resolution.
         */
        data object BatFallback : Resolution {
            override val rung = Rung.BatFallback
        }
    }

    /** Which rung in the D.66 chain matched. */
    enum class Rung {
        PerEventOverride,        // 1
        SubBeat,                 // 2
        ActivitySpecific,        // 3
        CategoryGeneric,         // 4
        SpeciesIdle,             // 5
        BatFallback,             // 6
    }
}
