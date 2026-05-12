package com.eight87.strictlykeptboy.notif

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SnoozeActionTest {

    @Test fun snoozeArmsAlarmAndDismissesNotification() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        // First post a notification.
        val fire = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev1")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "x")
        }
        ReminderBroadcastReceiver().onReceive(ctx, fire)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, shadowOf(nm).activeNotifications.size)

        val before = System.currentTimeMillis()
        // Then send a snooze 10m broadcast.
        val snooze = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_SNOOZE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev1")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "x")
            putExtra(ReminderBroadcastReceiver.EXTRA_SNOOZE_MINUTES, 10)
        }
        ReminderBroadcastReceiver().onReceive(ctx, snooze)

        // Notification dismissed.
        assertEquals(0, shadowOf(nm).activeNotifications.size)
        // A new alarm armed roughly +10m.
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = shadowOf(am).scheduledAlarms
        assertEquals(1, scheduled.size)
        val delta = scheduled[0].triggerAtTime - before
        assertTrue("expected ~10min, got ${delta}ms", delta in (9 * 60_000L)..(11 * 60_000L))
    }
}
