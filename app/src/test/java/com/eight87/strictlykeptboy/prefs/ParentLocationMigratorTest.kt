package com.eight87.strictlykeptboy.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ParentLocationMigratorTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun freshPrefs(name: String): RepoStoragePrefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        return RepoStoragePrefs.openForTest(sp)
    }

    private fun freshRepoStore(name: String): RepoStore {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        return RepoStore.openForTest(sp)
    }

    private fun seedOldRepoDir(filesDir: File, name: String): File {
        val dir = File(filesDir, "repos/$name")
        dir.mkdirs()
        File(dir, "hello.md").writeText("hi\n")
        File(dir, ".git").mkdirs()
        return dir
    }

    @Test fun migrates_filesDir_repos_to_parent_once() = runTest {
        val filesDir = tmp.newFolder("filesDir-a")
        val prefs = freshPrefs("pls-a")
        val store = freshRepoStore("rs-a")

        val aOld = seedOldRepoDir(filesDir, "a")
        val bOld = seedOldRepoDir(filesDir, "b")
        // Register `a` in the store at its old path so we can verify rootDir gets re-pointed.
        store.add(
            RepoConfig(
                repoId = "a",
                displayName = "a",
                rootDir = aOld.absolutePath,
                authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
            ),
        )

        val migrator = ParentLocationMigrator(
            storagePrefs = prefs,
            repoStore = store,
            filesDir = filesDir,
            deviceName = "TestDevice",
        )

        val result = migrator.migrate()
        assertTrue("result must be Migrated", result is ParentLocationMigrator.Result.Migrated)
        (result as ParentLocationMigrator.Result.Migrated)
        assertEquals(2, result.movedCount)

        val newParent = File(filesDir, "strictlykeptboy")
        assertTrue(File(newParent, "a").isDirectory)
        assertTrue(File(newParent, "b").isDirectory)
        assertTrue(File(newParent, "a/hello.md").isFile)
        assertFalse("old repo dir must be gone", aOld.exists())
        assertFalse("old repo dir must be gone", bOld.exists())

        // Marker.
        assertTrue("marker must be written at new parent", SkbRootMarker.isSkbRoot(newParent))
        val marker = SkbRootMarker.read(newParent)!!
        assertEquals("TestDevice", marker.createdBy)

        // Prefs: Internal pointed at the new parent + flag set.
        val loc = prefs.location
        assertTrue(loc is ParentLocation.Internal)
        assertEquals(newParent.absolutePath, (loc as ParentLocation.Internal).absPath)
        assertTrue(prefs.migratedFromD27b)

        // RepoConfig.rootDir re-pointed.
        val updated = store.get("a")!!
        assertEquals(File(newParent, "a").absolutePath, updated.rootDir)
    }

    @Test fun is_idempotent_when_run_twice() = runTest {
        val filesDir = tmp.newFolder("filesDir-b")
        val prefs = freshPrefs("pls-b")
        val store = freshRepoStore("rs-b")

        seedOldRepoDir(filesDir, "only-one")
        val migrator = ParentLocationMigrator(prefs, store, filesDir, deviceName = "X")

        val first = migrator.migrate() as ParentLocationMigrator.Result.Migrated
        assertEquals(1, first.movedCount)

        // Second invocation must be a no-op.
        val second = migrator.migrate()
        assertEquals(ParentLocationMigrator.Result.NoOp, second)

        val newParent = File(filesDir, "strictlykeptboy")
        assertTrue(File(newParent, "only-one").isDirectory)
        assertTrue(prefs.migratedFromD27b)
    }

    @Test fun fresh_install_with_no_old_dir_still_initialises_marker_and_prefs() = runTest {
        val filesDir = tmp.newFolder("filesDir-c")
        val prefs = freshPrefs("pls-c")
        val store = freshRepoStore("rs-c")
        // No filesDir/repos at all.
        val migrator = ParentLocationMigrator(prefs, store, filesDir, deviceName = "Fresh")

        val result = migrator.migrate() as ParentLocationMigrator.Result.Migrated
        assertEquals(0, result.movedCount)
        val newParent = File(filesDir, "strictlykeptboy")
        assertTrue(newParent.isDirectory)
        assertTrue(SkbRootMarker.isSkbRoot(newParent))
        assertTrue(prefs.location is ParentLocation.Internal)
        assertTrue(prefs.migratedFromD27b)
    }

    @Test fun partial_recovery_when_new_target_already_exists() = runTest {
        // Simulate a previous run that moved `a` but crashed before flipping
        // the migrated flag. `a` exists in the new parent already; `b` is
        // still in the old layout. Second run must skip `a` (target
        // already exists) and move `b`, then mark migrated.
        val filesDir = tmp.newFolder("filesDir-d")
        val prefs = freshPrefs("pls-d")
        val store = freshRepoStore("rs-d")
        File(filesDir, "strictlykeptboy/a").mkdirs()
        File(filesDir, "strictlykeptboy/a/hello.md").writeText("hi\n")
        seedOldRepoDir(filesDir, "a") // stale leftover at the old path
        seedOldRepoDir(filesDir, "b")

        val migrator = ParentLocationMigrator(prefs, store, filesDir, deviceName = "Recover")
        val result = migrator.migrate() as ParentLocationMigrator.Result.Migrated
        assertEquals("only b should move; a was already at the new target", 1, result.movedCount)
        assertEquals("a is skipped — target already exists", 1, result.skippedCount)
        assertTrue(prefs.migratedFromD27b)
    }
}
