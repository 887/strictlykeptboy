package com.eight87.strictlykeptboy.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Phase WW.4 — discover user-installed packs cloned into
 * `<app-private>/avatar-packs/<pack-id>/`.
 *
 * The actual clone wiring (Phase B credential reuse, JGit shallow
 * clone, network) is deferred to WW.4 follow-up. This loader handles
 * the on-disk side: scanning the directory, validating each manifest,
 * exposing decoded bitmaps to the resolver.
 *
 * Per D.69: pack repos are registered as **read-only data sources**;
 * pushing is never attempted from this loader.
 */
class UserPackLoader(private val rootDir: File) {

    /** Parent dir holding `<pack-id>/` sub-folders. Created on demand. */
    val packsRoot: File = File(rootDir, ASSETS_ROOT).also {
        if (!it.exists()) it.mkdirs()
    }

    /**
     * Scan [packsRoot] and parse every `pack.toml`. Silently skips
     * malformed packs (logged) so one bad pack doesn't break others.
     */
    fun loadAll(): StaticPackStore {
        val packs = LinkedHashMap<String, StickerPack>()
        val dirs = packsRoot.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (dir in dirs) {
            val packId = dir.name
            val manifestFile = File(dir, "pack.toml")
            if (!manifestFile.isFile) continue
            val toml = runCatching { manifestFile.readText() }.getOrElse {
                Log.w(TAG, "unreadable manifest at ${manifestFile.absolutePath}: ${it.message}")
                continue
            }
            val pack = runCatching { PackManifest.parse(toml, packId) }
                .getOrElse {
                    Log.w(TAG, "invalid manifest at ${manifestFile.absolutePath}: ${it.message}")
                    null
                }
            if (pack != null) packs[packId] = pack
        }
        return StaticPackStore(packs)
    }

    /** Decode a sticker file from `<packsRoot>/<packId>/<file>`. */
    fun loadBitmap(packId: String, file: String): Bitmap? {
        val src = File(File(packsRoot, packId), file)
        if (!src.isFile) return null
        return runCatching {
            src.inputStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    companion object {
        const val ASSETS_ROOT = "avatar-packs"
        private const val TAG = "UserPackLoader"

        /** Default location inside the app-private filesDir. */
        fun openFor(context: Context): UserPackLoader =
            UserPackLoader(rootDir = context.filesDir)
    }
}
