package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.zdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/**
 * Round 2.25 Phase A.3 — coverage for the pure
 * [NowNextResolver.derive] + [NowNextResolver.formatRelative] helpers.
 */
class NowNextResolverTest {

    private fun mi(
        id: String,
        start: String,
        end: String,
        title: String = id,
        cal: String = "c1",
        emoji: String? = null,
    ) = MaterializedInstance(
        source = InstanceSource.OneOff(EventRef(id)),
        calendar = CalendarRef(cal),
        repo = RepoRef("r1"),
        originalStart = zdt(start),
        originalEnd = zdt(end),
        effectiveStart = zdt(start),
        effectiveEnd = zdt(end),
        title = title,
        body = "",
        emoji = emoji,
    )

    @Test fun empty_returns_both_null() {
        val snap = NowNextResolver.derive(emptyList(), at = zdt("2026-05-17T09:00:00").toInstant())
        assertNull(snap.now)
        assertNull(snap.next)
    }

    @Test fun all_past_returns_both_null() {
        val today = listOf(
            mi("a", "2026-05-17T07:00:00", "2026-05-17T07:30:00"),
            mi("b", "2026-05-17T08:00:00", "2026-05-17T08:30:00"),
        )
        val snap = NowNextResolver.derive(today, at = zdt("2026-05-17T20:00:00").toInstant())
        assertNull(snap.now)
        assertNull(snap.next)
    }

    @Test fun mid_event_now_is_set_next_is_following() {
        val today = listOf(
            mi("a", "2026-05-17T09:00:00", "2026-05-17T10:00:00"),
            mi("b", "2026-05-17T11:00:00", "2026-05-17T11:30:00"),
        )
        val snap = NowNextResolver.derive(today, at = zdt("2026-05-17T09:30:00").toInstant())
        assertEquals("a", snap.now?.title)
        assertEquals("b", snap.next?.title)
    }

    @Test fun no_current_no_next_today_falls_through_to_null_when_tomorrow_empty() {
        val today = listOf(
            mi("a", "2026-05-17T07:00:00", "2026-05-17T08:00:00"),
        )
        val snap = NowNextResolver.derive(today, at = zdt("2026-05-17T22:00:00").toInstant())
        assertNull(snap.now)
        assertNull(snap.next)
    }

    @Test fun next_is_tomorrow_when_today_has_none_after_now() {
        val today = listOf(
            mi("a", "2026-05-17T07:00:00", "2026-05-17T08:00:00"),
        )
        val tomorrow = listOf(
            mi("t", "2026-05-18T06:30:00", "2026-05-18T07:00:00", title = "brush teeth"),
        )
        val snap = NowNextResolver.derive(today, at = zdt("2026-05-17T23:00:00").toInstant(), tomorrow = tomorrow)
        assertNull(snap.now)
        assertEquals("brush teeth", snap.next?.title)
    }

    @Test fun next_picks_earliest_grouped_band_start() {
        // Two events sharing the same group label, consecutive. Next at 09:30 picks the earliest.
        val today = listOf(
            mi("g1", "2026-05-17T10:00:00", "2026-05-17T10:30:00", title = "morning routine pt1"),
            mi("g2", "2026-05-17T10:30:00", "2026-05-17T11:00:00", title = "morning routine pt2"),
        )
        val snap = NowNextResolver.derive(today, at = zdt("2026-05-17T09:30:00").toInstant())
        assertEquals("morning routine pt1", snap.next?.title)
    }

    @Test fun now_inside_grouped_band_points_at_active_segment_next_at_following() {
        // While inside g1's window, "now" is g1; "next" is g2.
        val today = listOf(
            mi("g1", "2026-05-17T10:00:00", "2026-05-17T10:30:00", title = "morning routine pt1"),
            mi("g2", "2026-05-17T10:30:00", "2026-05-17T11:00:00", title = "morning routine pt2"),
        )
        val snap = NowNextResolver.derive(today, at = zdt("2026-05-17T10:10:00").toInstant())
        assertEquals("morning routine pt1", snap.now?.title)
        assertEquals("morning routine pt2", snap.next?.title)
    }

    @Test fun multi_calendar_next_is_ordered_by_start_not_calendar() {
        // Cal "b" event starts earlier than cal "a" event; next picks "b".
        val today = listOf(
            mi("a-evt", "2026-05-17T15:00:00", "2026-05-17T15:30:00", cal = "a"),
            mi("b-evt", "2026-05-17T13:00:00", "2026-05-17T13:30:00", cal = "b"),
        )
        val snap = NowNextResolver.derive(today, at = zdt("2026-05-17T12:00:00").toInstant())
        assertNotNull(snap.next)
        assertEquals("b-evt", snap.next?.title)
    }

    @Test fun formatRelative_buckets() {
        assertEquals("now", NowNextResolver.formatRelative(Duration.ofSeconds(30)))
        assertEquals("in 12 min", NowNextResolver.formatRelative(Duration.ofMinutes(12)))
        assertEquals("in 2h 15m", NowNextResolver.formatRelative(Duration.ofMinutes(135)))
        assertEquals("in 2h", NowNextResolver.formatRelative(Duration.ofHours(2)))
        assertEquals("in 3d 2h", NowNextResolver.formatRelative(Duration.ofHours(74)))
        assertEquals("in 5d", NowNextResolver.formatRelative(Duration.ofDays(5)))
    }
}
