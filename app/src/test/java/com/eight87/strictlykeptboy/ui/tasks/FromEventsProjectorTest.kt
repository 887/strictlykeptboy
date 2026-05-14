package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RuleRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.1.D.8 — verifies the FromEvents projector emits same-day,
 * non-timebox instances as TaskSource.FromEvents items.
 */
class FromEventsProjectorTest {
    private val today = LocalDate.of(2026, 5, 13)
    private val zone = ZoneId.of("UTC")

    private fun instance(
        calId: String,
        title: String,
        date: LocalDate = today,
    ): MaterializedInstance {
        val start = ZonedDateTime.of(date, java.time.LocalTime.of(10, 0), zone)
        return MaterializedInstance(
            source = InstanceSource.RuleInstance(RuleRef("rule-$calId"), start),
            calendar = CalendarRef(calId),
            repo = RepoRef("r"),
            originalStart = start,
            originalEnd = start.plusHours(1),
            effectiveStart = start,
            effectiveEnd = start.plusHours(1),
            title = title,
            body = "",
        )
    }

    private fun meta(id: String, kind: CalendarKind = CalendarKind.Regular) = CalendarMeta(
        ref = CalendarRef(id),
        repo = RepoRef("r"),
        displayName = "cal-$id",
        priority = 500,
        kind = kind,
        tzId = zone,
    )

    @Test fun regular_chores_become_tasks() {
        val instances = listOf(instance("chore", "wash dishes"))
        val cals = mapOf("chore" to meta("chore"))
        val out = FromEventsProjector.project(instances, cals, today, zone)
        assertEquals(1, out.size)
        assertEquals(TaskSource.FromEvents, out[0].source)
        assertEquals("wash dishes", out[0].title)
        assertEquals(today, out[0].due)
    }

    @Test fun timebox_calendars_skipped() {
        val instances = listOf(instance("focus", "deep work"))
        val cals = mapOf("focus" to meta("focus", kind = CalendarKind.Timebox))
        val out = FromEventsProjector.project(instances, cals, today, zone)
        assertTrue(out.isEmpty())
    }

    @Test fun other_days_skipped() {
        val instances = listOf(instance("chore", "tomorrow", date = today.plusDays(1)))
        val cals = mapOf("chore" to meta("chore"))
        val out = FromEventsProjector.project(instances, cals, today, zone)
        assertTrue(out.isEmpty())
    }
}
