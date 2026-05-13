package com.eight87.strictlykeptboy.ui.trip

import com.eight87.strictlykeptboy.avatar.StickerResolver

/**
 * Phase CCC.11 / HV-H — locked sticker-key registry for the trip wizard.
 *
 * Each [TripScreen] maps to one bat-mascot beat from HV-H. The sticker
 * resolver (Phase WW.2) consumes these keys via `activityId` lookup. When
 * a key isn't present in the active pack the resolver falls back through
 * the standard D.66 chain (category-generic → species-idle → bat-fallback),
 * so the wizard renders correctly even before the HV-H.7 SVG assets ship.
 *
 * Neutral-variant swaps (HV-H.3, HV-H.6) are tagged so the resolver's
 * `passesNeutralFilter` rule kicks in: when neutral-mode is ON the kink-
 * coded key skips and the resolver continues down the chain to the
 * neutral-tagged sibling. The sibling key is documented here so the
 * future pack-manifest author knows which two keys must share an entry.
 *
 * SOLID notes:
 *  - **S** — owns ONLY the key registry. Resolver invocation lives in
 *    [resolveTripScreenSticker]; pack data lives in the existing
 *    `StickerPack` shape.
 *  - **O** — adding a beat = adding a [TripScreenBeat] entry, not editing
 *    an exhaustive `when`. The resolver doesn't change.
 *  - **D** — depends only on the abstract `StickerResolver` interface.
 */
object TripStickerBeats {

    data class TripScreenBeat(
        val screen: TripScreen,
        val kinkKey: String,
        val neutralKey: String? = null,
    )

    /** Locked HV-H roster — wizard renders one of these per screen. */
    val beats: List<TripScreenBeat> = listOf(
        TripScreenBeat(TripScreen.Basics, "trip-suitcase-waving"),
        TripScreenBeat(TripScreen.Transport, "flight-paw-prints"),
        TripScreenBeat(
            TripScreen.Anchors,
            kinkKey = "beach-loungin-with-cage-still-on",
            neutralKey = "beach-loungin",
        ),
        TripScreenBeat(
            TripScreen.Confirm,
            kinkKey = "confirm-tail-flick",
            // HV-H.6 reassurance bubble — neutral variant.
            neutralKey = "staying-on-track",
        ),
    )

    /** Lookup by screen. */
    fun beatFor(screen: TripScreen): TripScreenBeat =
        beats.first { it.screen == screen }

    /**
     * Phase CCC.11 — neutral-aware sticker resolution for the trip wizard.
     *
     * Returns the resolver's choice for the active screen + active species
     * pack. The resolver memoizes by `(species, activityId, neutralMode)`
     * so the result is cheap to re-fetch on every recomposition.
     */
    fun resolveTripScreenSticker(
        resolver: StickerResolver,
        species: String,
        screen: TripScreen,
        neutralMode: Boolean,
    ): StickerResolver.Resolution {
        val beat = beatFor(screen)
        val key = if (neutralMode && beat.neutralKey != null) beat.neutralKey else beat.kinkKey
        return resolver.resolve(
            StickerResolver.Request(
                species = species,
                activityId = key,
                neutralMode = neutralMode,
            ),
        )
    }
}
