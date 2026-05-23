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
        val manifestLines = readManifestLines()
        val bundledHash = manifestLines.contentHash
        val seededHash = prefs.getString(KEY_SEEDED_HASH, null)
        if (seededHash != null && seededHash == bundledHash && repoRoot.exists()) {
            return@withContext Result.success(repoRoot)
        }
        runCatching {
            if (repoRoot.exists()) {
                repoRoot.deleteRecursively()
            }
            extract(repoRoot.toPath(), manifestLines.entries)
            reconstructSymlink(repoRoot.toPath())
            prefs.edit {
                putString(KEY_SEEDED_HASH, bundledHash)
                // Keep the legacy boolean for callers that still read it
                // (DemoRepoSeeder gating, settings "demo seeded?" badge).
                putBoolean(KEY_SEEDED, true)
            }
            repoRoot
        }.onFailure { t ->
            Log.w(TAG, "rich-demo seed failed; cleaning partial extract", t)
            runCatching { repoRoot.deleteRecursively() }
        }
    }

    fun isSeeded(): Boolean = prefs.getBoolean(KEY_SEEDED, false)

    /**
     * Cheap hash-check used by MainActivity's on-startup re-seed gate. Reads
     * the bundled manifest header (one asset open + a couple of lines) and
     * compares against [KEY_SEEDED_HASH]. Returns true when the seed should
     * be re-extracted (asset content changed since the last seed). False if
     * the seed is fresh, never ran, or the asset open fails — never seeded
     * is handled by [seedIfNeeded] on the next call.
     */
    fun needsReseed(): Boolean = runCatching {
        val bundledHash = readManifestLines().contentHash
        val seededHash = prefs.getString(KEY_SEEDED_HASH, null) ?: return@runCatching true
        seededHash != bundledHash
    }.getOrDefault(false)

    fun resetSeededFlag() {
        prefs.edit {
            remove(KEY_SEEDED)
            remove(KEY_SEEDED_HASH)
        }
    }

    // --- internals --------------------------------------------------

    private fun extract(repoRoot: Path, entries: List<String>) {
        Files.createDirectories(repoRoot)
        for (relPath in entries) {
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

    /**
     * Parses the manifest into its content-hash header (`# content-hash: …`,
     * written by the `regenerateRichDemoManifest` gradle task) and its
     * ordered list of file entries. Manifest lines without a hash header
     * (legacy builds) fall back to a hash derived from the entry list
     * itself — still better than the previous boolean idempotency since
     * adding / removing a file invalidates the seed.
     */
    private fun readManifestLines(): ManifestContents {
        var headerHash: String? = null
        val entries = mutableListOf<String>()
        context.assets.open("$ASSET_ROOT/$MANIFEST_NAME").bufferedReader().use { r ->
            r.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                if (line.startsWith("#")) {
                    val prefix = "# content-hash:"
                    if (line.startsWith(prefix)) {
                        headerHash = line.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
                    }
                    return@forEach
                }
                entries += line
            }
        }
        val hash = headerHash ?: run {
            // Fallback: hash the entry list. Won't catch in-place content
            // edits, but does catch adds / renames / removes.
            val md = java.security.MessageDigest.getInstance("SHA-256")
            entries.forEach {
                md.update(it.toByteArray(Charsets.UTF_8))
                md.update(0)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        return ManifestContents(contentHash = hash, entries = entries)
    }

    private data class ManifestContents(
        val contentHash: String,
        val entries: List<String>,
    )

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

        /**
         * Legacy boolean kept for back-compat with callers that just want
         * "did the rich-demo get extracted at any point". The actual
         * idempotency now lives in [KEY_SEEDED_HASH] — the seeder
         * re-extracts whenever the bundled manifest's content-hash header
         * (written by the `regenerateRichDemoManifest` gradle task)
         * differs from the hash stored after the last successful seed.
         * So content changes auto-invalidate; no manual version-bump
         * dance required.
         */
        const val KEY_SEEDED = "pref_rich_demo_seeded_v3"

        /**
         * Content-hash of the bundled rich-demo at last successful seed.
         * Compared against the manifest header on every launch — mismatch
         * triggers a re-extract.
         */
        const val KEY_SEEDED_HASH = "pref_rich_demo_seeded_hash"
    }
}
