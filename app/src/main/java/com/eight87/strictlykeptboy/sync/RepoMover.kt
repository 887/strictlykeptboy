package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.prefs.ParentLocation
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs
import com.eight87.strictlykeptboy.prefs.SkbRootMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Round 2.17 Phase E.5 — move every registered repo from the current
 * parent to a new parent.
 *
 * Contract:
 *  1. The caller has already determined `oldParent` and `newParent`
 *     and verified that the new parent's `.skb-root` marker is in place
 *     (or that we should write one if absent — `mkdirs` happens here).
 *  2. For every repo whose `rootDir` lives under `oldParent`:
 *       - copy `<oldParent>/<repoId>/` → `<newParent>/<repoId>/`
 *       - update `RepoConfig.rootDir` in [RepoStore]
 *       - delete `<oldParent>/<repoId>/`
 *       - drop the open `GitRepoRegistry` handle so the next access
 *         re-opens at the new path.
 *  3. The `.skb-root` marker at `oldParent` is left in place — Restore
 *     can clean it up later. The `newParent` marker is preserved if
 *     present; we only write a fresh marker when none exists.
 *  4. Progress is published via [progress]; the caller can observe and
 *     show a dialog. Cancellation is cooperative — calling [cancel]
 *     after the next repo finishes copying triggers the rollback path:
 *     prefs flip back to `oldParentLocation`, copied-but-not-yet-deleted
 *     repos at `newParent` are left in place (Restore cleans up).
 *
 * Marker rule: the source `.skb-root` is NOT carried into per-repo
 * copies — it lives at the parent root, not inside individual repos.
 * What "carried over" means in the test is that the marker at the
 * *destination parent* exists after the move; the move-job calls
 * [ensureMarker] on `newParent` before copying.
 */
class RepoMover(
    private val repoStore: RepoStore,
    private val storagePrefs: RepoStoragePrefs,
    private val deviceName: String = "",
) {

    /**
     * Discrete states emitted on [progress]. Compose dialog hosts read
     * the latest value and render accordingly.
     */
    sealed interface Progress {
        data object Idle : Progress
        data class Running(
            val currentIndex: Int,
            val totalCount: Int,
            val currentRepoLabel: String,
        ) : Progress
        data class Done(val movedCount: Int) : Progress
        data class Cancelled(val movedBeforeCancel: Int) : Progress
        data class Failed(val message: String, val movedBeforeFail: Int) : Progress
    }

    private val _progress: MutableStateFlow<Progress> = MutableStateFlow(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    @Volatile
    private var cancelRequested: Boolean = false

    /** Cooperative cancel — checked between repo copies. */
    fun cancel() {
        cancelRequested = true
    }

    /**
     * Move every repo currently registered under [oldParent] to
     * [newParent]. Persists [newParentLocation] up front so a process
     * crash mid-move still leaves the prefs pointed at the new parent
     * (where the live data ends up); on cancel we revert.
     *
     * @return number of repos moved (or partially moved at cancel/fail).
     */
    suspend fun move(
        oldParent: File,
        newParent: File,
        oldParentLocation: ParentLocation?,
        newParentLocation: ParentLocation,
    ): Int = withContext(Dispatchers.IO) {
        cancelRequested = false
        var moved = 0
        try {
            newParent.mkdirs()
            ensureMarker(newParent)
            // Persist the new parent up front so the UI's
            // ParentLocationGate flips immediately; we revert on cancel.
            storagePrefs.set(newParentLocation)

            val toMove = repoStore.list().filter { cfg ->
                val cur = File(cfg.rootDir).absoluteFile
                cur.startsWith(oldParent.absoluteFile)
            }
            val total = toMove.size

            for ((idx, cfg) in toMove.withIndex()) {
                if (cancelRequested) {
                    rollback(oldParentLocation)
                    _progress.value = Progress.Cancelled(movedBeforeCancel = moved)
                    return@withContext moved
                }
                _progress.value = Progress.Running(
                    currentIndex = idx,
                    totalCount = total,
                    currentRepoLabel = cfg.displayName,
                )
                val src = File(cfg.rootDir).absoluteFile
                val dst = File(newParent, src.name).absoluteFile
                if (src.absolutePath == dst.absolutePath) {
                    // Same location — nothing to do, just bump the count.
                    moved++
                    continue
                }
                // Drop any open JGit handle so it doesn't hold a fd on
                // files we're about to delete.
                runCatching { GitRepoRegistry.evict(cfg.repoId) }

                copyRecursive(src, dst)
                repoStore.update(cfg.copy(rootDir = dst.absolutePath))
                deleteRecursive(src)
                moved++
            }
            _progress.value = Progress.Done(movedCount = moved)
            moved
        } catch (t: Throwable) {
            rollback(oldParentLocation)
            _progress.value = Progress.Failed(
                message = t.message ?: t::class.simpleName.orEmpty(),
                movedBeforeFail = moved,
            )
            moved
        }
    }

    private fun rollback(oldParentLocation: ParentLocation?) {
        if (oldParentLocation != null) {
            runCatching { storagePrefs.set(oldParentLocation) }
        }
    }

    private fun ensureMarker(parent: File) {
        if (!SkbRootMarker.isSkbRoot(parent)) {
            runCatching { SkbRootMarker.write(parent = parent, deviceName = deviceName) }
        }
    }

    private fun copyRecursive(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles().orEmpty().forEach { child ->
                copyRecursive(child, File(dst, child.name))
            }
        } else {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
    }

    private fun deleteRecursive(target: File) {
        if (!target.exists()) return
        if (target.isDirectory) {
            target.listFiles().orEmpty().forEach { deleteRecursive(it) }
        }
        target.delete()
    }
}
