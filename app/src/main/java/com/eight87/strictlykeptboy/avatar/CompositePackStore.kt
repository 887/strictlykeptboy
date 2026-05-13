package com.eight87.strictlykeptboy.avatar

/**
 * Phase WW.4 — fan-out [PackStore] that consults multiple sources in
 * order. The first source returning non-null wins. Convention: user-
 * installed packs come first so that if a user clones a custom bat
 * pack, it overrides the bundled default-bat assets.
 */
class CompositePackStore(private val sources: List<PackStore>) : PackStore {
    override fun get(packId: String): StickerPack? {
        for (s in sources) s.get(packId)?.let { return it }
        return null
    }

    /** Convenience: list every pack across every source (for picker UIs). */
    fun all(): Collection<StickerPack> {
        val out = LinkedHashMap<String, StickerPack>()
        for (s in sources) {
            if (s is StaticPackStore) {
                for (p in s.all()) out.putIfAbsent(p.packId, p)
            }
        }
        return out.values
    }
}
