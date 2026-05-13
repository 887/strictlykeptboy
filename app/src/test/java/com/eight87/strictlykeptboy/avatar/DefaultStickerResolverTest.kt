package com.eight87.strictlykeptboy.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase WW.2 — D.66 chain tests. Pure JVM (no Android dependencies).
 */
class DefaultStickerResolverTest {

    private fun pack(
        species: String,
        entries: Map<String, Set<StickerTag>>,
    ): StickerPack {
        val map = entries.mapValues { (id, tags) ->
            StickerEntry(activityId = id, file = "$id.webp", tags = tags)
        }
        return StickerPack(
            packId = PackId.forBundledSpecies(species),
            name = "Test $species",
            species = species,
            stickers = map,
        )
    }

    private fun store(vararg packs: StickerPack): PackStore {
        val map = packs.associateBy { it.packId }
        return StaticPackStore(map)
    }

    private fun resolver(store: PackStore): DefaultStickerResolver =
        DefaultStickerResolver(
            packStore = store,
            activePackProvider = { species -> PackId.forBundledSpecies(species) },
        )

    @Test
    fun `rung 1 — per-event override wins`() {
        val p = pack(
            "bat",
            mapOf(
                "idle" to setOf(StickerTag.Idle, StickerTag.Neutral),
                "pushup" to setOf(StickerTag.Workout),
                "special" to setOf(StickerTag.Neutral),
            ),
        )
        val r = resolver(store(p))
        val out = r.resolve(
            StickerResolver.Request(species = "bat", activityId = "pushup", perEventOverride = "special"),
        )
        assertTrue(out is StickerResolver.Resolution.Found)
        assertEquals("special", (out as StickerResolver.Resolution.Found).entry.activityId)
        assertEquals(StickerResolver.Rung.PerEventOverride, out.rung)
    }

    @Test
    fun `rung 3 — activity-specific match`() {
        val p = pack("bat", mapOf("idle" to setOf(StickerTag.Idle), "pushup" to setOf(StickerTag.Workout)))
        val out = resolver(store(p)).resolve(
            StickerResolver.Request(species = "bat", activityId = "pushup"),
        )
        assertTrue(out is StickerResolver.Resolution.Found)
        assertEquals(StickerResolver.Rung.ActivitySpecific, (out as StickerResolver.Resolution.Found).rung)
    }

    @Test
    fun `rung 4 — category-generic match`() {
        val p = pack("bat", mapOf("idle" to setOf(StickerTag.Idle), "workout" to setOf(StickerTag.Workout)))
        val out = resolver(store(p)).resolve(
            StickerResolver.Request(species = "bat", activityId = "workout-pushup"),
        )
        assertTrue(out is StickerResolver.Resolution.Found)
        assertEquals("workout", (out as StickerResolver.Resolution.Found).entry.activityId)
        assertEquals(StickerResolver.Rung.CategoryGeneric, out.rung)
    }

    @Test
    fun `rung 5 — species idle when activity unknown`() {
        val p = pack("bat", mapOf("idle" to setOf(StickerTag.Idle)))
        val out = resolver(store(p)).resolve(
            StickerResolver.Request(species = "bat", activityId = "no-such-activity"),
        )
        assertTrue(out is StickerResolver.Resolution.Found)
        assertEquals(StickerResolver.Rung.SpeciesIdle, (out as StickerResolver.Resolution.Found).rung)
    }

    @Test
    fun `rung 6 — bat fallback when pack missing`() {
        val out = resolver(StaticPackStore(emptyMap())).resolve(
            StickerResolver.Request(species = "bat", activityId = "pushup"),
        )
        assertEquals(StickerResolver.Resolution.BatFallback, out)
    }

    @Test
    fun `neutral mode filters kink-tagged stickers`() {
        val p = pack(
            "bat",
            mapOf(
                "idle" to setOf(StickerTag.Idle),
                "cage" to setOf(StickerTag.Kink),
            ),
        )
        // Without neutral mode → activity-specific hit.
        val kinkOn = resolver(store(p)).resolve(
            StickerResolver.Request(species = "bat", activityId = "cage", neutralMode = false),
        )
        assertEquals(StickerResolver.Rung.ActivitySpecific, (kinkOn as StickerResolver.Resolution.Found).rung)

        // With neutral mode → kink-only tag is filtered, falls through to idle.
        val kinkOff = resolver(store(p)).resolve(
            StickerResolver.Request(species = "bat", activityId = "cage", neutralMode = true),
        )
        assertEquals(StickerResolver.Rung.SpeciesIdle, (kinkOff as StickerResolver.Resolution.Found).rung)
    }

    @Test
    fun `neutral mode passes kink-plus-neutral stickers`() {
        val p = pack(
            "bat",
            mapOf(
                "idle" to setOf(StickerTag.Idle),
                "stretching" to setOf(StickerTag.Kink, StickerTag.Neutral),
            ),
        )
        val out = resolver(store(p)).resolve(
            StickerResolver.Request(species = "bat", activityId = "stretching", neutralMode = true),
        )
        assertEquals(StickerResolver.Rung.ActivitySpecific, (out as StickerResolver.Resolution.Found).rung)
    }

    @Test
    fun `memoization returns cached result`() {
        val p = pack("bat", mapOf("idle" to setOf(StickerTag.Idle)))
        val r = resolver(store(p))
        val a = r.resolve(StickerResolver.Request(species = "bat", activityId = "idle"))
        val b = r.resolve(StickerResolver.Request(species = "bat", activityId = "idle"))
        // Same instance returned (cache hit).
        assertTrue(a === b)
    }
}
