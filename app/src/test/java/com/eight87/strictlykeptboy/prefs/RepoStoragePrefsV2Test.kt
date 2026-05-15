package com.eight87.strictlykeptboy.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.17.A.7 — `RepoStoragePrefs` v2 schema tests.
 *
 * Names per the plan's "Robolectric tests (concrete names)" list:
 *  - round_trips_internal
 *  - round_trips_external_with_cached_path
 *  - reads_v1_prefs_and_upgrades
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RepoStoragePrefsV2Test {

    private fun freshSharedPrefs(name: String) =
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }

    @Test fun round_trips_internal() {
        val sp = freshSharedPrefs("rsp_v2_internal")
        val p1 = RepoStoragePrefs.openForTest(sp)
        val loc = ParentLocation.Internal(absPath = "/data/user/0/com.eight87.strictlykeptboy/files/strictlykeptboy")
        p1.set(loc)
        assertEquals(loc, p1.location)

        val p2 = RepoStoragePrefs.openForTest(sp)
        assertEquals(loc, p2.location)
    }

    @Test fun round_trips_external_with_cached_path() {
        val sp = freshSharedPrefs("rsp_v2_ext")
        val p1 = RepoStoragePrefs.openForTest(sp)
        val loc = ParentLocation.External(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2Fstrictlykeptboy",
            label = "Documents",
            cachedRealPath = "/storage/emulated/0/Documents/strictlykeptboy",
        )
        p1.set(loc)
        assertEquals(loc, p1.location)

        val p2 = RepoStoragePrefs.openForTest(sp)
        assertEquals(loc, p2.location)
        assertEquals(
            "/storage/emulated/0/Documents/strictlykeptboy",
            (p2.location as ParentLocation.External).cachedRealPath,
        )
    }

    @Test fun round_trips_external_null_cached_path() {
        val sp = freshSharedPrefs("rsp_v2_ext_null")
        val p1 = RepoStoragePrefs.openForTest(sp)
        val loc = ParentLocation.External(treeUri = "content://x", label = "x", cachedRealPath = null)
        p1.set(loc)
        val p2 = RepoStoragePrefs.openForTest(sp)
        assertEquals(loc, p2.location)
        assertNull((p2.location as ParentLocation.External).cachedRealPath)
    }

    @Test fun reads_v1_prefs_and_upgrades() {
        val sp = freshSharedPrefs("rsp_v1_upgrade")
        // Seed v1 keys (mirror.kind = "external" + uri + label).
        sp.edit()
            .putString("mirror.kind", "external")
            .putString("mirror.tree_uri", "content://legacy/tree/primary%3ADocuments")
            .putString("mirror.label", "Documents")
            .putBoolean("mirror.skipped_in_wizard", true)
            .apply()

        // First open triggers upgrade. The v1 External mirror is promoted
        // to a v2 External parent shell (no cachedRealPath — the Phase B
        // picker rewrite or the migrator will populate it).
        val p1 = RepoStoragePrefs.openForTest(sp)
        val loc = p1.location
        assertTrue("expected External after upgrade", loc is ParentLocation.External)
        loc as ParentLocation.External
        assertEquals("content://legacy/tree/primary%3ADocuments", loc.treeUri)
        assertEquals("Documents", loc.label)
        assertNull("cached real path is unset on v1 upgrade", loc.cachedRealPath)
        // skipped flag is preserved (same key).
        assertTrue(p1.skippedDuringWizard)

        // Upgrade is idempotent — open a second instance, no change.
        val p2 = RepoStoragePrefs.openForTest(sp)
        assertEquals(loc, p2.location)
    }

    @Test fun v1_none_kind_leaves_v2_unconfirmed() {
        val sp = freshSharedPrefs("rsp_v1_none")
        sp.edit().putString("mirror.kind", "none").apply()
        val p = RepoStoragePrefs.openForTest(sp)
        assertNull("v1 None must not auto-confirm a v2 parent", p.location)
    }
}
