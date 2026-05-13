package com.eight87.strictlykeptboy.widget.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.avatar.StickerBitmapCache
import com.eight87.strictlykeptboy.avatar.StickerResolver

/**
 * Phase VV.4 / EEE.4 — widget-surface sticker bitmap renderer.
 *
 * Wraps the WW [StickerResolver] with bitmap-decode + LRU-cache wiring
 * a `RemoteViews.setImageViewBitmap` call needs. Falls back to
 * `R.drawable.about_bat` for `BatFallback` and on any decode error.
 *
 * Cache keys follow [StickerBitmapCache.keyOf] so value-mode NowCard
 * (UI-LL) entries and widget entries share the cache.
 */
class WidgetStickerRenderer(
    private val context: Context,
    private val resolver: StickerResolver,
    private val assetLoader: AssetPackLoader,
    private val cache: StickerBitmapCache,
) {

    fun renderBatFallback(): Bitmap? = batBitmap()

    fun render(request: StickerResolver.Request): Bitmap? {
        val resolution = resolver.resolve(request)
        return when (resolution) {
            is StickerResolver.Resolution.Found -> {
                val key = cache.keyOf(resolution.packId, resolution.entry.activityId)
                cache.get(key) ?: run {
                    val bmp = assetLoader.loadBitmap(request.species, resolution.entry.file)
                    if (bmp != null) cache.put(key, bmp)
                    bmp ?: batBitmap()
                }
            }
            is StickerResolver.Resolution.BatFallback -> batBitmap()
        }
    }

    private fun batBitmap(): Bitmap? {
        cache.get(BAT_KEY)?.let { return it }
        return runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.about_bat)
        }.getOrNull()?.also { cache.put(BAT_KEY, it) }
    }

    companion object {
        private const val BAT_KEY = "__bat__:fallback"
    }
}
