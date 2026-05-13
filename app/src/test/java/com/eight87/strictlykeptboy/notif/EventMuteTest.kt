package com.eight87.strictlykeptboy.notif

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.1.F.2 — per-event mute prefs round-trip + receiver short-circuit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class EventMuteTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test fun defaultIsNotMuted() {
        val prefs = NotificationPrefs.open(ctx())
        assertFalse(prefs.isEventMuted("r1", "ev1"))
    }

    @Test fun toggleMuteRoundTrip() {
        val prefs = NotificationPrefs.open(ctx())
        prefs.setEventMuted("r1", "ev2", true)
        assertTrue(prefs.isEventMuted("r1", "ev2"))
        prefs.setEventMuted("r1", "ev2", false)
        assertFalse(prefs.isEventMuted("r1", "ev2"))
    }

    @Test fun mutePerEventIsScopedNotGlobal() {
        val prefs = NotificationPrefs.open(ctx())
        prefs.setEventMuted("r1", "evA", true)
        assertFalse(prefs.isEventMuted("r1", "evB"))
        assertFalse(prefs.isEventMuted("r2", "evA"))
    }
}
