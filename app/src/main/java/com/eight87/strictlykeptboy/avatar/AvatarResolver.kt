package com.eight87.strictlykeptboy.avatar

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import com.eight87.strictlykeptboy.R

/**
 * Phase WW — narrow facade for the **avatar** use site (top-bar repo
 * switcher chip + Repos list rows).
 *
 * Composes the [StickerResolver] chain with the [StickerBitmapCache] +
 * a concrete byte-source ([StickerBytesLoader]) so call-site composables
 * only need this one interface.
 *
 * Returns a sealed [Resolved] so call sites can render bitmap-or-fallback
 * exhaustively. The fallback drawable id is bundled
 * `R.drawable.about_bat` per D.66 rung 6.
 */
interface AvatarResolver {

    /** Resolve the avatar for `(species, activityId)`. */
    fun resolve(
        species: String,
        activityId: String? = null,
        neutralMode: Boolean = false,
    ): Resolved

    sealed interface Resolved {
        data class BitmapHit(val bitmap: Bitmap, val rung: StickerResolver.Rung) : Resolved
        data class DrawableFallback(@DrawableRes val drawableRes: Int, val rung: StickerResolver.Rung) : Resolved
    }
}

/**
 * Loads raw sticker bytes for a `(packId, file)` pair. Separated from
 * the resolver so the assets-backed and user-pack-folder-backed paths
 * can be swapped (Decorator-style) without the resolver knowing.
 */
fun interface StickerBytesLoader {
    /** Decode the sticker bytes to a [Bitmap], or return `null` if not present / decode fails. */
    fun loadBitmap(packId: String, file: String): Bitmap?
}

/**
 * Default [AvatarResolver] composing the sticker resolver chain with
 * the LRU cache and a byte-source. Pure constructor; no side effects.
 *
 * Caller is responsible for invalidating [stickerResolver] (the
 * underlying [DefaultStickerResolver]) and the cache when the active
 * pack changes.
 */
class DefaultAvatarResolver(
    private val stickerResolver: StickerResolver,
    private val cache: StickerBitmapCache,
    private val loader: StickerBytesLoader,
    @DrawableRes private val fallbackRes: Int = R.drawable.about_bat,
) : AvatarResolver {

    override fun resolve(
        species: String,
        activityId: String?,
        neutralMode: Boolean,
    ): AvatarResolver.Resolved {
        val resolution = stickerResolver.resolve(
            StickerResolver.Request(
                species = species,
                activityId = activityId,
                neutralMode = neutralMode,
            ),
        )
        return when (resolution) {
            is StickerResolver.Resolution.BatFallback ->
                AvatarResolver.Resolved.DrawableFallback(fallbackRes, resolution.rung)

            is StickerResolver.Resolution.Found -> {
                val key = cache.keyOf(resolution.packId, resolution.entry.activityId)
                val cached = cache.get(key)
                if (cached != null) {
                    AvatarResolver.Resolved.BitmapHit(cached, resolution.rung)
                } else {
                    val decoded = loader.loadBitmap(resolution.packId, resolution.entry.file)
                    if (decoded != null) {
                        cache.put(key, decoded)
                        AvatarResolver.Resolved.BitmapHit(decoded, resolution.rung)
                    } else {
                        // The pack referenced a file we couldn't decode — fall back
                        // gracefully rather than crashing the avatar surface. This
                        // preserves the D.66 "bat fallback always wins" contract.
                        AvatarResolver.Resolved.DrawableFallback(fallbackRes, StickerResolver.Rung.BatFallback)
                    }
                }
            }
        }
    }
}
