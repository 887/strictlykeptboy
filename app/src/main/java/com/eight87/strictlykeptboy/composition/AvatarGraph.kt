package com.eight87.strictlykeptboy.composition

import android.content.Context
import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.avatar.AvatarPackPrefs
import com.eight87.strictlykeptboy.avatar.AvatarResolver
import com.eight87.strictlykeptboy.avatar.CompositePackStore
import com.eight87.strictlykeptboy.avatar.DefaultAvatarResolver
import com.eight87.strictlykeptboy.avatar.DefaultStickerResolver
import com.eight87.strictlykeptboy.avatar.StickerBitmapCache
import com.eight87.strictlykeptboy.avatar.StickerResolver
import com.eight87.strictlykeptboy.avatar.UserPackLoader

/**
 * [M] #8d (audit pass 2026-05-17) — cohesive sub-graph holding the
 * avatar pack loaders + resolvers + bitmap cache extracted out of
 * [AppGraph]. No behavioural change: every `by lazy` retains the same
 * initialisation order and the same composition (user packs first,
 * then bundled defaults), and consumers in `MainActivity` reach these
 * fields via `graph.avatarGraph.<field>`.
 */
class AvatarGraph(private val appContext: Context) {

    /** Phase WW.5 — per-species active pack + per-activity sticker overrides. */
    val avatarPackPrefs: AvatarPackPrefs by lazy { AvatarPackPrefs.open(appContext) }

    /** Phase WW.1 — bundled-pack registry loaded from APK assets. */
    val assetPackLoader: AssetPackLoader by lazy { AssetPackLoader(appContext) }

    /** Phase WW.4 — user-installed pack registry (`<filesDir>/avatar-packs/`). */
    val userPackLoader: UserPackLoader by lazy { UserPackLoader.openFor(appContext) }

    /** Phase WW.2 — composite store: user packs first, then bundled defaults. */
    val packStore: CompositePackStore by lazy {
        CompositePackStore(
            sourceFactories = listOf(
                { userPackLoader.loadAll() },
                { assetPackLoader.loadAll() },
            ),
        )
    }

    /** Phase WW.2 — 6-rung D.66 resolver. */
    val stickerResolver: StickerResolver by lazy {
        DefaultStickerResolver(
            packStore = packStore,
            activePackProvider = { species -> avatarPackPrefs.activePackFor(species) },
        )
    }

    /** Phase WW.6 (MVP) — shared LRU cache for decoded sticker bitmaps. */
    val stickerBitmapCache: StickerBitmapCache by lazy { StickerBitmapCache() }

    /**
     * Phase WW — top-level avatar facade for top-bar / Repos rows / NowCard.
     *
     * Dispatches bitmap loading to whichever loader owns the resolved pack
     * (user vs bundled), falling back to `R.drawable.about_bat` when a pack
     * references a file that hasn't shipped yet (the initial bundled-pack
     * manifests are scaffolds — artwork lands in a follow-up).
     */
    val avatarResolver: AvatarResolver by lazy {
        DefaultAvatarResolver(
            stickerResolver = stickerResolver,
            cache = stickerBitmapCache,
            loader = { packId, file ->
                val species = if (packId.startsWith("default-")) packId.removePrefix("default-") else null
                if (species != null) {
                    assetPackLoader.loadBitmap(species, file)
                } else {
                    userPackLoader.loadBitmap(packId, file)
                }
            },
        )
    }
}
