package com.eight87.strictlykeptboy.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.17.A.7 — round-trip Internal / External; read-from-v1 upgrade.
 *
 * Originally `RepoStoragePrefsTest` (Round 2.7.B.5); rewritten in 2.17
 * for the `ParentLocation` schema. Test class name retained per
 * D-2.17.l "refactor, don't rewrite" — kept as RepoStoragePrefsTest plus
 * `RepoStoragePrefsV2Test` per the plan's verification list.
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

    @Test fun defaultIsNullLocation() {
        val p = newPrefs()
        assertNull("no parent confirmed yet", p.location)
        assertFalse(p.skippedDuringWizard)
        assertFalse(p.migratedFromD27b)
    }

    @Test fun setExternalClearsWizardSkipFlag() {
        val p = newPrefs("rsp_clear_skip")
        p.skippedDuringWizard = true
        p.set(ParentLocation.External("content://x", "x", cachedRealPath = "/storage/emulated/0/x"))
        assertFalse(p.skippedDuringWizard)
    }

    @Test fun setInternalClearsWizardSkipFlag() {
        val p = newPrefs("rsp_clear_skip2")
        p.skippedDuringWizard = true
        p.set(ParentLocation.Internal(absPath = "/data/user/0/com.app/files/strictlykeptboy"))
        assertFalse(p.skippedDuringWizard)
    }

    @Test fun migratedFlagSurvivesProcessRestart() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = "rsp_flag"
        val sp = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        val p1 = RepoStoragePrefs.openForTest(sp)
        p1.migratedFromD27b = true
        val p2 = RepoStoragePrefs.openForTest(sp)
        assertTrue(p2.migratedFromD27b)
    }
}
