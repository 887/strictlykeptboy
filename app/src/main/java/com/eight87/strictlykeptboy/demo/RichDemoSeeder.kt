package com.eight87.strictlykeptboy.demo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Round 2.20 Phase B — rich-demo asset extractor.
 *
 * Walks `assets/rich-demo-repo/_manifest.txt` and copies every listed
 * file out of the APK assets into `<parentDir>/rich-demo/<path>`.
 * Idempotent via a versioned [SharedPreferences] flag
 * (`pref_rich_demo_seeded_v1`); bump the version when shipping a new
 * rich-demo content snapshot to force re-seed.
 *
 * **Symlink reconstruction:** the manifest contains a marker file
 * `.claudemd-is-symlink` indicating that `CLAUDE.md` should be a
 * symbolic link to `AGENTS.md` (per the produced-repo convention in
 * CLAUDE.md / D-2.20). After extraction we delete the marker and call
 * [Files.createSymbolicLink]; if the underlying filesystem rejects
 * symlinks (some FAT-mounted emulator volumes), we fall back to
 * copying `AGENTS.md` to `CLAUDE.md` and log the fallback.
 *
 * **Crash-safe:** on failure mid-extraction, the partially-written
 * `rich-demo/` dir is deleted so the next call gets a clean slate.
 *
 * SOLID: single responsibility — assets → filesystem. The wizard-
 * scaffold-based [DemoRepoSeeder] stays unchanged; this is a sibling.
 */
class RichDemoSeeder(
    private val context: Context,
    private val prefs: SharedPreferences,
) {

    /**
     * Extract the bundled rich-demo repo under [parentDir] iff it has
     * not been seeded yet. Returns the repo root on success (or on
     * already-seeded short-circuit). All I/O on [Dispatchers.IO].
     */
    suspend fun seedIfNeeded(parentDir: File): Result<File> = withContext(Dispatchers.IO) {
        val repoRoot = File(parentDir, REPO_DIR_NAME)
        if (isSeeded() && repoRoot.exists()) {
            return@withContext Result.success(repoRoot)
        }
        runCatching {
            if (repoRoot.exists()) {
                repoRoot.deleteRecursively()
            }
            extract(repoRoot.toPath())
            reconstructSymlink(repoRoot.toPath())
            prefs.edit { putBoolean(KEY_SEEDED, true) }
            repoRoot
        }.onFailure { t ->
            Log.w(TAG, "rich-demo seed failed; cleaning partial extract", t)
            runCatching { repoRoot.deleteRecursively() }
        }
    }

    fun isSeeded(): Boolean = prefs.getBoolean(KEY_SEEDED, false)

    fun resetSeededFlag() {
        prefs.edit { remove(KEY_SEEDED) }
    }

    // --- internals --------------------------------------------------

    private fun extract(repoRoot: Path) {
        Files.createDirectories(repoRoot)
        val manifest = readManifest()
        for (relPath in manifest) {
            // Skip the manifest itself — no need to extract the index.
            if (relPath == MANIFEST_NAME) continue
            val target = repoRoot.resolve(relPath)
            val parent = target.parent
            if (parent != null) Files.createDirectories(parent)
            context.assets.open("$ASSET_ROOT/$relPath").use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun readManifest(): List<String> {
        return context.assets.open("$ASSET_ROOT/$MANIFEST_NAME").bufferedReader().use { r ->
            r.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
    }

    private fun reconstructSymlink(repoRoot: Path) {
        val marker = repoRoot.resolve(SYMLINK_MARKER)
        if (!Files.exists(marker)) return
        runCatching { Files.delete(marker) }
        val claudeMd = repoRoot.resolve("CLAUDE.md")
        val agentsMd = repoRoot.resolve("AGENTS.md")
        // Ensure destination doesn't already exist (extraction shouldn't
        // have produced one, but the manifest could grow CLAUDE.md
        // accidentally).
        runCatching { Files.deleteIfExists(claudeMd) }
        try {
            // Relative target so the link survives a repo move.
            Files.createSymbolicLink(claudeMd, claudeMd.parent.relativize(agentsMd))
        } catch (uoe: UnsupportedOperationException) {
            Log.w(TAG, "filesystem does not support symlinks; copying AGENTS.md → CLAUDE.md", uoe)
            Files.copy(agentsMd, claudeMd, StandardCopyOption.REPLACE_EXISTING)
        } catch (fse: FileSystemException) {
            Log.w(TAG, "symlink creation rejected; copying AGENTS.md → CLAUDE.md", fse)
            Files.copy(agentsMd, claudeMd, StandardCopyOption.REPLACE_EXISTING)
        } catch (ioe: IOException) {
            Log.w(TAG, "symlink creation failed; copying AGENTS.md → CLAUDE.md", ioe)
            Files.copy(agentsMd, claudeMd, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val TAG = "RichDemoSeeder"

        /** Asset directory under `src/main/assets/`. */
        const val ASSET_ROOT = "rich-demo-repo"

        /** Filename for the generated manifest (one relative path per line). */
        const val MANIFEST_NAME = "_manifest.txt"

        /**
         * Marker authored at Phase A; presence means CLAUDE.md should be a
         * symlink to AGENTS.md. Named without a leading dot because aapt2
         * silently strips dot-prefixed files from `assets/` (verified
         * empirically — the dotfile vanishes from the packaged APK).
         */
        const val SYMLINK_MARKER = "claudemd-is-symlink.marker"

        /** Subdirectory under [seedIfNeeded]'s `parentDir`. */
        const val REPO_DIR_NAME = "rich-demo"

        /** Versioned idempotency key — bump to force re-seed after content updates. */
        const val KEY_SEEDED = "pref_rich_demo_seeded_v1"
    }
}
