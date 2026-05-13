package com.eight87.strictlykeptboy.avatar

import java.util.concurrent.ConcurrentHashMap

/**
 * Phase WW.2 — concrete [StickerResolver] implementing the D.66 chain.
 *
 * Inputs:
 *  - [packStore]: resolves `pack id → StickerPack` (assets + user-cloned).
 *  - [activePackProvider]: per-species "which pack id is active for this species".
 *    Read-only; the picker UI mutates the underlying [AvatarPackPrefs].
 *
 * Resolution chain (top-down, first match wins) per D.66:
 *
 *   1. **Per-event override** — caller-supplied `sticker_id` on the
 *      active event's frontmatter.
 *   2. **Sub-beat** — `subbeatStickerId` on the active `[[subbeat]]`.
 *   3. **Activity-specific** — pack entry matching `activityId` exactly
 *      (with neutral-mode filter: kink-tagged entries skip unless also
 *      neutral-tagged).
 *   4. **Category-generic** — pack entry matching the prefix-stripped
 *      category id (e.g. `workout-pushup` → `workout`). Neutral-mode
 *      filter applies.
 *   5. **Species idle** — pack entry with `activity_id = "idle"`.
 *   6. **Bat fallback** — render `R.drawable.about_bat`. Always wins
 *      if nothing else matched; guaranteed-to-exist by build.
 *
 * Memoization: `(species, activity, sub-beat-index, pack-id, neutral-
 * mode, per-event-override)` cache. ConcurrentHashMap keeps this thread-
 * safe; the cache is bounded only by the number of distinct request
 * tuples seen (tiny in practice — there's a closed set of activity ids
 * per pack, so the worst-case is `|packs| * |activities| * 2`).
 */
class DefaultStickerResolver(
    private val packStore: PackStore,
    private val activePackProvider: (species: String) -> String,
) : StickerResolver {

    private val cache = ConcurrentHashMap<CacheKey, StickerResolver.Resolution>()

    override fun resolve(request: StickerResolver.Request): StickerResolver.Resolution {
        val key = CacheKey(
            species = request.species.lowercase(),
            activityId = request.activityId,
            subbeatIndex = request.subbeatIndex,
            subbeatStickerId = request.subbeatStickerId,
            perEventOverride = request.perEventOverride,
            packId = activePackProvider(request.species.lowercase()),
            neutralMode = request.neutralMode,
        )
        cache[key]?.let { return it }
        val resolved = resolveUncached(request, key.packId)
        cache[key] = resolved
        return resolved
    }

    /** Invalidate all memoized entries (call when the active-pack picker changes). */
    fun invalidate() { cache.clear() }

    // ----------------------------------------------------------------

    private fun resolveUncached(
        req: StickerResolver.Request,
        packId: String,
    ): StickerResolver.Resolution {
        val pack: StickerPack? = packStore.get(packId)
        val neutral = req.neutralMode

        // Rung 1 — per-event override
        if (req.perEventOverride != null && pack != null) {
            pack.stickers[req.perEventOverride]?.let { e ->
                if (passesNeutralFilter(e, neutral)) {
                    return StickerResolver.Resolution.Found(
                        packId = packId, entry = e,
                        rung = StickerResolver.Rung.PerEventOverride,
                    )
                }
            }
        }
        // Rung 2 — sub-beat
        if (req.subbeatStickerId != null && pack != null) {
            pack.stickers[req.subbeatStickerId]?.let { e ->
                if (passesNeutralFilter(e, neutral)) {
                    return StickerResolver.Resolution.Found(
                        packId = packId, entry = e,
                        rung = StickerResolver.Rung.SubBeat,
                    )
                }
            }
        }
        // Rung 3 — activity-specific
        if (req.activityId != null && pack != null) {
            pack.stickers[req.activityId]?.let { e ->
                if (passesNeutralFilter(e, neutral)) {
                    return StickerResolver.Resolution.Found(
                        packId = packId, entry = e,
                        rung = StickerResolver.Rung.ActivitySpecific,
                    )
                }
            }
        }
        // Rung 4 — category-generic (`workout-pushup` → `workout`)
        if (req.activityId != null && pack != null) {
            val dash = req.activityId.indexOf('-')
            if (dash > 0) {
                val category = req.activityId.substring(0, dash)
                pack.stickers[category]?.let { e ->
                    if (passesNeutralFilter(e, neutral)) {
                        return StickerResolver.Resolution.Found(
                            packId = packId, entry = e,
                            rung = StickerResolver.Rung.CategoryGeneric,
                        )
                    }
                }
            }
        }
        // Rung 5 — species idle
        if (pack != null) {
            pack.stickers[IDLE_ACTIVITY_ID]?.let { e ->
                // Idle is the species ambient state — neutral filter doesn't apply
                // to a species-idle sticker (it's species, not activity).
                return StickerResolver.Resolution.Found(
                    packId = packId, entry = e,
                    rung = StickerResolver.Rung.SpeciesIdle,
                )
            }
        }
        // Rung 6 — bat fallback (guaranteed)
        return StickerResolver.Resolution.BatFallback
    }

    /**
     * Neutral-mode filter (D.66): hide stickers tagged `kink` unless they
     * also carry `neutral`. Non-neutral-mode passes everything.
     */
    private fun passesNeutralFilter(entry: StickerEntry, neutral: Boolean): Boolean {
        if (!neutral) return true
        val tags = entry.tags
        val isKink = tags.any { it == StickerTag.Kink }
        val isNeutral = tags.any { it == StickerTag.Neutral }
        return !isKink || isNeutral
    }

    private data class CacheKey(
        val species: String,
        val activityId: String?,
        val subbeatIndex: Int?,
        val subbeatStickerId: String?,
        val perEventOverride: String?,
        val packId: String,
        val neutralMode: Boolean,
    )
}
