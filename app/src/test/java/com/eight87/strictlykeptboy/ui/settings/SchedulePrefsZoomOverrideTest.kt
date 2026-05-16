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
 * Round 2.23 Phase C (D-2.23.a) — global zoom override round-trip.
 *
 * Pins:
 *  - default state has `globalZoomOverride = null` (Auto).
 *  - `setGlobalZoomOverride(n)` persists across reopen.
 *  - `setGlobalZoomOverride(null)` clears the override.
 *  - out-of-range Int values clamp to [ZOOM_MIN..ZOOM_MAX].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SchedulePrefsZoomOverrideTest {

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test fun defaults_to_null() {
        val prefs = CalendarVisibilityPrefs.open(ctx, ListKind.Calendars)
        assertNull(prefs.globalZoomOverride())
        assertNull(prefs.state.value.globalZoomOverride)
    }

    @Test fun set_and_reload_persists() {
        val prefs = CalendarVisibilityPrefs.open(ctx, ListKind.Calendars)
        prefs.setGlobalZoomOverride(3)
        assertEquals(3, prefs.globalZoomOverride())
        // Reopen — same backing SharedPreferences.
        val reopened = CalendarVisibilityPrefs.open(ctx, ListKind.Calendars)
        assertEquals(3, reopened.globalZoomOverride())
    }

    @Test fun null_clears_override() {
        val prefs = CalendarVisibilityPrefs.open(ctx, ListKind.Calendars)
        prefs.setGlobalZoomOverride(4)
        prefs.setGlobalZoomOverride(null)
        assertNull(prefs.globalZoomOverride())
    }

    @Test fun out_of_range_clamps() {
        val prefs = CalendarVisibilityPrefs.open(ctx, ListKind.Calendars)
        prefs.setGlobalZoomOverride(0)
        assertEquals(1, prefs.globalZoomOverride())
        prefs.setGlobalZoomOverride(99)
        assertEquals(4, prefs.globalZoomOverride())
    }
}
