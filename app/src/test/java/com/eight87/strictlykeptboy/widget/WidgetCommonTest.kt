package com.eight87.strictlykeptboy.widget

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.widget.common.Countdown
import com.eight87.strictlykeptboy.widget.common.WidgetLayoutSize
import com.eight87.strictlykeptboy.widget.common.WidgetPrivacy
import com.eight87.strictlykeptboy.widget.common.WidgetTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class WidgetCommonTest {

    @Test fun layout_size_picks_bands() {
        assertEquals(WidgetLayoutSize.Size2x1, WidgetLayoutSize.pick(60, 40))
        assertEquals(WidgetLayoutSize.Size4x2, WidgetLayoutSize.pick(240, 120))
        assertEquals(WidgetLayoutSize.Size4x4, WidgetLayoutSize.pick(260, 260))
    }

    @Test fun countdown_components() {
        val c = Countdown(totalMinutes = 1440L * 2 + 60L * 3 + 7L)
        assertEquals(2L, c.days)
        assertEquals(3L, c.hours)
        assertEquals(7L, c.minutes)
        assertFalse(c.isPastDue)
    }

    @Test fun countdown_past_due_carries_sign() {
        val c = Countdown(totalMinutes = -1440L)
        assertTrue(c.isPastDue)
        assertEquals(1L, c.days)
    }

    @Test fun ddhhmm_formats_with_leading_zeros() {
        val s = WidgetTimeFormat.ddhhmm(Countdown(1440L + 60L + 5L))
        assertEquals("01:01:05", s)
    }

    @Test fun bucket_today_tomorrow_in_days_past_due() {
        assertEquals(WidgetTimeFormat.Bucket.Today, WidgetTimeFormat.bucket(Countdown(30L)))
        assertEquals(WidgetTimeFormat.Bucket.Tomorrow, WidgetTimeFormat.bucket(Countdown(1440L + 1L)))
        assertEquals(WidgetTimeFormat.Bucket.InDays, WidgetTimeFormat.bucket(Countdown(1440L * 5L)))
        assertEquals(WidgetTimeFormat.Bucket.DaysAgo, WidgetTimeFormat.bucket(Countdown(-1L)))
    }

    @Test fun exact_and_coarse_remaining() {
        assertEquals("0m", WidgetTimeFormat.exactRemaining(0))
        assertEquals("5m", WidgetTimeFormat.exactRemaining(5))
        assertEquals("1h 30m", WidgetTimeFormat.exactRemaining(90))
        assertEquals("<5m", WidgetTimeFormat.coarseRemaining(2))
        assertEquals("<30m", WidgetTimeFormat.coarseRemaining(20))
        assertEquals("3h+", WidgetTimeFormat.coarseRemaining(200))
    }

    @Test fun privacy_redacts_per_event_private() {
        val i = instance(isPrivate = true)
        assertTrue(WidgetPrivacy.shouldRedact(i, WidgetPrivacy.Surface.Home) { false })
        assertTrue(WidgetPrivacy.shouldRedact(i, WidgetPrivacy.Surface.Lockscreen) { false })
    }

    @Test fun privacy_lockscreen_redacts_default_private_calendar() {
        val i = instance(isPrivate = false)
        assertFalse(WidgetPrivacy.shouldRedact(i, WidgetPrivacy.Surface.Home) { true })
        assertTrue(WidgetPrivacy.shouldRedact(i, WidgetPrivacy.Surface.Lockscreen) { true })
    }

    @Test fun privacy_home_keeps_non_private_visible() {
        val i = instance(isPrivate = false)
        assertFalse(WidgetPrivacy.shouldRedact(i, WidgetPrivacy.Surface.Home) { false })
    }

    private fun instance(isPrivate: Boolean): MaterializedInstance {
        val start = ZonedDateTime.parse("2026-06-21T10:00:00+02:00[Europe/Berlin]")
        val end = start.plusHours(1)
        return MaterializedInstance(
            source = InstanceSource.OneOff(EventRef("evt-1")),
            calendar = CalendarRef("cal-1"),
            repo = RepoRef("repo-1"),
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = "Sir's visit",
            body = "",
            isPrivate = isPrivate,
        )
    }
}
