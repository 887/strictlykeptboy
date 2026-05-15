package com.eight87.strictlykeptboy.system

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/**
 * Round 2.18.E.6 — `VIEW content://com.android.calendar/time/<epoch>`
 * routes to [RoutedIntent.GoToDate] pinned to the local date that
 * corresponds to the epoch seconds in the path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class TimeEpochIntentTest {

    @Test fun routesToGoToDateForKnownEpoch() {
        // 1747353600 = 2025-05-16 00:00 UTC. We assert against the
        // local date because that's how Schedule pins.
        val epoch = 1_747_353_600L
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/time/$epoch")
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.GoToDate)
        val expected = Instant.ofEpochSecond(epoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        assertEquals(expected, (routed as RoutedIntent.GoToDate).date)
    }

    @Test fun rejectsMissingEpochSegment() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/time/")
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertEquals(RoutedIntent.Unhandled, routed)
    }
}
