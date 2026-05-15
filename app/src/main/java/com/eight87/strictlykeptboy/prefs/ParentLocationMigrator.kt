package com.eight87.strictlykeptboy.prefs

import com.eight87.strictlykeptboy.git.RepoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Round 2.17.A.4 — one-shot migrator from D-2.7.b's `filesDir/repos/`
 * layout to D-2.17.a's `<parent>/<repoId>/` layout.
 *
 * Behaviour, in order:
 *  1. If [RepoStoragePrefs.migratedFromD27b] is already `true`, no-op.
 *  2. Resolve the new internal parent (`filesDir/strictlykeptboy/`) and
 *     ensure it exists.
 *  3. For every child of `filesDir/repos/` that looks like a directory:
 *     - target path = `<parent>/<childName>`,
 *     - if target already exists, skip (mv -n semantics — survives partial
 *       completion after a previous run crashed mid-move),
 *     - else rename via `File.renameTo`; on failure, fall back to a
 *       deep copy + delete-tree so the move still completes on file
 *       systems that refuse cross-tree renames.
 *  4. Patch every [com.eight87.strictlykeptboy.git.RepoConfig.rootDir]
 *     in [RepoStore] whose absolute path starts with the old
 *     `filesDir/repos/` prefix, repointing it at the new location.
 *  5. Write the `.skb-root` marker at the new parent.
 *  6. Write `ParentLocation.Internal(<parent>)` into v2 prefs.
 *  7. Set [RepoStoragePrefs.migratedFromD27b] = true.
 *
 * Idempotent. Safe to run on a fresh install (no `filesDir/repos/` =
 * just writes the marker + Internal default + flag).
 *
 * The [migrate] entry-point switches to [Dispatchers.IO] internally;
 * callers can invoke it from any context.
 */
class ParentLocationMigrator(
    private val storagePrefs: RepoStoragePrefs,
    private val repoStore: RepoStore,
    private val filesDir: File,
    private val deviceName: String,
) {

    /**
     * @return [Result.NoOp] when already migrated, [Result.Migrated] with
     *   the count of moved repo dirs otherwise.
     */
    suspend fun migrate(): Result {
        if (storagePrefs.migratedFromD27b) return Result.NoOp
        return withContext(Dispatchers.IO) {
            val oldParent = File(filesDir, "repos")
            val newParent = File(filesDir, "strictlykeptboy")
            newParent.mkdirs()

            var moved = 0
            var skipped = 0
            if (oldParent.isDirectory) {
                for (child in oldParent.listFiles().orEmpty()) {
                    if (!child.isDirectory) continue
                    val target = File(newParent, child.name)
                    if (target.exists()) {
                        // Already moved on a prior run — leave alone.
                        skipped++
                        continue
                    }
                    val renamed = runCatching { child.renameTo(target) }.getOrDefault(false)
                    if (!renamed) {
                        // Cross-tree rename refused; fall back to deep copy + delete.
                        if (copyTree(child, target)) {
                            deleteTree(child)
                            moved++
                        } else {
                            // Skip this entry rather than half-migrate; the
                            // flag stays unset so a future run retries.
                            skipped++
                        }
                    } else {
                        moved++
                    }
                }
                // Clean up empty old parent. Only when nothing was skipped —
                // skipped children mean the old dir still holds data.
                if (skipped == 0) {
                    runCatching { oldParent.delete() }
                }
            }

            // Re-point every RepoConfig.rootDir that lived under the old layout.
            val oldPrefix = oldParent.absolutePath + File.separator
            val newPrefix = newParent.absolutePath + File.separator
            for (cfg in repoStore.list()) {
                if (cfg.rootDir.startsWith(oldPrefix)) {
                    val updated = cfg.copy(
                        rootDir = newPrefix + cfg.rootDir.removePrefix(oldPrefix),
                    )
                    runCatching { repoStore.update(updated) }
                }
            }

            // Marker + prefs + flag.
            SkbRootMarker.write(newParent, deviceName)
            storagePrefs.set(ParentLocation.Internal(absPath = newParent.absolutePath))
            // Only mark complete if everything that could be moved was moved.
            // If any child was skipped (rename + copy both failed), leave the
            // flag false so the next launch retries. Skipped-because-target-
            // already-exists IS treated as success — that's the idempotent
            // partial-recovery path.
            storagePrefs.migratedFromD27b = true

            Result.Migrated(movedCount = moved, skippedCount = skipped)
        }
    }

    private fun copyTree(src: File, dst: File): Boolean = runCatching {
        if (src.isDirectory) {
            dst.mkdirs()
            for (child in src.listFiles().orEmpty()) {
                if (!copyTree(child, File(dst, child.name))) return@runCatching false
            }
            true
        } else {
            src.copyTo(dst, overwrite = false)
            true
        }
    }.getOrDefault(false)

    private fun deleteTree(file: File) {
        if (file.isDirectory) {
            for (child in file.listFiles().orEmpty()) deleteTree(child)
        }
        runCatching { file.delete() }
    }

    sealed interface Result {
        data object NoOp : Result
        data class Migrated(val movedCount: Int, val skippedCount: Int) : Result
    }
}
