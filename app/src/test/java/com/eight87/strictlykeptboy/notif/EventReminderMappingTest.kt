package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Reminder
import com.eight87.strictlykeptboy.store.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Phase 2.1.F.1 — pure-Kotlin tests for the Reminder[] → ReminderInput
 * mapping. The scheduler is not exercised here; that's covered by
 * `EventReminderSchedulerTest`. The contract under test is the lead-time
 * token format and the array-vs-legacy precedence.
 */
class EventReminderMappingTest {

    private fun event(
        reminders: List<String> = emptyList(),
        legacy: List<String> = emptyList(),
    ): Event {
        val now = OffsetDateTime.now(ZoneOffset.UTC).toString()
        return Event(
            header = EntityHeader(id = "ev1", createdAt = now, updatedAt = now, author = "me"),
            title = "Dentist",
            start = now,
            end = now,
            calendarId = "cal-a",
            notifications = legacy,
        )
        // `reminders` is unused here; mapping consumes a `List<Reminder>`
        // supplier from the caller's TOML parse (see frontmatter overload).
    }

    @Test fun singleReminderBlockProducesSingleLeadTime() {
        val r = listOf(Reminder(offset = "-PT30M", kind = ReminderKind.PreEvent))
        val leads = EventReminderMapping.leadTimesFor(event(), r)
        assertEquals(listOf("30m"), leads)
    }

    @Test fun preEventTokensCoverMinutesHoursDays() {
        val r = listOf(
            Reminder("-PT15M", ReminderKind.PreEvent),
            Reminder("-PT1H", ReminderKind.PreEvent),
            Reminder("-PT2H", ReminderKind.PreEvent),
            Reminder("-P1D", ReminderKind.PreEvent),
        )
        val leads = EventReminderMapping.leadTimesFor(event(), r)
        assertEquals(listOf("15m", "1h", "2h", "1d"), leads)
    }

    @Test fun atStartOffsetEmitsZero() {
        val r = listOf(Reminder("0", ReminderKind.AtStart))
        assertEquals(listOf("0"), EventReminderMapping.leadTimesFor(event(), r))
    }

    @Test fun postEventOffsetsAreDroppedInV1() {
        val r = listOf(
            Reminder("-PT30M", ReminderKind.PreEvent),
            Reminder("PT4H", ReminderKind.PostEventCheckin),
        )
        assertEquals(listOf("30m"), EventReminderMapping.leadTimesFor(event(), r))
    }

    @Test fun legacyNotificationsArrayUsedWhenReminderArrayEmpty() {
        val e = event(legacy = listOf("15m", "1h"))
        assertEquals(listOf("15m", "1h"), EventReminderMapping.leadTimesFor(e))
    }

    @Test fun reminderArrayTakesPrecedenceOverLegacy() {
        val e = event(legacy = listOf("99m"))
        val r = listOf(Reminder("-PT30M", ReminderKind.PreEvent))
        assertEquals(listOf("30m"), EventReminderMapping.leadTimesFor(e, r))
    }

    @Test fun emptyReminderAndLegacyReturnsEmptyList() {
        assertEquals(emptyList<String>(), EventReminderMapping.leadTimesFor(event()))
    }

    @Test fun fromEventReturnsNullWhenNoReminders() {
        assertNull(EventReminderMapping.fromEvent(event(), repoId = "r1"))
    }

    @Test fun fromEventBuildsScheduledInputWithLegacy() {
        val e = event(legacy = listOf("15m", "1h"))
        val input = EventReminderMapping.fromEvent(e, repoId = "r1")
        assertNotNull(input)
        assertEquals("r1", input!!.repoId)
        assertEquals("ev1", input.eventId)
        assertEquals("cal-a", input.calendarId)
        assertEquals(listOf("15m", "1h"), input.leadTimes)
    }

    @Test fun malformedOffsetIsDropped() {
        val r = listOf(
            Reminder("not-a-duration", ReminderKind.PreEvent),
            Reminder("-PT30M", ReminderKind.PreEvent),
        )
        // Reminder.fromTable would have rejected malformed, but the mapping
        // is defensive anyway: only the valid one survives.
        assertEquals(listOf("30m"), EventReminderMapping.leadTimesFor(event(), r))
    }

    @Test fun toLeadTimeTokenSamples() {
        assertEquals("30m", EventReminderMapping.toLeadTimeToken("-PT30M"))
        assertEquals("1h", EventReminderMapping.toLeadTimeToken("-PT1H"))
        assertEquals("1d", EventReminderMapping.toLeadTimeToken("-P1D"))
        assertEquals("0", EventReminderMapping.toLeadTimeToken("0"))
        assertEquals(null, EventReminderMapping.toLeadTimeToken("PT4H"))
        assertEquals(null, EventReminderMapping.toLeadTimeToken("garbage"))
    }
}
