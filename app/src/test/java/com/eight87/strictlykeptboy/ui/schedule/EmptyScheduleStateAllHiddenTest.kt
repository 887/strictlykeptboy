package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.5.D.3 — the new fourth empty-state kind fires when repos
 * exist but every single one has `showOnSchedule = false`.
 */
class EmptyScheduleStateAllHiddenTest {

    @Test fun allReposHiddenFires() {
        assertEquals(
            EmptyScheduleKind.AllReposHidden,
            selectEmptyKind(
                repoCount = 3,
                calendarCount = 5,
                activeCalendarCount = 5,
                showOnScheduleRepoCount = 0,
            ),
        )
    }

    @Test fun atLeastOneShownFallsThroughToExistingStates() {
        // With one shown repo + active calendars, the legacy NoEvents
        // empty state still wins.
        assertEquals(
            EmptyScheduleKind.NoEvents,
            selectEmptyKind(
                repoCount = 3,
                calendarCount = 5,
                activeCalendarCount = 5,
                showOnScheduleRepoCount = 1,
            ),
        )
    }

    @Test fun noReposBeatsAllHidden() {
        // repoCount=0 → NoRepos regardless of the new arg.
        assertEquals(
            EmptyScheduleKind.NoRepos,
            selectEmptyKind(
                repoCount = 0,
                calendarCount = 0,
                activeCalendarCount = 0,
                showOnScheduleRepoCount = 0,
            ),
        )
    }
}
