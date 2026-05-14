package com.eight87.strictlykeptboy.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.PushResult
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.prefs.MirrorLocation
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
 * Round 2.7.C.4 + 2.7.D.3 — MirrorReconciler integration:
 *   - first sync after the mirror is set up creates the bare repo
 *   - push to mirror lands the working repo's HEAD
 *   - applyToAll() bulk-adds the mirror remote to every existing repo
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class MirrorReconcilerTest {

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

    private fun cfg(repoId: String, rootDir: File): RepoConfig =
        RepoConfig(
            repoId = repoId,
            displayName = repoId,
            rootDir = rootDir.absolutePath,
            remotes = emptyList(),
            primaryRemote = null,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )

    @Test fun reconcileAddsMirrorRemoteAndCreatesBareDir() = runTest {
        val store = newRepoStore("a")
        val prefs = newStoragePrefs("a")
        val mirrorBase = tmp.newFolder("mirror-base")
        prefs.set(MirrorLocation.External(treeUri = "tree://primary/x", label = "x"))
        // Inject a test resolver that bypasses DocumentsContract — returns
        // the real tmp path.
        val reconciler = MirrorReconciler(
            repoStore = store,
            storagePrefs = prefs,
            resolveTreeUri = { mirrorBase.absolutePath },
        )

        val workDir = tmp.newFolder("repo-a")
        store.add(cfg("repo-a", workDir))

        reconciler.reconcile("repo-a")
        val updated = store.get("repo-a")!!
        val mirror = updated.remotes.first { it.name.value == "mirror" }
        val expectedBare = File(mirrorBase, "repo-a.git")
        assertEquals(expectedBare.toURI().toString(), mirror.url)
        assertTrue("bare repo HEAD must exist", File(expectedBare, "HEAD").isFile)
    }

    @Test fun reconcileIsIdempotent() = runTest {
        val store = newRepoStore("b")
        val prefs = newStoragePrefs("b")
        val mirrorBase = tmp.newFolder("mirror-b")
        prefs.set(MirrorLocation.External(treeUri = "tree://primary/y", label = "y"))
        val reconciler = MirrorReconciler(
            repoStore = store, storagePrefs = prefs,
            resolveTreeUri = { mirrorBase.absolutePath },
        )
        val workDir = tmp.newFolder("repo-b")
        store.add(cfg("repo-b", workDir))
        reconciler.reconcile("repo-b")
        val firstSize = store.get("repo-b")!!.remotes.size
        reconciler.reconcile("repo-b")
        assertEquals(firstSize, store.get("repo-b")!!.remotes.size)
    }

    @Test fun reconcileSkipsWhenLocationIsNone() = runTest {
        val store = newRepoStore("c")
        val prefs = newStoragePrefs("c")
        val reconciler = MirrorReconciler(store, prefs, resolveTreeUri = { error("unused") })
        val workDir = tmp.newFolder("repo-c")
        store.add(cfg("repo-c", workDir))
        reconciler.reconcile("repo-c")
        assertTrue(store.get("repo-c")!!.remotes.isEmpty())
    }

    @Test fun reconcileSkipsWhenResolverReturnsNull() = runTest {
        val store = newRepoStore("d")
        val prefs = newStoragePrefs("d")
        prefs.set(MirrorLocation.External(treeUri = "tree://sdcard/z", label = "z"))
        val reconciler = MirrorReconciler(store, prefs, resolveTreeUri = { null })
        val workDir = tmp.newFolder("repo-d")
        store.add(cfg("repo-d", workDir))
        reconciler.reconcile("repo-d")
        assertTrue(store.get("repo-d")!!.remotes.isEmpty())
    }

    @Test fun pushToMirrorLandsCommitHead() = runTest {
        // Scaffold a working repo + reconcile + push to the mirror; verify
        // the bare repo's HEAD matches the working repo's HEAD.
        val store = newRepoStore("e")
        val prefs = newStoragePrefs("e")
        val mirrorBase = tmp.newFolder("mirror-e")
        prefs.set(MirrorLocation.External(treeUri = "tree://primary/e", label = "e"))
        val reconciler = MirrorReconciler(store, prefs, resolveTreeUri = { mirrorBase.absolutePath })

        val workDir = tmp.newFolder("repo-e")
        val repo = GitRepo.initLocalOnly(
            rootDir = workDir,
            repoId = "repo-e",
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )
        store.add(cfg("repo-e", workDir))
        File(workDir, "hello.md").writeText("hi\n")
        repo.commitAll("first")

        reconciler.reconcile("repo-e")
        val mirrorBinding = store.get("repo-e")!!.remotes.first { it.name.value == "mirror" }
        repo.addRemote(mirrorBinding)

        val pushResult = repo.push(mirrorBinding.name)
        assertEquals(PushResult.Success, pushResult)

        val bare = File(mirrorBase, "repo-e.git")
        Git.open(bare).use { g ->
            val log = g.log().setMaxCount(10).call().toList()
            assertEquals("expected the seed commit in the bare mirror", 1, log.size)
        }
    }

    @Test fun applyToAllAddsMirrorToEveryRepo() = runTest {
        val store = newRepoStore("f")
        val prefs = newStoragePrefs("f")
        val mirrorBase = tmp.newFolder("mirror-f")
        for (id in listOf("r1", "r2", "r3")) {
            store.add(cfg(id, tmp.newFolder(id)))
        }
        prefs.set(MirrorLocation.External(treeUri = "tree://primary/f", label = "f"))
        val reconciler = MirrorReconciler(store, prefs, resolveTreeUri = { mirrorBase.absolutePath })

        val added = reconciler.applyToAll()
        assertEquals(3, added)
        for (id in listOf("r1", "r2", "r3")) {
            val remotes = store.get(id)!!.remotes
            assertTrue("$id missing mirror", remotes.any { it.name.value == "mirror" })
            val mirror = remotes.first { it.name.value == "mirror" }
            val expectedBare = File(mirrorBase, "$id.git")
            assertEquals(expectedBare.toURI().toString(), mirror.url)
        }
    }

    @Test fun reposMissingMirrorEnumeratesCorrectly() = runTest {
        val store = newRepoStore("g")
        val prefs = newStoragePrefs("g")
        val mirrorBase = tmp.newFolder("mirror-g")
        store.add(cfg("r1", tmp.newFolder("g-r1")))
        store.add(cfg("r2", tmp.newFolder("g-r2")))
        prefs.set(MirrorLocation.External(treeUri = "tree://primary/g", label = "g"))
        val reconciler = MirrorReconciler(store, prefs, resolveTreeUri = { mirrorBase.absolutePath })

        assertEquals(2, reconciler.reposMissingMirror().size)
        reconciler.reconcile("r1")
        assertEquals(1, reconciler.reposMissingMirror().size)
    }
}
