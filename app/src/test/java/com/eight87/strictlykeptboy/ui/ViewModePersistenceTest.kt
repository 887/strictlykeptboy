package com.eight87.strictlykeptboy.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewModePrefs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModePersistenceTest {

    private fun openFresh(): ScheduleViewModePrefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Each test call gets a fresh in-memory file by name — Robolectric
        // resets between tests, so simply re-opening simulates a process
        // restart.
        return ScheduleViewModePrefs.open(ctx)
    }

    @Test fun default_is_schedule() {
        // Round 2.25.x (D.126) — fresh prefs default to Schedule (agenda).
        // The Day grid renders demo 5-min events as unreadable slivers; the
        // agenda list is the readable representation on first launch.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences(ScheduleViewModePrefs.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val prefs = openFresh()
        check(prefs.selected.value == ScheduleViewTab.Schedule)
    }

    @Test fun set_week_then_reopen_restores_week() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences(ScheduleViewModePrefs.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()

        val first = openFresh()
        first.set(ScheduleViewTab.Week)
        check(first.selected.value == ScheduleViewTab.Week)

        // Simulate process restart — open a new instance from the same
        // backing prefs file.
        val second = openFresh()
        check(second.selected.value == ScheduleViewTab.Week)
    }

    @Test fun every_tab_round_trips() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ScheduleViewTab.entries.forEach { tab ->
            ctx.getSharedPreferences(ScheduleViewModePrefs.PREFS_FILE, Context.MODE_PRIVATE)
                .edit().clear().commit()
            openFresh().set(tab)
            check(openFresh().selected.value == tab) { "round-trip failed for $tab" }
        }
    }
}
