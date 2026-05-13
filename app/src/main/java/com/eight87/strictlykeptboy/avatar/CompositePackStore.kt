package com.eight87.strictlykeptboy.avatar

/**
 * Phase WW.4 — fan-out [PackStore] that consults multiple sources in
 * order. The first source returning non-null wins. Convention: user-
 * installed packs come first so that if a user clones a custom bat
 * pack, it overrides the bundled default-bat assets.
 *
 * Phase 2.5.C — sources are now produced lazily via [sourceFactories] so
 * the picker can call [refresh] after importing a custom pack and pick
 * up the new entry without recreating the composite.
 */
class CompositePackStore(
    private val sourceFactories: List<() -> PackStore>,
) : PackStore {

    @Volatile
    private var sources: List<PackStore> = sourceFactories.map { it() }

    override fun get(packId: String): StickerPack? {
        for (s in sources) s.get(packId)?.let { return it }
        return null
    }

    /** Re-invoke every source factory; subsequent [get] / [all] sees fresh state. */
    fun refresh() {
        sources = sourceFactories.map { it() }
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

    companion object {
        /** Helper for the static use case (kept for ergonomics + tests). */
        fun ofStatic(sources: List<PackStore>): CompositePackStore =
            CompositePackStore(sourceFactories = sources.map<PackStore, () -> PackStore> { s -> ({ s }) })
    }
}
