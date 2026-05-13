package com.eight87.strictlykeptboy.avatar

import android.graphics.Bitmap
import androidx.collection.LruCache

/**
 * Phase WW.6 (MVP slice) — LRU cache for decoded sticker bitmaps.
 *
 * Sized by byte-count of pixel data (so a single 512×512 WebP doesn't
 * starve a row of 48dp avatars). Default ~20 MB target which fits the
 * D.66 / AV-H budget (~40 stickers @ 512×512×RGBA = ~40 × 1 MB).
 *
 * Keys are stable strings (`"<pack-id>:<activity-id>"`) so callers
 * don't have to plumb full entries through. Decoder lives outside this
 * class — the cache is decode-agnostic.
 *
 * The cache uses `androidx.collection.LruCache`, which is thread-safe.
 */
class StickerBitmapCache(maxByteCount: Int = DEFAULT_MAX_BYTES) {

    private val inner = object : LruCache<String, Bitmap>(maxByteCount) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? = inner.get(key)

    fun put(key: String, bitmap: Bitmap) {
        inner.put(key, bitmap)
    }

    fun evictAll() { inner.evictAll() }

    /** Convenience key builder so all callers agree on the format. */
    fun keyOf(packId: String, activityId: String): String = "$packId:$activityId"

    companion object {
        /** ~20 MB; tuned for 40 stickers at 512×512 RGBA per D.66 budget. */
        const val DEFAULT_MAX_BYTES: Int = 20 * 1024 * 1024
    }
}
