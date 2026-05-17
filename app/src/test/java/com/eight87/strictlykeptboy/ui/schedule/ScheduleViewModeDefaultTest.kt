package com.eight87.strictlykeptboy.ui.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.25.x (D.126) — fresh prefs default to Schedule (agenda).
 *
 * The Day grid renders 5-minute demo events as unreadable slivers at
 * default zoom; the Schedule list is the readable representation for
 * first-launch users. Anyone who already picked a tab keeps their pick
 * (load() returns the stored value unchanged); only empty prefs flip
 * to the new default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleViewModeDefaultTest {
    @Test
    fun fresh_prefs_default_to_schedule_view() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val raw = ctx.getSharedPreferences(
            "skb_view_mode_test_fresh_${System.nanoTime()}",
            Context.MODE_PRIVATE,
        )
        raw.edit().clear().apply()
        val prefs = ScheduleViewModePrefs.openForTest(raw)
        assertEquals(ScheduleViewTab.Schedule, prefs.selected.value)
    }

    @Test
    fun existing_user_pick_is_preserved() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val raw = ctx.getSharedPreferences(
            "skb_view_mode_test_existing_${System.nanoTime()}",
            Context.MODE_PRIVATE,
        )
        raw.edit().clear().putString("selectedTab", "Day").apply()
        val prefs = ScheduleViewModePrefs.openForTest(raw)
        assertEquals(ScheduleViewTab.Day, prefs.selected.value)
    }
}
