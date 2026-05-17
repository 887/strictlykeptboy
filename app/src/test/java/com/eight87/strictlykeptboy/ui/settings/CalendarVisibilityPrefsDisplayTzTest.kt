package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.24 Phase C.4 — CalendarVisibilityPrefs.displayTzId round-trip.
 *
 * Mirrors the Round 2.23 globalZoomOverride pattern: null by default,
 * setter persists, getter reads back; blank values normalise to null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarVisibilityPrefsDisplayTzTest {

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test fun default_displayTzId_is_null() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        assertNull(store.displayTzId())
        assertNull(store.state.value.displayTzId)
    }

    @Test fun setDisplayTzId_round_trips_through_prefs() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        store.setDisplayTzId("America/New_York")
        assertEquals("America/New_York", store.displayTzId())
        val reopened = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        assertEquals("America/New_York", reopened.displayTzId())
    }

    @Test fun setDisplayTzId_null_resets_to_system() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        store.setDisplayTzId("Europe/Berlin")
        store.setDisplayTzId(null)
        assertNull(store.displayTzId())
    }

    @Test fun setDisplayTzId_blank_string_normalises_to_null() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        store.setDisplayTzId("   ")
        assertNull(store.displayTzId())
    }

    @Test fun displayTzId_orthogonal_to_globalZoomOverride() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        store.setGlobalZoomOverride(3)
        store.setDisplayTzId("Asia/Tokyo")
        assertEquals(3, store.globalZoomOverride())
        assertEquals("Asia/Tokyo", store.displayTzId())
    }
}
