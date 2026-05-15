package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs
import java.io.File

/**
 * Round 2.17.A.8 — reconciler over the *parent folder* (D-2.17.a).
 *
 * Replaces the Round 2.7.C `MirrorReconciler` (renamed via `git mv` —
 * see the corresponding `ParentReconcilerTest` for the surface it
 * now defines). The previous file-mirror dance is gone:
 *  - the parent IS the working tree location, so there is no separate
 *    bare-mirror to manipulate per repo,
 *  - the `"mirror"` remote that 2.7.C added to every repo is now dead
 *    weight; [pruneStaleMirrorRemotes] strips it on the first reconcile
 *    after migration so the post-2.17 push fan-out doesn't try to
 *    publish to it.
 *
 * The new surface answers one question: *which repo directories exist
 * under the configured parent that aren't already registered in
 * [RepoStore]?* The Phase E adoption flow consumes the result.
 */
class ParentReconciler(
    private val repoStore: RepoStore,
    private val storagePrefs: RepoStoragePrefs,
) {

    /**
     * Snapshot of an adoptable repo: a directory under the parent that
     * looks like a git working tree but has no [RepoStore] entry yet.
     * The display name is the directory name; Phase E.4 overlays
     * `repo.toml` when present.
     */
    data class AdoptedRepo(
        val repoId: String,
        val rootDir: File,
    )

    /** Stable name of the now-legacy mirror remote. */
    val mirrorName: RemoteName = RemoteName(MIRROR_REMOTE_NAME)

    /**
     * Scan the configured parent and return every direct child that is
     * a git repo but isn't in [RepoStore] yet. Returns an empty list
     * when no parent is configured or the parent's working dir doesn't
     * resolve (SAF revoked, path missing, etc.).
     *
     * Detection: a child is "a git repo" iff `<child>/.git` exists
     * (regular or bare-redirect file, both produced by JGit/`git init`).
     */
    suspend fun reconcile(): List<AdoptedRepo> {
        val parent = storagePrefs.location?.workingDir() ?: return emptyList()
        return scan(parent)
    }

    /**
     * Scan a specific directory rather than the configured parent —
     * used by Phase E.4's "Adopt existing folder" flow before the user
     * commits to switching the parent.
     */
    suspend fun reconcileExternal(parent: File): List<AdoptedRepo> = scan(parent)

    private fun scan(parent: File): List<AdoptedRepo> {
        if (!parent.isDirectory) return emptyList()
        val registered = repoStore.list().asSequence()
            .map { File(it.rootDir).absoluteFile }
            .toSet()
        return parent.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .filter { File(it, ".git").exists() }
            .filter { it.absoluteFile !in registered }
            .map { AdoptedRepo(repoId = it.name, rootDir = it) }
            .toList()
    }

    /**
     * Round 2.17.A.8 — one-shot helper invoked during migration to
     * drop the dead `"mirror"` remote from every registered repo.
     * Idempotent: a repo with no `mirror` remote is left alone.
     *
     * @return number of repos that had a mirror remote pruned.
     */
    suspend fun pruneStaleMirrorRemotes(): Int {
        var pruned = 0
        for (cfg in repoStore.list()) {
            if (cfg.remotes.any { it.name == mirrorName }) {
                runCatching { repoStore.removeRemote(cfg.repoId, mirrorName) }
                pruned++
            }
        }
        return pruned
    }

    companion object {
        /** Name of the legacy 2.7.C `"mirror"` remote — retained for prune. */
        const val MIRROR_REMOTE_NAME: String = "mirror"
    }
}
