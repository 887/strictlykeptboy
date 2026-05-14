package com.eight87.strictlykeptboy.ui.scaffold

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.16.G.1 — unit-test the flick-commit decision pulled out of
 * [NowPlayingSheetHost]'s drag-settle handler.
 *
 * The full draggable + nestedScroll integration is hard to drive in
 * Robolectric, so the math itself is the testable surface. Cases
 * mirror the locked behavior:
 *
 *   threshold = 0.05f (5% of sheet travel, verbatim from tonearmboy)
 *   moved > +threshold  → 1f (open)
 *   moved < -threshold  → 0f (close)
 *   otherwise           → position fallback (end >= 0.5f → 1f else 0f)
 */
class SheetHostFlickCommitTest {

    @Test fun `insufficient upward move from closed stays closed`() {
        assertEquals(0f, flickCommitTarget(start = 0f, end = 0.04f), 0f)
    }

    @Test fun `decisive upward flick commits to open`() {
        assertEquals(1f, flickCommitTarget(start = 0f, end = 0.06f), 0f)
    }

    @Test fun `decisive downward flick commits to close`() {
        assertEquals(0f, flickCommitTarget(start = 1f, end = 0.94f), 0f)
    }

    @Test fun `significant upward move opens`() {
        assertEquals(1f, flickCommitTarget(start = 0f, end = 0.5f), 0f)
    }

    @Test fun `insufficient move below half-position falls back to close`() {
        assertEquals(0f, flickCommitTarget(start = 0.4f, end = 0.42f), 0f)
    }

    @Test fun `insufficient move at-or-above half-position falls back to open`() {
        assertEquals(1f, flickCommitTarget(start = 0.6f, end = 0.62f), 0f)
    }
}
