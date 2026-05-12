package com.eight87.strictlykeptboy.notif

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NotificationPrefsTest {

    private fun freshPrefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("test-notif-${System.nanoTime()}", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test fun defaultsEnabledNotSilent() {
        val p = NotificationPrefs.openForTest(freshPrefs())
        assertTrue(p.isChannelEnabled(NotificationChannels.EVENTS))
        assertFalse(p.isChannelSilent(NotificationChannels.EVENTS))
    }

    @Test fun togglePersistsAcrossReopen() {
        val prefs = freshPrefs()
        val p1 = NotificationPrefs.openForTest(prefs)
        p1.setChannelEnabled(NotificationChannels.SYNC, false)
        p1.setChannelSilent(NotificationChannels.EVENTS, true)
        p1.setCalendarEnabled("repo1", "cal1", false)
        p1.setCalendarLeadTimes("repo1", "cal1", listOf("15m", "1h"))

        val p2 = NotificationPrefs.openForTest(prefs)
        assertFalse(p2.isChannelEnabled(NotificationChannels.SYNC))
        assertTrue(p2.isChannelSilent(NotificationChannels.EVENTS))
        assertFalse(p2.isCalendarEnabled("repo1", "cal1"))
        assertEquals(listOf("15m", "1h"), p2.calendarLeadTimes("repo1", "cal1"))
    }

    @Test fun unsetCalendarLeadTimesReturnsNull() {
        val p = NotificationPrefs.openForTest(freshPrefs())
        assertNull(p.calendarLeadTimes("repo1", "cal-unset"))
    }
}
