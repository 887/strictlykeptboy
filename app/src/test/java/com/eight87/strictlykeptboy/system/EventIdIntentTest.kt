package com.eight87.strictlykeptboy.system

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.E.6 — `VIEW content://<authority>/events/<id>` with MIME
 * `vnd.android.cursor.item/event` routes to [RoutedIntent.ShowEvent]
 * with the row id parsed from the URI's last path segment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class EventIdIntentTest {

    @Test fun routesToShowEventForEventsRow() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("content://com.android.calendar/events/9001"),
                "vnd.android.cursor.item/event",
            )
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.ShowEvent)
        assertEquals(9001L, (routed as RoutedIntent.ShowEvent).eventId)
    }

    @Test fun routesByMimeAloneWhenUriIsMissing() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "vnd.android.cursor.item/event"
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertEquals(RoutedIntent.Unhandled, routed)
    }
}
