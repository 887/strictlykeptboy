package com.eight87.strictlykeptboy.ui.playing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round 2.16.G.1 — unit gate for the three pure helpers in
 * [QueueReorderLogic].
 *
 * Mirrors the cases the drag-reorder host exercises:
 *
 *  - [translateVisualToReal] is the identity (the queue list renders
 *    every item 1:1 since the active row is no longer hidden).
 *  - [clampMoveAwayFromActive] refuses to displace the current row
 *    and shifts past it instead.
 *  - [firstDifference] reconstructs the (from, to) reorder pair from
 *    two adjacent snapshots produced by the drag helper, supporting
 *    both "moved-down" and "moved-up" cases and rejecting size or
 *    multi-edit mismatches.
 */
class QueueReorderLogicTest {

    @Test fun `translateVisualToReal is identity`() {
        assertEquals(0, translateVisualToReal(currentIndex = -1, visual = 0))
        assertEquals(3, translateVisualToReal(currentIndex = 1, visual = 3))
        assertEquals(7, translateVisualToReal(currentIndex = 4, visual = 7))
    }

    @Test fun `clampMoveAwayFromActive passes through when no active`() {
        assertEquals(2 to 5, clampMoveAwayFromActive(currentIndex = -1, from = 2, to = 5))
    }

    @Test fun `clampMoveAwayFromActive drops move whose source is the active row`() {
        assertNull(clampMoveAwayFromActive(currentIndex = 3, from = 3, to = 5))
    }

    @Test fun `clampMoveAwayFromActive shifts destination past active when dragging upward`() {
        // from=5 to=3 with active=3 → destination shifts to 4 (past the active row)
        assertEquals(5 to 4, clampMoveAwayFromActive(currentIndex = 3, from = 5, to = 3))
    }

    @Test fun `clampMoveAwayFromActive shifts destination past active when dragging downward`() {
        // from=1 to=3 with active=3 → destination shifts to 2 (past the active row)
        assertEquals(1 to 2, clampMoveAwayFromActive(currentIndex = 3, from = 1, to = 3))
    }

    @Test fun `clampMoveAwayFromActive drops a no-op after the shift`() {
        // from=2 to=3 with active=3 → would clamp to (2 to 2), drop it.
        assertNull(clampMoveAwayFromActive(currentIndex = 3, from = 2, to = 3))
    }

    @Test fun `firstDifference detects downward move`() {
        val before = listOf("a", "b", "c", "d", "e")
        val after  = listOf("b", "c", "a", "d", "e") // "a" 0→2
        assertEquals(0 to 2, firstDifference(before, after))
    }

    @Test fun `firstDifference detects upward move`() {
        val before = listOf("a", "b", "c", "d", "e")
        val after  = listOf("a", "d", "b", "c", "e") // "d" 3→1
        assertEquals(3 to 1, firstDifference(before, after))
    }

    @Test fun `firstDifference returns null for identical lists`() {
        val list = listOf("a", "b", "c")
        assertNull(firstDifference(list, list))
    }

    @Test fun `firstDifference returns null on size mismatch`() {
        assertNull(firstDifference(listOf("a", "b"), listOf("a", "b", "c")))
    }

    @Test fun `firstDifference returns null on non-reorder edits`() {
        // Two unrelated swaps — not a single move; reject.
        val before = listOf("a", "b", "c", "d")
        val after  = listOf("b", "a", "d", "c")
        assertNull(firstDifference(before, after))
    }
}
