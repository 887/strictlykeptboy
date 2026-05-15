package com.eight87.strictlykeptboy.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.A — round-trip + observability of per-system-calendar
 * overrides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SystemCalendarPrefsStoreTest {

    private lateinit var store: SystemCalendarPrefsStore

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("sys_cal_prefs_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        store = SystemCalendarPrefsStore.openForTest(prefs)
    }

    @Test fun missingOverrideReturnsNull() {
        assertNull(store.get("com.google", "alice@gmail.com", 7L))
    }

    @Test fun setRoundTripsAcrossInstances() {
        store.set(
            "com.google", "alice@gmail.com", 7L,
            SystemCalendarOverride(activeToggle = false, priority = 700, supersedes = listOf("9", "10")),
        )
        val out = store.get("com.google", "alice@gmail.com", 7L)
        assertEquals(false, out!!.activeToggle)
        assertEquals(700, out.priority)
        assertEquals(listOf("9", "10"), out.supersedes)
        // Re-open against the same prefs to verify persistence.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("sys_cal_prefs_test", Context.MODE_PRIVATE)
        val reopened = SystemCalendarPrefsStore.openForTest(prefs)
        val rehydrated = reopened.get("com.google", "alice@gmail.com", 7L)
        assertEquals(false, rehydrated!!.activeToggle)
        assertEquals(700, rehydrated.priority)
    }

    @Test fun stateFlowEmitsOnMutation() {
        assertTrue(store.state.value.isEmpty())
        store.set("com.google", "a", 1L, SystemCalendarOverride(priority = 600))
        assertEquals(1, store.state.value.size)
        store.clear("com.google", "a", 1L)
        assertTrue(store.state.value.isEmpty())
    }

    @Test fun clearAllRemovesEverything() {
        store.set("com.google", "a", 1L, SystemCalendarOverride())
        store.set("com.exchange", "b", 2L, SystemCalendarOverride())
        store.clearAll()
        assertTrue(store.state.value.isEmpty())
    }
}
