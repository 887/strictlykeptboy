package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.notif.NotificationChannels
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.ui.settings.categories.offsetToMinutes
import com.eight87.strictlykeptboy.ui.settings.categories.orderedOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.2.D.8 — selecting a default-lead chip flips the persisted
 * pref. Pure storage test: simulates what the FilterChip onClick does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NotificationDefaultsLeadTimeChipTest {

    private fun freshPrefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("defaults-${System.nanoTime()}", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test fun defaultLeadTimesFallback() {
        val p = NotificationPrefs.openForTest(freshPrefs())
        assertEquals(listOf("15m", "1h", "1d"), p.defaultLeadTimes())
        assertEquals(NotificationChannels.EVENTS, p.defaultChannel())
    }

    @Test fun selectingChipFlipsPref() {
        val sp = freshPrefs()
        val p = NotificationPrefs.openForTest(sp)
        // Simulate the chip-click handler: toggle "30m" into the selected set.
        val initial = p.defaultLeadTimes().toSet()
        val withChip = initial + "30m"
        p.setDefaultLeadTimes(orderedOffsets(withChip))
        val reloaded = NotificationPrefs.openForTest(sp).defaultLeadTimes()
        assertEquals(listOf("15m", "30m", "1h", "1d"), reloaded)
    }

    @Test fun unselectingChipRemovesOffset() {
        val sp = freshPrefs()
        val p = NotificationPrefs.openForTest(sp)
        p.setDefaultLeadTimes(listOf("5m", "1h"))
        val current = p.defaultLeadTimes().toSet()
        p.setDefaultLeadTimes(orderedOffsets(current - "5m"))
        assertEquals(listOf("1h"), NotificationPrefs.openForTest(sp).defaultLeadTimes())
    }

    @Test fun channelDefaultPersists() {
        val sp = freshPrefs()
        val p = NotificationPrefs.openForTest(sp)
        p.setDefaultChannel(NotificationChannels.TASKS)
        assertEquals(NotificationChannels.TASKS, NotificationPrefs.openForTest(sp).defaultChannel())
    }

    @Test fun offsetToMinutesParsesUnits() {
        assertEquals(5L, offsetToMinutes("5m"))
        assertEquals(60L, offsetToMinutes("1h"))
        assertEquals(60L * 24, offsetToMinutes("1d"))
        assertEquals(60L * 24 * 7, offsetToMinutes("1w"))
        assertNull(offsetToMinutes("xx"))
    }

    @Test fun orderedOffsetsSortsByDuration() {
        val ordered = orderedOffsets(setOf("1d", "5m", "1h", "30m"))
        assertEquals(listOf("5m", "30m", "1h", "1d"), ordered)
    }
}
