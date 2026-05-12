package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NotificationChannelRegistrationTest {

    @Test fun allSixChannelsRegistered() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (id in NotificationChannels.ALL) {
            val c = nm.getNotificationChannel(id)
            assertNotNull("channel $id not registered", c)
        }
    }

    @Test fun expectedImportances() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,
            nm.getNotificationChannel(NotificationChannels.EVENTS).importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,
            nm.getNotificationChannel(NotificationChannels.TASKS).importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW,
            nm.getNotificationChannel(NotificationChannels.BRIEFINGS).importance)
        assertEquals(NotificationManager.IMPORTANCE_MIN,
            nm.getNotificationChannel(NotificationChannels.SYNC).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
            nm.getNotificationChannel(NotificationChannels.ERRORS).importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW,
            nm.getNotificationChannel(NotificationChannels.FOREGROUND).importance)
    }

    @Test fun reRegistrationIsIdempotent() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)
        NotificationChannels.registerAll(ctx) // should not throw
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // All 6 channels still present.
        assertEquals(6, NotificationChannels.ALL.count { nm.getNotificationChannel(it) != null })
    }
}
