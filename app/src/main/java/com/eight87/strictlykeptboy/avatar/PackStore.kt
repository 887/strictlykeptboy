package com.eight87.strictlykeptboy.avatar

// Phase WW.2 / WW.4 — narrow read-side over loaded sticker packs.
// Concrete impls live behind this interface so the resolver doesn't
// import Context / AssetManager directly (DIP).
fun interface PackStore {
    fun get(packId: String): StickerPack?
}

// Static PackStore backed by an in-memory map. Useful for tests and as
// the bundled-pack registry built once at app start from
// assets/avatar-packs/.
class StaticPackStore(private val packs: Map<String, StickerPack>) : PackStore {
    override fun get(packId: String): StickerPack? = packs[packId]

    fun all(): Collection<StickerPack> = packs.values
}
