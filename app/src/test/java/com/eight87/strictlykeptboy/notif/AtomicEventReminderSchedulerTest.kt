package com.eight87.strictlykeptboy.notif

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AtomicEventReminderSchedulerTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test fun schedulesStartAndEndAlarms() {
        val sched = AtomicEventReminderScheduler(ctx())
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)
        val end = start.plusMinutes(5)
        val reminder = AtomicEventReminderScheduler.AtomicReminder(
            repoId = "r1",
            eventId = "ev1",
            calendarId = "cal1",
            title = "Brush teeth",
            startIso = start.toString(),
            endIso = end.toString(),
        )
        val ids = sched.schedule(reminder)
        assertEquals(2, ids.size)
        assertTrue(ids.contains("r1:ev1:atomic:start"))
        assertTrue(ids.contains("r1:ev1:atomic:end"))

        val am = ctx().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(2, shadowOf(am).scheduledAlarms.size)
    }

    @Test fun skipsPastStartButKeepsFutureEnd() {
        val sched = AtomicEventReminderScheduler(ctx())
        val now = Instant.now()
        // start was 30s ago, end is 4m30s from now → only end scheduled.
        val start = OffsetDateTime.ofInstant(now.minusSeconds(30), ZoneOffset.UTC)
        val end = OffsetDateTime.ofInstant(now.plusSeconds(270), ZoneOffset.UTC)
        val r = AtomicEventReminderScheduler.AtomicReminder(
            repoId = "r1", eventId = "ev1", calendarId = "cal1",
            title = "x", startIso = start.toString(), endIso = end.toString(),
        )
        val ids = sched.schedule(r, now = now)
        assertEquals(1, ids.size)
        assertEquals("r1:ev1:atomic:end", ids.single())
    }

    @Test fun snoozeArmsFreshAlarm() {
        val sched = AtomicEventReminderScheduler(ctx())
        val r = AtomicEventReminderScheduler.AtomicReminder(
            repoId = "r1", eventId = "ev1", calendarId = "cal1",
            title = "x",
            startIso = OffsetDateTime.now(ZoneOffset.UTC).toString(),
            endIso = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5).toString(),
        )
        val fireAt = System.currentTimeMillis() + 10 * 60_000L
        sched.snoozeAt(r, fireAt)
        val am = ctx().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(1, shadowOf(am).scheduledAlarms.size)
    }
}
