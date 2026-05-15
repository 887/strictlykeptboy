package com.eight87.strictlykeptboy.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.prefs.ParentLocation
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Round 2.17.A.8 — `ParentReconciler` covers:
 *  - reconcile on Internal parent — adopts unregistered repo dirs
 *  - reconcile when no parent configured — empty list
 *  - reconcile when parent dir missing — empty list
 *  - reconcile skips already-registered repos
 *  - pruneStaleMirrorRemotes drops the legacy `mirror` remote
 *
 * Previously [`MirrorReconcilerTest`] (Round 2.7.C.4); renamed via
 * `git mv` per D-2.17.l ("refactor, don't rewrite").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ParentReconcilerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newRepoStore(name: String = "rs"): RepoStore {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences("rs-$name", Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        return RepoStore.openForTest(sp)
    }

    private fun newStoragePrefs(name: String = "rsp"): RepoStoragePrefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences("rsp-$name", Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        return RepoStoragePrefs.openForTest(sp)
    }

    private fun cfg(repoId: String, rootDir: File, remotes: List<RemoteBinding> = emptyList()): RepoConfig =
        RepoConfig(
            repoId = repoId,
            displayName = repoId,
            rootDir = rootDir.absolutePath,
            remotes = remotes,
            primaryRemote = remotes.firstOrNull()?.name,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )

    private fun initGitDir(dir: File) {
        dir.mkdirs()
        Git.init().setDirectory(dir).setInitialBranch("main").call().close()
    }

    @Test fun reconcileAdoptsUnregisteredRepoDirs() = runTest {
        val store = newRepoStore("a")
        val prefs = newStoragePrefs("a")
        val parent = tmp.newFolder("parent-a")
        prefs.set(ParentLocation.Internal(absPath = parent.absolutePath))

        // Two git repos under the parent, neither registered.
        initGitDir(File(parent, "alpha"))
        initGitDir(File(parent, "beta"))
        // A non-git dir under the parent — must NOT be adopted.
        File(parent, "junk").mkdirs()

        val reconciler = ParentReconciler(repoStore = store, storagePrefs = prefs)
        val adopted = reconciler.reconcile().map { it.repoId }.toSet()
        assertEquals(setOf("alpha", "beta"), adopted)
    }

    @Test fun reconcileAdoptsUnregisteredRepoDirsOnExternalParent() = runTest {
        // H.3(b) — parity coverage for ParentLocation.External. The
        // reconciler resolves location.workingDir() identically for
        // both variants, but pinning the External code path keeps a
        // regression here visible if that ever diverges.
        val store = newRepoStore("a-ext")
        val prefs = newStoragePrefs("a-ext")
        val parent = tmp.newFolder("parent-a-ext")
        prefs.set(
            ParentLocation.External(
                treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
                label = "Documents",
                cachedRealPath = parent.absolutePath,
            )
        )

        initGitDir(File(parent, "alpha-ext"))
        initGitDir(File(parent, "beta-ext"))
        File(parent, "junk").mkdirs()

        val reconciler = ParentReconciler(repoStore = store, storagePrefs = prefs)
        val adopted = reconciler.reconcile().map { it.repoId }.toSet()
        assertEquals(setOf("alpha-ext", "beta-ext"), adopted)
    }

    @Test fun reconcileSkipsAlreadyRegisteredRepos() = runTest {
        val store = newRepoStore("b")
        val prefs = newStoragePrefs("b")
        val parent = tmp.newFolder("parent-b")
        prefs.set(ParentLocation.Internal(absPath = parent.absolutePath))

        val regDir = File(parent, "already")
        initGitDir(regDir)
        store.add(cfg("already", regDir))

        initGitDir(File(parent, "new-one"))

        val reconciler = ParentReconciler(store, prefs)
        val adopted = reconciler.reconcile().map { it.repoId }
        assertEquals(listOf("new-one"), adopted)
    }

    @Test fun reconcileReturnsEmptyWhenNoParentConfigured() = runTest {
        val store = newRepoStore("c")
        val prefs = newStoragePrefs("c")
        // prefs.location == null
        val reconciler = ParentReconciler(store, prefs)
        assertTrue(reconciler.reconcile().isEmpty())
    }

    @Test fun reconcileReturnsEmptyWhenParentMissing() = runTest {
        val store = newRepoStore("d")
        val prefs = newStoragePrefs("d")
        prefs.set(ParentLocation.Internal(absPath = "/nonexistent-${System.nanoTime()}"))
        val reconciler = ParentReconciler(store, prefs)
        assertTrue(reconciler.reconcile().isEmpty())
    }

    @Test fun pruneStaleMirrorRemoteDropsLegacyRemote() = runTest {
        val store = newRepoStore("e")
        val prefs = newStoragePrefs("e")
        val workDir = tmp.newFolder("repo-e")
        val mirrorBinding = RemoteBinding(
            name = RemoteName(ParentReconciler.MIRROR_REMOTE_NAME),
            url = "file:///tmp/legacy.git",
            transport = Transport.File,
            authMethod = AuthMethod.None,
        )
        val cfg = cfg("repo-e", workDir, remotes = listOf(mirrorBinding))
        store.add(cfg)
        assertTrue(
            "precondition: mirror remote registered",
            store.get("repo-e")!!.remotes.any { it.name.value == "mirror" },
        )
        val reconciler = ParentReconciler(store, prefs)
        val pruned = reconciler.pruneStaleMirrorRemotes()
        assertEquals(1, pruned)
        assertTrue(
            "mirror remote must be gone after prune",
            store.get("repo-e")!!.remotes.none { it.name.value == "mirror" },
        )
        // Idempotent.
        assertEquals(0, reconciler.pruneStaleMirrorRemotes())
    }
}
