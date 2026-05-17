package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.PersonRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RuleRef
import com.eight87.strictlykeptboy.store.PromptKind
import com.eight87.strictlykeptboy.store.PromptTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.27 / Phase B.4 — verifies keeper-prompt instances route to
 * [TaskSource.KeeperPrompt], are dropped when a response file exists,
 * and never produce a row for a self-targeted prompt the boy authored
 * (single-user free-mode).
 */
class KeeperPromptProjectorTest {
    private val today = LocalDate.of(2026, 5, 17)
    private val zone = ZoneId.of("UTC")
    private val calId = "kinky-rituals"
    private val ruleId = "rule-cage-photo-sunday"
    private val keeperId = "keeper-bb"
    private val boyId = "boy-b0"

    private fun cals() = mapOf(
        calId to CalendarMeta(
            ref = CalendarRef(calId),
            repo = RepoRef("r"),
            displayName = "kinky-rituals",
            priority = 500,
            kind = CalendarKind.Regular,
            tzId = zone,
        ),
    )

    private fun recurringPromptInstance(
        date: LocalDate,
        target: PromptTarget = PromptTarget.Keeper,
        author: String = keeperId,
    ): MaterializedInstance {
        val start = ZonedDateTime.of(date, LocalTime.of(11, 0), zone)
        return MaterializedInstance(
            source = InstanceSource.RuleInstance(RuleRef(ruleId), start),
            calendar = CalendarRef(calId),
            repo = RepoRef("r"),
            originalStart = start,
            originalEnd = start.plusMinutes(10),
            effectiveStart = start,
            effectiveEnd = start.plusMinutes(10),
            title = "send the keeper a cage photo",
            body = "",
            author = PersonRef(author),
            requiresResponse = true,
            promptKind = PromptKind.Photo,
            promptTarget = target,
        )
    }

    private fun oneOffPromptInstance(date: LocalDate): MaterializedInstance {
        val start = ZonedDateTime.of(date, LocalTime.of(10, 30), zone)
        val eventId = "evt-proof-photo-$date"
        return MaterializedInstance(
            source = InstanceSource.OneOff(EventRef(eventId)),
            calendar = CalendarRef(calId),
            repo = RepoRef("r"),
            originalStart = start,
            originalEnd = start.plusMinutes(10),
            effectiveStart = start,
            effectiveEnd = start.plusMinutes(10),
            title = "send proof you're still caged",
            body = "",
            author = PersonRef(keeperId),
            requiresResponse = true,
            promptKind = PromptKind.Photo,
            promptTarget = PromptTarget.Keeper,
        )
    }

    @Test fun recurring_prompt_with_yesterdays_response_projects_today_only() {
        val yesterday = today.minusDays(1)
        val instances = listOf(
            recurringPromptInstance(yesterday),
            recurringPromptInstance(today),
        )
        val answered = setOf(yesterday)
        val out = FromEventsProjector.project(
            instances = instances,
            calendarsById = cals(),
            today = today,
            zone = zone,
            boyAuthorId = boyId,
            responseReader = { c, r ->
                if (c == calId && r == ruleId) answered else emptySet()
            },
        )
        assertEquals(1, out.size)
        assertEquals(TaskSource.KeeperPrompt, out[0].source)
        assertEquals(today, out[0].due)
    }

    @Test fun oneoff_prompt_with_no_response_projects_one_task() {
        val instances = listOf(oneOffPromptInstance(today))
        val out = FromEventsProjector.project(
            instances = instances,
            calendarsById = cals(),
            today = today,
            zone = zone,
            boyAuthorId = boyId,
            responseReader = { _, _ -> emptySet() },
        )
        assertEquals(1, out.size)
        assertEquals(TaskSource.KeeperPrompt, out[0].source)
        assertTrue(out[0].id.startsWith("keeper-prompt:"))
    }

    @Test fun closed_oneoff_prompt_projects_zero() {
        val instances = listOf(oneOffPromptInstance(today))
        val eventId = (instances[0].source as InstanceSource.OneOff).eventId.id
        val out = FromEventsProjector.project(
            instances = instances,
            calendarsById = cals(),
            today = today,
            zone = zone,
            boyAuthorId = boyId,
            responseReader = { c, r ->
                if (c == calId && r == eventId) setOf(today) else emptySet()
            },
        )
        assertTrue(out.isEmpty())
    }

    @Test fun self_targeted_prompt_authored_by_boy_is_skipped() {
        val instances = listOf(
            recurringPromptInstance(today, target = PromptTarget.Self, author = boyId),
        )
        val out = FromEventsProjector.project(
            instances = instances,
            calendarsById = cals(),
            today = today,
            zone = zone,
            boyAuthorId = boyId,
        )
        assertTrue(out.isEmpty())
    }
}
