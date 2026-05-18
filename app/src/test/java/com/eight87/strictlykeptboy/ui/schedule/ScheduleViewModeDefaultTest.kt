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
 * Fresh prefs default to `Now` (agenda-list, today+forward). Persisted
 * legacy values (`Schedule` from the old tab, `Agenda` from the prior
 * timebox tab) both migrate to `Now`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleViewModeDefaultTest {
    @Test
    fun fresh_prefs_default_to_now_view() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val raw = ctx.getSharedPreferences(
            "skb_view_mode_test_fresh_${System.nanoTime()}",
            Context.MODE_PRIVATE,
        )
        raw.edit().clear().apply()
        val prefs = ScheduleViewModePrefs.openForTest(raw)
        assertEquals(ScheduleViewTab.Now, prefs.selected.value)
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

    @Test
    fun legacy_schedule_value_migrates_to_now() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val raw = ctx.getSharedPreferences(
            "skb_view_mode_test_legacy_schedule_${System.nanoTime()}",
            Context.MODE_PRIVATE,
        )
        raw.edit().clear().putString("selectedTab", "Schedule").apply()
        val prefs = ScheduleViewModePrefs.openForTest(raw)
        assertEquals(ScheduleViewTab.Now, prefs.selected.value)
    }

    @Test
    fun legacy_agenda_value_migrates_to_now() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val raw = ctx.getSharedPreferences(
            "skb_view_mode_test_legacy_agenda_${System.nanoTime()}",
            Context.MODE_PRIVATE,
        )
        raw.edit().clear().putString("selectedTab", "Agenda").apply()
        val prefs = ScheduleViewModePrefs.openForTest(raw)
        assertEquals(ScheduleViewTab.Now, prefs.selected.value)
    }
}
