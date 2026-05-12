package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class LockscreenPrivacyTest {

    @Test fun privateEventUsesRedactedTitle() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        val intent = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "evpriv")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "Secret Dentist")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, true)
        }
        ReminderBroadcastReceiver().onReceive(ctx, intent)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(nm).activeNotifications
        assertEquals(1, posted.size)
        val n = posted[0].notification
        val title = n.extras.getCharSequence("android.title")?.toString()
        assertNotNull(title)
        // The private-event title is the generic "Scheduled event" string, NOT the real title.
        assertNotEquals("Secret Dentist", title)
        assertNotNull("publicVersion must be set for private events", n.publicVersion)
    }

    @Test fun normalEventShowsRealTitle() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        val intent = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev2")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "Lunch")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, false)
        }
        ReminderBroadcastReceiver().onReceive(ctx, intent)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(nm).activeNotifications
        assertTrue(posted.isNotEmpty())
        val title = posted[0].notification.extras.getCharSequence("android.title")?.toString()
        assertEquals("Lunch", title)
    }
}
