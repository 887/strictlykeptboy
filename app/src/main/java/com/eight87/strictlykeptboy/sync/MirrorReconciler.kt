package com.eight87.strictlykeptboy.sync

import android.net.Uri
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.prefs.MirrorLocation
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs
import com.eight87.strictlykeptboy.prefs.SafTreeUriResolver
import org.eclipse.jgit.api.Git
import java.io.File

/**
 * Round 2.7.C.1 — keep every repo's `"mirror"` remote in sync with the
 * app-wide [RepoStoragePrefs.location].
 *
 * Run before each sync pass (`MirrorReconciler.reconcile(...)`), or in bulk
 * after the user picks a folder outside the wizard (`applyToAll()` — see
 * 2.7.D.1). The bare repo at `<resolvedPath>/<repoId>.git` is created on
 * demand by [ensureBareMirror] before the first push lands.
 *
 * **Failure model.** If [SafTreeUriResolver.resolveRealPath] returns `null`
 * (non-primary volume, cloud provider, etc.) we skip the repo entirely
 * rather than write a broken `file://` URL — a broken remote would surface
 * as a recurring push failure every sync. The caller is expected to have
 * already validated the URI in the SAF picker.
 *
 * Note: this class is stateless — pass [repoStore] + [storagePrefs] + a
 * [resolveTreeUri] adapter (so tests can short-circuit the
 * DocumentsContract dependency).
 */
class MirrorReconciler(
    private val repoStore: RepoStore,
    private val storagePrefs: RepoStoragePrefs,
    /**
     * Maps a SAF tree URI string to a real on-disk path. Production code
     * passes `SafTreeUriResolver::resolveRealPath` after parsing via
     * `Uri.parse(...)`; tests pass an identity lambda to skip
     * DocumentsContract entirely.
     */
    private val resolveTreeUri: (String) -> String? = { stringUri ->
        runCatching { SafTreeUriResolver.resolveRealPath(Uri.parse(stringUri)) }.getOrNull()
    },
) {

    /** Stable name of the mirror remote on every repo. */
    val mirrorName: RemoteName = RemoteName(MIRROR_REMOTE_NAME)

    /**
     * Ensure the given repo carries a `mirror` remote pointing at the
     * currently-configured [RepoStoragePrefs.location]. No-op when:
     *  - the location is [MirrorLocation.None]
     *  - the mirror remote already exists
     *  - the tree URI can't be resolved to a real path (non-primary volume)
     *
     * Idempotent.
     */
    suspend fun reconcile(repoId: String) {
        val external = storagePrefs.location as? MirrorLocation.External ?: return
        val cfg = repoStore.get(repoId) ?: return
        if (cfg.remotes.any { it.name == mirrorName }) return
        val base = resolveTreeUri(external.treeUri) ?: return
        val bare = bareFileFor(base, repoId)
        ensureBareMirror(bare)
        val binding = RemoteBinding(
            name = mirrorName,
            url = bare.toURI().toString(),
            transport = Transport.File,
            authMethod = AuthMethod.None,
        )
        repoStore.addRemote(repoId, binding)
    }

    /**
     * Round 2.7.D.1 — bulk-apply the current mirror location to every
     * configured repo. Called from the BackupLocationCategory "Apply to
     * existing repos?" Yes confirmation.
     *
     * @return number of repos that gained a mirror remote (some may have
     *   already had one).
     */
    suspend fun applyToAll(): Int {
        if (storagePrefs.location !is MirrorLocation.External) return 0
        var added = 0
        for (cfg in repoStore.list()) {
            if (cfg.remotes.any { it.name == mirrorName }) continue
            val before = repoStore.get(cfg.repoId)?.remotes?.size ?: 0
            reconcile(cfg.repoId)
            val after = repoStore.get(cfg.repoId)?.remotes?.size ?: 0
            if (after > before) added++
        }
        return added
    }

    /**
     * Snapshot of which configured repos currently lack a mirror remote.
     * Used by the "(N repos)" copy in the retroactive-apply prompt
     * (2.7.D.1).
     */
    fun reposMissingMirror(): List<RepoConfig> {
        if (storagePrefs.location !is MirrorLocation.External) return emptyList()
        return repoStore.list().filter { cfg -> cfg.remotes.none { it.name == mirrorName } }
    }

    companion object {
        const val MIRROR_REMOTE_NAME: String = "mirror"

        /**
         * Round 2.7.C.2 — create a bare repo at [path] if it doesn't
         * already exist. Idempotent: returning successfully when [path]
         * is already a bare git repo.
         *
         * The bare repo's initial branch is `main` so a fresh-empty
         * mirror accepts the first push from a working repo without
         * requiring `--force` or branch-config trickery.
         */
        fun ensureBareMirror(path: File) {
            if (path.exists() && File(path, "HEAD").isFile) return
            path.parentFile?.mkdirs()
            Git.init()
                .setBare(true)
                .setDirectory(path)
                .setInitialBranch("main")
                .call()
                .close()
        }

        /** Convention: bare mirrors live at `<base>/<repoId>.git`. */
        fun bareFileFor(basePath: String, repoId: String): File =
            File(basePath, "$repoId.git")
    }
}
