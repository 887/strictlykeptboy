package com.eight87.strictlykeptboy.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * Phase WW.1 — load bundled default packs from
 * `app/src/main/assets/avatar-packs/<species>/pack.toml` (D.65).
 *
 * Bundled species and their pack ids are derived via
 * [PackId.forBundledSpecies] so callers can look them up without
 * reading the file system.
 */
class AssetPackLoader(private val appContext: Context) {

    /**
     * Discover and parse every `pack.toml` under `assets/avatar-packs/`.
     * Returns a static store ready to back the resolver. Silently skips
     * malformed packs (logged to logcat) so a broken third-party pack
     * dropped into assets at build time doesn't take the whole app down.
     */
    fun loadAll(): StaticPackStore {
        val packs = LinkedHashMap<String, StickerPack>()
        val assets = appContext.assets
        val species: Array<String> = runCatching {
            assets.list(ASSETS_ROOT)
        }.getOrNull() ?: emptyArray()
        for (s in species) {
            val packId = PackId.forBundledSpecies(s)
            val pack = runCatching { loadOne(s, packId) }.getOrNull()
            if (pack != null) packs[packId] = pack
        }
        return StaticPackStore(packs)
    }

    private fun loadOne(species: String, packId: String): StickerPack? {
        val manifestPath = "$ASSETS_ROOT/$species/pack.toml"
        val toml = runCatching {
            appContext.assets.open(manifestPath).bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.w(TAG, "missing manifest for $species at $manifestPath: ${it.message}")
            return null
        }
        return runCatching { PackManifest.parse(toml, packId) }
            .getOrElse {
                Log.w(TAG, "failed to parse $manifestPath: ${it.message}")
                null
            }
    }

    /**
     * Decode a sticker file from `assets/avatar-packs/<species>/<file>`.
     * Returns `null` on any decode error.
     */
    fun loadBitmap(species: String, file: String): Bitmap? {
        return runCatching {
            appContext.assets.open("$ASSETS_ROOT/$species/$file").use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }

    companion object {
        const val ASSETS_ROOT = "avatar-packs"
        private const val TAG = "AssetPackLoader"
    }
}
