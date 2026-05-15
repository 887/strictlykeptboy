package com.eight87.strictlykeptboy.system

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.E.10 — verify the new manifest filters route into the
 * right [RoutedIntent] variant, and that the existing
 * `strictlykeptboy://event/<id>` deep link is NOT swallowed (stays
 * `Unhandled` so Phase MM's router gets it).
 *
 * One test per filter, plus the back-compat collision check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class IntentFilterRoutingTest {

    @Test fun goToDateRouting() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/time/1747353600")
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.GoToDate)
    }

    @Test fun showEventRouting() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("content://com.android.calendar/events/42"),
                "vnd.android.cursor.item/event",
            )
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.ShowEvent)
        assertEquals(42L, (routed as RoutedIntent.ShowEvent).eventId)
    }

    @Test fun editEventRouting() {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(
                Uri.parse("content://com.android.calendar/events/7"),
                "vnd.android.cursor.item/event",
            )
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 1_000_000L)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, 2_000_000L)
            putExtra(CalendarContract.Events.TITLE, "Edit me")
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.EditEvent)
        val edit = routed as RoutedIntent.EditEvent
        assertEquals(7L, edit.eventId)
        assertEquals(1_000_000L, edit.prefill.beginMs)
        assertEquals("Edit me", edit.prefill.title)
    }

    @Test fun insertEventRouting() {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = "vnd.android.cursor.dir/event"
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 5_000L)
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.EditEvent)
        val edit = routed as RoutedIntent.EditEvent
        assertEquals(null, edit.eventId)
        assertEquals(5_000L, edit.prefill.beginMs)
    }

    @Test fun localIcsRouting() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("file:///sdcard/Download/test.ics"),
                "text/calendar",
            )
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.ImportIcs)
        assertTrue((routed as RoutedIntent.ImportIcs).source is IcsSource.LocalUri)
    }

    @Test fun httpsIcsRouting() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/calendar.ics")
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertTrue(routed is RoutedIntent.ImportIcs)
        assertTrue((routed as RoutedIntent.ImportIcs).source is IcsSource.RemoteUrl)
    }

    /**
     * Round 2.18.E.10 — back-compat: the existing Phase MM
     * `strictlykeptboy://event/<id>` deep link uses scheme
     * `strictlykeptboy`, not `content`. It must fall through to
     * `Unhandled` so the Phase MM router (which today is the share
     * link receiver in MainActivity) gets a crack.
     */
    @Test fun strictlykeptboyEventDeepLinkFallsThrough() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("strictlykeptboy://event/abc:def")
        }
        val routed = CalendarIntentRouter.classify(intent)
        assertEquals(RoutedIntent.Unhandled, routed)
    }
}
