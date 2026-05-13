package com.eight87.strictlykeptboy.git

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.5.A.4 — covers:
 *   - one-time migration from legacy `unifiedView` → per-repo flags;
 *   - the helper setters round-trip + persist;
 *   - the new `RepoConfig` flag defaults survive JSON round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RepoOverlayPrefsMigrationTest {

    private fun freshStore(name: String): RepoStore {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        return RepoStore.openForTest(prefs)
    }

    private fun cfg(id: String) = RepoConfig(
        repoId = id,
        displayName = id,
        rootDir = "/tmp/$id",
        authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
    )

    @Test fun migration_unified_true_sets_all_show_and_active_draw() = runTest {
        val store = freshStore("migration_unified_true")
        store.add(cfg("a"))
        store.add(cfg("b"))
        store.add(cfg("c"))

        store.migrateUnifiedViewV25(unifiedView = true, activeRepoId = "b")

        val list = store.list()
        assertTrue(list.all { it.showOnSchedule })
        // With multiple repos under unified=true, only the active draws tasks.
        assertEquals(setOf("b"), list.filter { it.drawTasksFrom }.map { it.repoId }.toSet())
    }

    @Test fun migration_unified_false_only_active_repo_flagged() = runTest {
        val store = freshStore("migration_unified_false")
        store.add(cfg("a"))
        store.add(cfg("b"))

        store.migrateUnifiedViewV25(unifiedView = false, activeRepoId = "b")

        val list = store.list()
        val showIds = list.filter { it.showOnSchedule }.map { it.repoId }.toSet()
        val drawIds = list.filter { it.drawTasksFrom }.map { it.repoId }.toSet()
        assertEquals(setOf("b"), showIds)
        assertEquals(setOf("b"), drawIds)
    }

    @Test fun migration_idempotent_skips_on_second_call() = runTest {
        val store = freshStore("migration_idempotent")
        store.add(cfg("a"))

        store.migrateUnifiedViewV25(unifiedView = true, activeRepoId = "a")
        store.setShowOnSchedule("a", false)
        // Second migration call must not flip back.
        store.migrateUnifiedViewV25(unifiedView = true, activeRepoId = "a")
        assertFalse(store.get("a")!!.showOnSchedule)
    }

    @Test fun setters_roundtrip() = runTest {
        val store = freshStore("setters_roundtrip")
        store.add(cfg("a"))

        store.setShowOnSchedule("a", false)
        store.setDrawTasksFrom("a", true)
        assertFalse(store.get("a")!!.showOnSchedule)
        assertTrue(store.get("a")!!.drawTasksFrom)
    }

    @Test fun config_serialization_roundtrip_persists_flags() = runTest {
        val store = freshStore("serialization_roundtrip")
        store.add(cfg("a").copy(showOnSchedule = false, drawTasksFrom = true))

        // Re-open the same prefs file.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("serialization_roundtrip", Context.MODE_PRIVATE)
        val reopened = RepoStore.openForTest(prefs)
        val reloaded = reopened.get("a")!!
        assertFalse(reloaded.showOnSchedule)
        assertTrue(reloaded.drawTasksFrom)
    }

    @Test fun config_defaults_when_missing_keys() = runTest {
        val store = freshStore("config_defaults")
        store.add(cfg("a"))
        // Defaults: showOnSchedule = true, drawTasksFrom = false.
        assertTrue(store.get("a")!!.showOnSchedule)
        assertFalse(store.get("a")!!.drawTasksFrom)
    }
}
