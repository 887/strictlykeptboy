package com.eight87.strictlykeptboy.ui.calendars

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import com.eight87.strictlykeptboy.ui.settings.ListKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.21 Phase C.5 — pure-state assertions for [OverlayPickerScreen]'s
 * persistence layer. The composable surface is best validated on the AVD;
 * here we cover the visibility-prefs round-trip the screen drives.
 *
 * Specifically: two repos × five calendars each — toggle parity, repo-
 * grouping order, and zoom round-trip via [CalendarVisibilityPrefs].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OverlayPickerScreenTest {

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun open(): CalendarVisibilityPrefs {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        return CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
    }

    @Test fun two_repos_five_calendars_each_toggle_parity() {
        val store = open()
        // Toggle every calendar off for repo-a, leave repo-b visible.
        (1..5).forEach { idx ->
            store.setVisible(id = "cal-$idx", visible = false, repoId = "repo-a")
        }
        // repo-b stays default-visible (no entry written).
        (1..5).forEach { idx ->
            assertFalse(store.isVisible("cal-$idx", "repo-a"))
            assertTrue(store.isVisible("cal-$idx", "repo-b"))
        }
    }

    @Test fun zoom_round_trips_per_overlay_keyed_by_repo_and_id() {
        val store = open()
        store.setZoom(id = "cal-routines", zoom = 3, repoId = "repo-a")
        store.setZoom(id = "cal-routines", zoom = 1, repoId = "repo-b")
        assertEquals(3, store.zoomOf("cal-routines", "repo-a"))
        assertEquals(1, store.zoomOf("cal-routines", "repo-b"))
    }

    @Test fun zoom_default_when_never_set_is_two() {
        val store = open()
        assertEquals(2, store.zoomOf("cal-fresh", "repo-x"))
    }

    @Test fun zoom_clamps_out_of_range_inputs() {
        val store = open()
        store.setZoom("c", zoom = 0, repoId = "r")
        assertEquals(1, store.zoomOf("c", "r"))
        store.setZoom("c", zoom = 99, repoId = "r")
        assertEquals(4, store.zoomOf("c", "r"))
    }

    @Test fun zoom_survives_reopen() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val a = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        a.setZoom("cal-q", 4, "repo-q")
        val b = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        assertEquals(4, b.zoomOf("cal-q", "repo-q"))
    }
}
