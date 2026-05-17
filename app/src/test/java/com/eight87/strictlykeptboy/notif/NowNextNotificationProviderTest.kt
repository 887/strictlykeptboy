package com.eight87.strictlykeptboy.notif

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.BandRef
import com.eight87.strictlykeptboy.resolver.NowNextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.25 Phase C.4 — asserts the built notification's title +
 * content match the snapshot in both directions: snapshot with both
 * now+next, snapshot with neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NowNextNotificationProviderTest {

    private val now: Instant = Instant.parse("2026-05-17T09:00:00Z")
    private val tz: ZoneId = ZoneId.of("UTC")

    private fun band(title: String, emoji: String? = null, atOffsetMin: Long): BandRef {
        val start = ZonedDateTime.ofInstant(now.plusSeconds(atOffsetMin * 60), tz)
        return BandRef(
            title = title,
            emoji = emoji,
            calendarDisplay = "demo",
            start = start,
            end = start.plusMinutes(30),
        )
    }

    @Test fun builds_now_and_next_lines_from_snapshot() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)
        val provider = NowNextNotificationProvider(ctx)
        val snap = NowNextSnapshot(
            now = band("deep focus", atOffsetMin = -10),
            next = band("brush teeth", emoji = "🪥", atOffsetMin = 135),
            now_at = now,
        )
        val n = provider.build(snap)
        val title = n.extras.getCharSequence("android.title")?.toString()
        val body = n.extras.getCharSequence("android.text")?.toString()
        assertEquals("Now: deep focus", title)
        // Body has the next title + a relative-time bucket. We assert
        // the prefix is the next line and the relative copy contains "h".
        assertTrue("body=$body", body!!.startsWith("Next: 🪥 brush teeth · "))
        assertTrue("body=$body", body.contains("h"))
    }

    @Test fun builds_no_active_task_when_snapshot_empty() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)
        val provider = NowNextNotificationProvider(ctx)
        val n = provider.build(NowNextSnapshot.Empty)
        val title = n.extras.getCharSequence("android.title")?.toString()
        val body = n.extras.getCharSequence("android.text")?.toString()
        assertEquals("No active task", title)
        // Empty next → body is the empty string we set.
        assertTrue("body=$body", body.isNullOrEmpty())
    }
}
