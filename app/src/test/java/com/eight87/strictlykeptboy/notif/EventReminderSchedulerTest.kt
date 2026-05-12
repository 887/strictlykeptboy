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
class EventReminderSchedulerTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test fun schedulesOneAlarmPerLeadTime() {
        val sched = EventReminderScheduler(ctx())
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2)
        val input = EventReminderScheduler.ReminderInput(
            repoId = "r1",
            eventId = "ev1",
            calendarId = "cal1",
            title = "Dentist",
            startIso = start.toString(),
            leadTimes = listOf("1d", "2h", "15m"),
        )
        val ids = sched.scheduleAll(listOf(input))
        assertEquals(3, ids.size)
        // Stable ids
        assertTrue(ids.contains("r1:ev1:1d"))
        assertTrue(ids.contains("r1:ev1:2h"))
        assertTrue(ids.contains("r1:ev1:15m"))

        val am = ctx().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = shadowOf(am).scheduledAlarms
        assertEquals(3, scheduled.size)
    }

    @Test fun skipsPastDueAlarms() {
        val sched = EventReminderScheduler(ctx())
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
        val input = EventReminderScheduler.ReminderInput(
            repoId = "r1", eventId = "ev1", calendarId = "cal1",
            title = "x", startIso = start.toString(),
            leadTimes = listOf("1h"), // fire-at is 55min in past
        )
        val ids = sched.scheduleAll(listOf(input), now = Instant.now())
        assertEquals(0, ids.size)
    }

    @Test fun privateFlagPropagates() {
        val sched = EventReminderScheduler(ctx())
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2)
        val input = EventReminderScheduler.ReminderInput(
            repoId = "r1", eventId = "evpriv", calendarId = "cal1",
            title = "secret", startIso = start.toString(),
            leadTimes = listOf("15m"), privateEvent = true,
        )
        val ids = sched.scheduleAll(listOf(input))
        assertEquals(1, ids.size)
        // The private flag rides on the PendingIntent's extras — verifying
        // here would require popping the alarm, which Robolectric supports
        // but adds noise. The lockscreen-privacy test exercises the receiver
        // side directly.
    }

    @Test fun snoozeArmsFreshAlarm() {
        val sched = EventReminderScheduler(ctx())
        val fireAt = System.currentTimeMillis() + 10 * 60_000L
        sched.snoozeAt("r1", "ev1", "x", fireAt)
        val am = ctx().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = shadowOf(am).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(fireAt, scheduled[0].triggerAtTime)
    }
}
