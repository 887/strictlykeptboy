package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.21 Phase F.5 — `groupDayBands` adjacency / opt-in / zoom
 * behaviour. Pure function — no Compose/Robolectric.
 */
class GroupedDayBandTest {

    private val cal = CalendarRef("cal-routines")
    private val otherCal = CalendarRef("cal-work")
    private val repo = RepoRef("repo-a")
    private val tz = ZoneId.of("UTC")

    private fun band(
        idSuffix: String,
        startMin: Int,
        endMin: Int,
        group: String?,
        calendar: CalendarRef = cal,
    ): DayBand {
        val date = ZonedDateTime.of(2026, 5, 16, 0, 0, 0, 0, tz)
        val start = date.plusMinutes(startMin.toLong())
        val end = date.plusMinutes(endMin.toLong())
        val mi = MaterializedInstance(
            source = InstanceSource.OneOff(EventRef("ev-$idSuffix")),
            calendar = calendar,
            repo = repo,
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = "T-$idSuffix",
            body = "",
            group = group,
        )
        return DayBand(instance = mi, priority = 500, laneIndex = 0, totalLanes = 1)
    }

    @Test fun adjacent_same_group_collapses_into_one_band() {
        val bands = listOf(
            band("a", 400, 405, "morning"),
            band("b", 406, 411, "morning"),
            band("c", 412, 417, "morning"),
        )
        val groups = groupDayBands(bands, mapOf(cal to true))
        assertEquals(1, groups.size)
        assertTrue(groups[0].isCollapsedGroup)
        assertEquals(3, groups[0].children.size)
        assertEquals("morning", groups[0].groupLabel)
    }

    @Test fun gap_above_threshold_breaks_group() {
        val bands = listOf(
            band("a", 400, 405, "morning"),
            band("b", 500, 505, "morning"), // 95 min gap
        )
        val groups = groupDayBands(bands, mapOf(cal to true))
        assertEquals(2, groups.size)
        assertFalse(groups[0].isCollapsedGroup)
        assertFalse(groups[1].isCollapsedGroup)
    }

    @Test fun different_groups_break_collapse() {
        val bands = listOf(
            band("a", 400, 405, "morning"),
            band("b", 406, 411, "midday"),
        )
        val groups = groupDayBands(bands, mapOf(cal to true))
        assertEquals(2, groups.size)
    }

    @Test fun calendar_without_meta_group_optin_never_collapses() {
        val bands = listOf(
            band("a", 400, 405, "morning"),
            band("b", 406, 411, "morning"),
        )
        val groups = groupDayBands(bands, mapOf(cal to false))
        assertEquals(2, groups.size)
        groups.forEach { assertFalse(it.isCollapsedGroup) }
    }

    @Test fun null_group_label_disables_collapse_even_when_opted_in() {
        val bands = listOf(
            band("a", 400, 405, null),
            band("b", 406, 411, null),
        )
        val groups = groupDayBands(bands, mapOf(cal to true))
        assertEquals(2, groups.size)
        groups.forEach { assertFalse(it.isCollapsedGroup) }
    }

    @Test fun cross_calendar_bands_do_not_collapse() {
        val bands = listOf(
            band("a", 400, 405, "morning", calendar = cal),
            band("b", 406, 411, "morning", calendar = otherCal),
        )
        val groups = groupDayBands(bands, mapOf(cal to true, otherCal to true))
        assertEquals(2, groups.size)
    }

    @Test fun auto_expand_threshold_is_zoom_3() {
        assertFalse(shouldAutoExpand(1))
        assertFalse(shouldAutoExpand(2))
        assertTrue(shouldAutoExpand(3))
        assertTrue(shouldAutoExpand(4))
    }

    @Test fun empty_input_returns_empty_list() {
        assertEquals(emptyList<GroupedDayBand>(), groupDayBands(emptyList(), emptyMap()))
    }
}
