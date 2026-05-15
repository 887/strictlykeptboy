package com.eight87.strictlykeptboy.system

import android.provider.CalendarContract
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.18.D.3 — field-mapping completeness for `EventInput` →
 * `ContentValues`. Timed vs all-day branches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class EventInputToContentValuesTest {

    @Test fun timedEventMappingCarriesAllColumns() {
        val tz = ZoneId.of("Europe/Berlin")
        val start = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        val input = EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("42"),
            repo = RepoRef("system/com.google/me@example.com"),
            title = "Standup",
            start = start,
            end = start.plusHours(1),
            isAllDay = false,
            emoji = "📅",
            body = "agenda",
            location = "Room 7",
            isBusy = true,
        )
        val cv = EventInputMapper.toContentValues(input, calendarId = 42L)

        assertEquals(42L, cv.getAsLong(CalendarContract.Events.CALENDAR_ID))
        // Emoji prefix lands in the title.
        assertEquals("📅 Standup", cv.getAsString(CalendarContract.Events.TITLE))
        assertEquals("agenda", cv.getAsString(CalendarContract.Events.DESCRIPTION))
        assertEquals("Room 7", cv.getAsString(CalendarContract.Events.EVENT_LOCATION))
        assertEquals(0, cv.getAsInteger(CalendarContract.Events.ALL_DAY))
        assertEquals("Europe/Berlin", cv.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
        assertEquals(start.toInstant().toEpochMilli(),
            cv.getAsLong(CalendarContract.Events.DTSTART))
        assertEquals(start.plusHours(1).toInstant().toEpochMilli(),
            cv.getAsLong(CalendarContract.Events.DTEND))
        assertEquals(
            CalendarContract.Events.STATUS_CONFIRMED,
            cv.getAsInteger(CalendarContract.Events.STATUS),
        )
        assertEquals(
            CalendarContract.Events.ACCESS_DEFAULT,
            cv.getAsInteger(CalendarContract.Events.ACCESS_LEVEL),
        )
        assertEquals(
            CalendarContract.Events.AVAILABILITY_BUSY,
            cv.getAsInteger(CalendarContract.Events.AVAILABILITY),
        )
    }

    @Test fun allDayEventUsesUtcTimezone() {
        val tz = ZoneId.of("Europe/Berlin")
        val start = ZonedDateTime.of(2026, 5, 16, 0, 0, 0, 0, tz)
        val input = EventInput(
            ref = EventRef("e2"),
            calendar = CalendarRef("9"),
            repo = RepoRef("x"),
            title = "Vacation",
            start = start,
            end = start.plusDays(1),
            isAllDay = true,
            body = "",
        )
        val cv = EventInputMapper.toContentValues(input, calendarId = 9L)
        assertEquals(1, cv.getAsInteger(CalendarContract.Events.ALL_DAY))
        // Per CalendarContract docs: all-day events MUST use UTC tz +
        // start-of-day epoch.
        assertEquals("UTC", cv.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
        val expectedStart = start.toLocalDate()
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        assertEquals(expectedStart, cv.getAsLong(CalendarContract.Events.DTSTART))
    }

    @Test fun noEmojiKeepsTitleClean() {
        val tz = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        val input = EventInput(
            ref = EventRef("e3"),
            calendar = CalendarRef("1"),
            repo = RepoRef("r"),
            title = "Bare title",
            start = start,
            end = start.plusHours(1),
            emoji = null,
        )
        val cv = EventInputMapper.toContentValues(input, calendarId = 1L)
        assertEquals("Bare title", cv.getAsString(CalendarContract.Events.TITLE))
    }

    @Test fun busyFalseSurfacesAvailabilityFree() {
        val tz = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        val input = EventInput(
            ref = EventRef("e4"),
            calendar = CalendarRef("1"),
            repo = RepoRef("r"),
            title = "Free slot",
            start = start,
            end = start.plusMinutes(30),
            isBusy = false,
        )
        val cv = EventInputMapper.toContentValues(input, calendarId = 1L)
        assertEquals(
            CalendarContract.Events.AVAILABILITY_FREE,
            cv.getAsInteger(CalendarContract.Events.AVAILABILITY),
        )
    }

    @Test fun rruleAbsentInOneOffMapping() {
        val tz = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        val input = EventInput(
            ref = EventRef("e5"),
            calendar = CalendarRef("1"),
            repo = RepoRef("r"),
            title = "x",
            start = start,
            end = start.plusHours(1),
        )
        val cv = EventInputMapper.toContentValues(input, calendarId = 1L)
        assertNull(cv.get(CalendarContract.Events.RRULE))
        assertNotNull(cv.get(CalendarContract.Events.DTEND))
    }
}
