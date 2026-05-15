package com.eight87.strictlykeptboy.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.B.5 — round-trip + observability for the per-calendar
 * `visible` override added to [SystemCalendarOverride], plus the
 * global flags added to [SystemCalendarGlobalPrefs].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SystemCalendarPrefsStoreVisibilityTest {

    private lateinit var store: SystemCalendarPrefsStore
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val prefs = ctx.getSharedPreferences("vis_prefs_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        store = SystemCalendarPrefsStore.openForTest(prefs)
    }

    @Test fun visibleDefaultsTrue() {
        // Missing override → callers treat visibility as true.
        assertNull(store.get("com.google", "a", 1L))
        val ov = SystemCalendarOverride()
        assertTrue(ov.visible)
    }

    @Test fun setVisibleRoundTripsAcrossInstances() {
        store.setVisible("com.google", "alice@gmail.com", 7L, false)
        val out = store.get("com.google", "alice@gmail.com", 7L)
        assertEquals(false, out?.visible)
        // Re-open against the same prefs to verify persistence.
        val prefs = ctx.getSharedPreferences("vis_prefs_test", Context.MODE_PRIVATE)
        val reopened = SystemCalendarPrefsStore.openForTest(prefs)
        assertEquals(false, reopened.get("com.google", "alice@gmail.com", 7L)?.visible)
    }

    @Test fun setVisibleDoesNotClobberOtherFields() {
        store.set(
            "com.google", "a", 7L,
            SystemCalendarOverride(priority = 900, supersedes = listOf("8")),
        )
        store.setVisible("com.google", "a", 7L, false)
        val out = store.get("com.google", "a", 7L)!!
        assertEquals(false, out.visible)
        assertEquals(900, out.priority)
        assertEquals(listOf("8"), out.supersedes)
    }

    @Test fun globalShowSystemCalendarsRoundTrips() {
        assertFalse(store.globalState.value.showSystemCalendars)
        store.setShowSystemCalendars(true)
        assertTrue(store.globalState.value.showSystemCalendars)
        val prefs = ctx.getSharedPreferences("vis_prefs_test", Context.MODE_PRIVATE)
        val reopened = SystemCalendarPrefsStore.openForTest(prefs)
        assertTrue(reopened.globalState.value.showSystemCalendars)
    }

    @Test fun globalAllowEditingRoundTrips() {
        store.setAllowEditing(true)
        assertTrue(store.globalState.value.allowEditing)
        store.setAllowEditing(false)
        assertFalse(store.globalState.value.allowEditing)
    }

    @Test fun markAccountChangeNudgeShownIsSticky() {
        assertFalse(store.globalState.value.accountChangeNudgeShown)
        store.markAccountChangeNudgeShown()
        assertTrue(store.globalState.value.accountChangeNudgeShown)
        val prefs = ctx.getSharedPreferences("vis_prefs_test", Context.MODE_PRIVATE)
        val reopened = SystemCalendarPrefsStore.openForTest(prefs)
        assertTrue(reopened.globalState.value.accountChangeNudgeShown)
    }
}
