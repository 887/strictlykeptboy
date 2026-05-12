package com.eight87.strictlykeptboy.ui.wizard

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgeGatePrefsTest {

    @Test fun `confirm flips isConfirmed and stores timestamp`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("agegate_test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val gate = AgeGatePrefs.openForTest(prefs)
        assertFalse(gate.isConfirmed())
        gate.confirm(nowMs = 1_700_000_000_000L)
        assertTrue(gate.isConfirmed())
        assertEquals(1_700_000_000_000L, gate.confirmedAtMs())
    }

    @Test fun `clear removes confirmation`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("agegate_test2", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val gate = AgeGatePrefs.openForTest(prefs)
        gate.confirm()
        gate.clear()
        assertFalse(gate.isConfirmed())
    }

    @Test fun `neutral mode pref round-trip`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("neutral_test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val nm = NeutralModePrefs.openForTest(prefs)
        assertFalse(nm.isEnabled())
        nm.setEnabled(true)
        assertTrue(nm.isEnabled())
        nm.setEnabled(false)
        assertFalse(nm.isEnabled())
    }
}
