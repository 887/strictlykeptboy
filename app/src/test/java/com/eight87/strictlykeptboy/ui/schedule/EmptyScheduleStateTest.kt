package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.2.C.8 — pure-selector unit tests for the three-state empty
 * schedule branch.
 *
 * The composable is a thin renderer over [selectEmptyKind]; we test the
 * selector here so the test stays Compose-runtime-free.
 */
class EmptyScheduleStateTest {

    @Test
    fun `no repos configured maps to NoRepos`() {
        assertEquals(
            EmptyScheduleKind.NoRepos,
            selectEmptyKind(repoCount = 0, calendarCount = 0, activeCalendarCount = 0),
        )
    }

    @Test
    fun `no repos still NoRepos even if cal counts somehow non-zero`() {
        // Defensive — shouldn't happen, but if repoCount is zero the
        // user clearly hasn't onboarded; NoRepos wins.
        assertEquals(
            EmptyScheduleKind.NoRepos,
            selectEmptyKind(repoCount = 0, calendarCount = 5, activeCalendarCount = 3),
        )
    }

    @Test
    fun `repos but zero calendars maps to NoActiveCalendars`() {
        assertEquals(
            EmptyScheduleKind.NoActiveCalendars,
            selectEmptyKind(repoCount = 1, calendarCount = 0, activeCalendarCount = 0),
        )
    }

    @Test
    fun `repos and calendars but none active maps to NoActiveCalendars`() {
        assertEquals(
            EmptyScheduleKind.NoActiveCalendars,
            selectEmptyKind(repoCount = 2, calendarCount = 4, activeCalendarCount = 0),
        )
    }

    @Test
    fun `repos and active calendars maps to NoEvents`() {
        assertEquals(
            EmptyScheduleKind.NoEvents,
            selectEmptyKind(repoCount = 1, calendarCount = 3, activeCalendarCount = 2),
        )
    }

    @Test
    fun `single repo single active calendar maps to NoEvents`() {
        assertEquals(
            EmptyScheduleKind.NoEvents,
            selectEmptyKind(repoCount = 1, calendarCount = 1, activeCalendarCount = 1),
        )
    }

    @Test
    fun `negative counts are tolerated and treated as zero`() {
        assertEquals(
            EmptyScheduleKind.NoRepos,
            selectEmptyKind(repoCount = -1, calendarCount = 0, activeCalendarCount = 0),
        )
        assertEquals(
            EmptyScheduleKind.NoActiveCalendars,
            selectEmptyKind(repoCount = 1, calendarCount = -1, activeCalendarCount = -1),
        )
    }

    @Test
    fun `initials helper handles single token, dashed, and empty`() {
        assertEquals("A", initialsForAuthor("alex"))
        assertEquals("AS", initialsForAuthor("alex-887"))
        assertEquals("AS", initialsForAuthor("alex_887"))
        assertEquals("?", initialsForAuthor(""))
    }
}
