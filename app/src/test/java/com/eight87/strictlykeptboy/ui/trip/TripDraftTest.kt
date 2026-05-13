package com.eight87.strictlykeptboy.ui.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripDraftTest {

    @Test fun `empty draft is incomplete and reports no user choices`() {
        val d = TripDraft()
        assertFalse(d.hasUserChoices)
        assertFalse(d.isComplete)
        assertEquals(0, d.durationDays)
    }

    @Test fun `complete draft passes validation`() {
        val d = TripDraft(
            name = "Sicily 2026",
            destination = "Catania",
            startDate = LocalDate.of(2026, 6, 12),
            endDate = LocalDate.of(2026, 6, 19),
            transport = TransportMode.Flight,
            travelerCount = 2,
        )
        assertTrue(d.hasUserChoices)
        assertTrue(d.isComplete)
        assertEquals(8, d.durationDays) // inclusive
    }

    @Test fun `end before start fails completion`() {
        val d = TripDraft(
            name = "x",
            startDate = LocalDate.of(2026, 6, 19),
            endDate = LocalDate.of(2026, 6, 12),
        )
        assertFalse(d.isComplete)
    }

    @Test fun `zero travelers fails completion`() {
        val d = TripDraft(
            name = "x",
            startDate = LocalDate.of(2026, 6, 12),
            endDate = LocalDate.of(2026, 6, 19),
            travelerCount = 0,
        )
        assertFalse(d.isComplete)
    }

    @Test fun `blank name fails completion`() {
        val d = TripDraft(
            startDate = LocalDate.of(2026, 6, 12),
            endDate = LocalDate.of(2026, 6, 19),
        )
        assertFalse(d.isComplete)
    }

    @Test fun `hasUserChoices flips on any field`() {
        assertTrue(TripDraft(name = "a").hasUserChoices)
        assertTrue(TripDraft(destination = "x").hasUserChoices)
        assertTrue(TripDraft(transport = TransportMode.Flight).hasUserChoices)
        assertTrue(TripDraft(packKinkKit = true).hasUserChoices)
        assertTrue(TripDraft(includeVacationDaily = false).hasUserChoices)
        assertTrue(TripDraft(travelerCount = 3).hasUserChoices)
    }
}
