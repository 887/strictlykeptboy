package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.2.D.7 — AutoTabletPrefs round-trip for every field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AutoTabletPrefsTest {

    private fun freshPrefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("auto-tablet-${System.nanoTime()}", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test fun defaultsMatchSpec() {
        val p = AutoTabletPrefs.openForTest(freshPrefs())
        assertTrue(p.isShowOnAutoForRepo("any-repo"))
        assertEquals(8, p.state.value.maxAutoEventsToday)
        assertEquals(TabletMasterDetailMode.Auto, p.state.value.tabletMasterDetailMode)
        assertFalse(p.state.value.tabletOpenDetailByDefault)
    }

    @Test fun perRepoShowToggleRoundTrips() {
        val sp = freshPrefs()
        val p1 = AutoTabletPrefs.openForTest(sp)
        p1.setShowOnAutoForRepo("repo-a", false)
        p1.setShowOnAutoForRepo("repo-b", true)
        val p2 = AutoTabletPrefs.openForTest(sp)
        assertFalse(p2.isShowOnAutoForRepo("repo-a"))
        assertTrue(p2.isShowOnAutoForRepo("repo-b"))
        // Unset repo still defaults to true.
        assertTrue(p2.isShowOnAutoForRepo("repo-c"))
    }

    @Test fun maxEventsClampsIntoRange() {
        val p = AutoTabletPrefs.openForTest(freshPrefs())
        p.setMaxAutoEventsToday(100)
        assertEquals(AutoTabletPrefs.MAX_EVENTS, p.state.value.maxAutoEventsToday)
        p.setMaxAutoEventsToday(0)
        assertEquals(AutoTabletPrefs.MIN_EVENTS, p.state.value.maxAutoEventsToday)
        p.setMaxAutoEventsToday(12)
        assertEquals(12, p.state.value.maxAutoEventsToday)
    }

    @Test fun masterDetailModeRoundTrips() {
        val sp = freshPrefs()
        val p1 = AutoTabletPrefs.openForTest(sp)
        p1.setTabletMasterDetailMode(TabletMasterDetailMode.Off)
        assertEquals(TabletMasterDetailMode.Off, p1.state.value.tabletMasterDetailMode)
        val p2 = AutoTabletPrefs.openForTest(sp)
        assertEquals(TabletMasterDetailMode.Off, p2.state.value.tabletMasterDetailMode)

        p2.setTabletMasterDetailMode(TabletMasterDetailMode.On)
        val p3 = AutoTabletPrefs.openForTest(sp)
        assertEquals(TabletMasterDetailMode.On, p3.state.value.tabletMasterDetailMode)
    }

    @Test fun openDetailByDefaultRoundTrips() {
        val sp = freshPrefs()
        val p1 = AutoTabletPrefs.openForTest(sp)
        p1.setTabletOpenDetailByDefault(true)
        val p2 = AutoTabletPrefs.openForTest(sp)
        assertTrue(p2.state.value.tabletOpenDetailByDefault)
    }
}
