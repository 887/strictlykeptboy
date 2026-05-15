package com.eight87.strictlykeptboy.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.prefs.ParentLocation
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs
import com.eight87.strictlykeptboy.prefs.SkbRootMarker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Round 2.17 Phase E.6 — `RepoMover` unit test.
 *
 * Pre-seeds two repos under an old parent + marker, calls `move()`,
 * asserts that:
 *  - both repos' `rootDir` now point under the new parent,
 *  - the old per-repo dirs are gone,
 *  - the new per-repo dirs carry their original content,
 *  - the new parent carries a `.skb-root` marker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RepoMoverTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newRepoStore(name: String = "rm"): RepoStore {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences("rm-rs-$name", Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        return RepoStore.openForTest(sp)
    }

    private fun newStoragePrefs(name: String = "rm"): RepoStoragePrefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences("rm-rsp-$name", Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        return RepoStoragePrefs.openForTest(sp)
    }

    private fun seedRepoDir(parent: File, repoId: String, fileContent: String): File {
        val dir = File(parent, repoId).apply { mkdirs() }
        File(dir, ".git").apply { mkdirs() }
        File(dir, "calendars").apply { mkdirs() }
        File(dir, "calendars/notes.md").writeText(fileContent)
        return dir
    }

    @Test fun movesTwoReposAndUpdatesRootDirs() = runTest {
        val store = newRepoStore("a")
        val prefs = newStoragePrefs("a")
        val oldParent = tmp.newFolder("old-parent")
        val newParent = tmp.newFolder("new-parent")
        SkbRootMarker.write(oldParent, deviceName = "test")

        val alphaDir = seedRepoDir(oldParent, "alpha", "ALPHA")
        val betaDir = seedRepoDir(oldParent, "beta", "BETA")

        val oldLoc = ParentLocation.Internal(absPath = oldParent.absolutePath)
        prefs.set(oldLoc)

        store.add(
            RepoConfig(
                repoId = "alpha",
                displayName = "alpha",
                rootDir = alphaDir.absolutePath,
                remotes = emptyList(),
                primaryRemote = null,
                authorIdentity = AuthorIdentity("me", "me@example.com"),
            ),
        )
        store.add(
            RepoConfig(
                repoId = "beta",
                displayName = "beta",
                rootDir = betaDir.absolutePath,
                remotes = emptyList(),
                primaryRemote = null,
                authorIdentity = AuthorIdentity("me", "me@example.com"),
            ),
        )

        val mover = RepoMover(repoStore = store, storagePrefs = prefs, deviceName = "test")
        val moved = mover.move(
            oldParent = oldParent,
            newParent = newParent,
            oldParentLocation = oldLoc,
            newParentLocation = ParentLocation.Internal(absPath = newParent.absolutePath),
        )

        assertEquals(2, moved)
        // RepoConfig.rootDir updated for both.
        val cfgs = store.list().associateBy { it.repoId }
        assertEquals(File(newParent, "alpha").absolutePath, cfgs.getValue("alpha").rootDir)
        assertEquals(File(newParent, "beta").absolutePath, cfgs.getValue("beta").rootDir)
        // Old paths gone.
        assertFalse(File(oldParent, "alpha").exists())
        assertFalse(File(oldParent, "beta").exists())
        // New paths populated.
        assertTrue(File(newParent, "alpha/calendars/notes.md").isFile)
        assertEquals("ALPHA", File(newParent, "alpha/calendars/notes.md").readText())
        assertTrue(File(newParent, "beta/calendars/notes.md").isFile)
        // Marker carried over (written at the new parent root).
        assertTrue(SkbRootMarker.isSkbRoot(newParent))
        // Prefs flipped.
        assertEquals(newParent.absolutePath, (prefs.location as ParentLocation.Internal).absPath)
    }

    @Test fun preservesExistingMarkerAtNewParent() = runTest {
        val store = newRepoStore("b")
        val prefs = newStoragePrefs("b")
        val oldParent = tmp.newFolder("old-parent-b")
        val newParent = tmp.newFolder("new-parent-b")
        SkbRootMarker.write(oldParent, deviceName = "test")
        // Pre-existing marker at new parent.
        SkbRootMarker.write(newParent, deviceName = "preexisting", nowIso = "2020-01-01T00:00:00Z")
        val originalMarker = SkbRootMarker.read(newParent)!!

        seedRepoDir(oldParent, "solo", "SOLO")
        prefs.set(ParentLocation.Internal(absPath = oldParent.absolutePath))
        store.add(
            RepoConfig(
                repoId = "solo",
                displayName = "solo",
                rootDir = File(oldParent, "solo").absolutePath,
                remotes = emptyList(),
                primaryRemote = null,
                authorIdentity = AuthorIdentity("me", "me@example.com"),
            ),
        )
        val mover = RepoMover(repoStore = store, storagePrefs = prefs, deviceName = "test")
        mover.move(
            oldParent = oldParent,
            newParent = newParent,
            oldParentLocation = ParentLocation.Internal(absPath = oldParent.absolutePath),
            newParentLocation = ParentLocation.Internal(absPath = newParent.absolutePath),
        )
        // Marker untouched (no double-write).
        val after = SkbRootMarker.read(newParent)!!
        assertEquals(originalMarker.createdAt, after.createdAt)
    }
}
