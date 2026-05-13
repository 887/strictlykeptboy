package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Phase FFF / EC-G.1 — free-form form validation.
 *
 * Robolectric-free (pure JVM): the validator is plain Kotlin, no
 * Android dependencies.
 */
class EventDraftValidatorTest {
    private val now: OffsetDateTime = OffsetDateTime.parse("2026-05-13T12:00:00+02:00")

    @Test fun valid_draft_has_no_errors() {
        val d = EventDraft(
            title = "Lunch",
            start = now,
            end = now.plusMinutes(30),
            calendarId = "cal-1",
        )
        val errs = EventDraftValidator.validate(d)
        assertFalse(errs.any)
    }

    @Test fun empty_title_is_error() {
        val d = EventDraft(title = "  ", calendarId = "cal-1", start = now, end = now.plusMinutes(15))
        assertTrue(EventDraftValidator.validate(d).titleEmpty)
    }

    @Test fun end_not_after_start_is_error() {
        val d = EventDraft(title = "X", calendarId = "cal-1", start = now, end = now)
        assertTrue(EventDraftValidator.validate(d).endNotAfterStart)
    }

    @Test fun duration_over_24h_is_error() {
        val d = EventDraft(title = "X", calendarId = "cal-1", start = now, end = now.plusHours(25))
        assertTrue(EventDraftValidator.validate(d).durationTooLong)
    }

    @Test fun missing_calendar_is_error() {
        val d = EventDraft(title = "X", calendarId = "", start = now, end = now.plusMinutes(15))
        assertTrue(EventDraftValidator.validate(d).calendarMissing)
    }

    @Test fun custom_rrule_blank_is_error() {
        val d = EventDraft(
            title = "X", calendarId = "cal-1", start = now, end = now.plusMinutes(15),
            recurrence = RecurrencePreset.Custom, customRRule = "  ",
        )
        assertTrue(EventDraftValidator.validate(d).customRRuleBlank)
    }

    @Test fun private_flag_round_trips_through_minted_event() {
        val d = EventDraft(
            title = "X", calendarId = "cal-1", start = now, end = now.plusMinutes(15),
            private = true,
        )
        val ev = DraftToEvent.mint(d, author = "me")
        assertTrue(ev.private)
    }
}
