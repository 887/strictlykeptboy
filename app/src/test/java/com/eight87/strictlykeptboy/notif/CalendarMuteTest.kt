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

/**
 * Phase 2.1.F.3 + F.4 — LogicalGroup keying + time-bounded mute round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CalendarMuteTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test fun groupStorageKeyShapes() {
        assertEquals("calendar.r1.cal-a", LogicalGroup.Calendar("r1", "cal-a").storageKey)
        assertEquals("category.medical", LogicalGroup.Category("medical").storageKey)
        assertEquals("repo.r1", LogicalGroup.Repo("r1").storageKey)
    }

    @Test fun activeMuteIsRespectedAndExpiresPositionally() {
        val prefs = NotificationPrefs.open(ctx())
        val cal = LogicalGroup.Calendar("r1", "cal-a")
        val now = 1_000_000_000L

        assertFalse(prefs.isGroupMutedAt(cal, now))
        prefs.setGroupMute(cal, untilEpochMs = now + 60_000L)
        assertTrue(prefs.isGroupMutedAt(cal, now))
        assertTrue(prefs.isGroupMutedAt(cal, now + 30_000L))
        // After the deadline, the same prefs entry stops being active —
        // the read path doesn't clear it but the active-check returns false.
        assertFalse(prefs.isGroupMutedAt(cal, now + 60_001L))
    }

    @Test fun clearGroupMuteRemovesKey() {
        val prefs = NotificationPrefs.open(ctx())
        val cat = LogicalGroup.Category("medical")
        prefs.setGroupMute(cat, untilEpochMs = 9_999_999_999L)
        assertEquals(9_999_999_999L, prefs.groupMuteUntil(cat))
        prefs.setGroupMute(cat, untilEpochMs = null)
        assertNull(prefs.groupMuteUntil(cat))
    }

    @Test fun calendarEnabledTogglePersists() {
        val prefs = NotificationPrefs.open(ctx())
        // Default true.
        assertTrue(prefs.isCalendarEnabled("r1", "cal-a"))
        prefs.setCalendarEnabled("r1", "cal-a", false)
        assertFalse(prefs.isCalendarEnabled("r1", "cal-a"))
        prefs.setCalendarEnabled("r1", "cal-a", true)
        assertTrue(prefs.isCalendarEnabled("r1", "cal-a"))
    }

    @Test fun notificationMuteValueObjectMatchesPrefs() {
        val mute = NotificationMute(LogicalGroup.Repo("r1"), untilEpochMs = 500L)
        assertTrue(mute.isActiveAt(499L))
        assertFalse(mute.isActiveAt(500L))
        assertFalse(mute.isActiveAt(501L))
        assertFalse(NotificationMute(LogicalGroup.Repo("r1"), untilEpochMs = null).isActiveAt(0L))
    }
}
