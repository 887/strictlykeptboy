package com.eight87.strictlykeptboy.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.7.B.5 — round-trip None / External; wizard-skip flag clears
 * when a real location lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RepoStoragePrefsTest {

    private fun newPrefs(file: String = "repo_storage_test"): RepoStoragePrefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        return RepoStoragePrefs.openForTest(sp)
    }

    @Test fun defaultIsNone() {
        val p = newPrefs()
        assertEquals(MirrorLocation.None, p.location)
        assertFalse(p.skippedDuringWizard)
    }

    @Test fun roundTripsExternal() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = "repo_storage_rt"
        val sp = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        val p1 = RepoStoragePrefs.openForTest(sp)
        val loc = MirrorLocation.External(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2Fskb",
            label = "skb",
        )
        p1.set(loc)
        assertEquals(loc, p1.location)

        // New instance — same backing prefs — must observe the same value.
        val p2 = RepoStoragePrefs.openForTest(sp)
        assertEquals(loc, p2.location)
    }

    @Test fun roundTripsNoneAfterClearing() {
        val p = newPrefs("repo_storage_clear")
        p.set(MirrorLocation.External("content://x", "x"))
        p.set(MirrorLocation.None)
        assertEquals(MirrorLocation.None, p.location)
    }

    @Test fun skippedDuringWizardSurvivesRoundTrip() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = "repo_storage_skipped"
        val sp = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        val p1 = RepoStoragePrefs.openForTest(sp)
        p1.skippedDuringWizard = true
        val p2 = RepoStoragePrefs.openForTest(sp)
        assertTrue(p2.skippedDuringWizard)
    }

    @Test fun settingExternalClearsSkippedFlag() {
        val p = newPrefs("repo_storage_clear_skip")
        p.skippedDuringWizard = true
        p.set(MirrorLocation.External("content://x", "x"))
        assertFalse(p.skippedDuringWizard)
    }
}
