package com.eight87.strictlykeptboy.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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

    /**
     * Phase 2.7.A.1 — copy the bundled sticker pack for [species] from
     * `assets/avatar-packs/<species>/` into [destDir] (creating it if
     * needed). Preserves relative paths + filenames.
     *
     * - Files that already exist at the destination are NOT overwritten —
     *   this lets a user clone the repo, edit `stickers/<species>/`, and
     *   trust that re-scaffolding (or pack-refresh flows) won't clobber
     *   their edits.
     * - Missing source directory (e.g. early-Phase-WW state where the
     *   artwork hasn't shipped yet) is tolerated: the destination dir is
     *   created empty and the call returns 0.
     * - I/O runs on [Dispatchers.IO]; safe to invoke from a suspend
     *   builder on the main thread.
     *
     * Returns the number of files written.
     */
    suspend fun copyPackInto(species: String, destDir: Path): Int = withContext(Dispatchers.IO) {
        Files.createDirectories(destDir)
        val assets = appContext.assets
        val srcRoot = "$ASSETS_ROOT/$species"
        var copied = 0
        fun walk(assetSubDir: String, destSubDir: Path) {
            val entries: Array<String> = runCatching {
                assets.list(assetSubDir)
            }.getOrNull() ?: emptyArray()
            for (entry in entries) {
                val childAsset = if (assetSubDir.isEmpty()) entry else "$assetSubDir/$entry"
                val childDest = destSubDir.resolve(entry)
                val childChildren = runCatching { assets.list(childAsset) }.getOrNull()
                if (childChildren != null && childChildren.isNotEmpty()) {
                    Files.createDirectories(childDest)
                    walk(childAsset, childDest)
                } else {
                    // Either a leaf file, or an empty directory we can ignore.
                    val isFile = runCatching {
                        assets.open(childAsset).use { /* just probe */ }
                        true
                    }.getOrElse { false }
                    if (!isFile) continue
                    if (Files.exists(childDest)) continue
                    Files.createDirectories(childDest.parent ?: destDir)
                    assets.open(childAsset).use { stream ->
                        Files.copy(stream, childDest, StandardCopyOption.REPLACE_EXISTING)
                    }
                    // REPLACE_EXISTING is safe because we already checked
                    // !exists above; passed so the call doesn't throw if a
                    // racy filesystem creates the file underneath us.
                    copied += 1
                }
            }
        }
        walk(srcRoot, destDir)
        copied
    }

    companion object {
        const val ASSETS_ROOT = "avatar-packs"
        private const val TAG = "AssetPackLoader"
    }
}
