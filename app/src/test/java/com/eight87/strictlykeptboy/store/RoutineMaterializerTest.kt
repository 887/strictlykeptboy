package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Phase XX.11 / AT-K.5 + AT-K.6 — routine materialization.
 *
 * Sequential test (AT-K.5): three atomics at 5+10+3 = 18 min starting
 * at 07:00 → 07:00/07:05/07:15, no overlap, all carry `materialized_from = morning` upstream.
 *
 * Overlap test (AT-K.6): pre-populate target window with a conflicting
 * existing slot → plan surfaces conflicts; `shiftPast` re-runs after
 * the conflict.
 */
class RoutineMaterializerTest {

    private val start7 = OffsetDateTime.parse("2026-05-13T07:00:00Z")

    private val three = listOf(
        RoutineMaterializer.Entry(sourceEventId = "brush", title = "Brush", durationMinutes = 5),
        RoutineMaterializer.Entry(sourceEventId = "shower", title = "Shower", durationMinutes = 10),
        RoutineMaterializer.Entry(sourceEventId = "deo", title = "Deodorant", durationMinutes = 3),
    )

    @Test fun sequentialPlacementBackToBack() {
        val plan = RoutineMaterializer.plan(three, start7)
        assertEquals(3, plan.planned.size)
        assertEquals(start7, plan.planned[0].start)
        assertEquals(start7.plusMinutes(5), plan.planned[0].end)
        assertEquals(start7.plusMinutes(5), plan.planned[1].start)
        assertEquals(start7.plusMinutes(15), plan.planned[1].end)
        assertEquals(start7.plusMinutes(15), plan.planned[2].start)
        assertEquals(start7.plusMinutes(18), plan.planned[2].end)
        assertFalse(plan.hasConflicts)
    }

    @Test fun overlapWithExistingProducesConflicts() {
        val existing = listOf(
            RoutineMaterializer.ExistingSlot(start7.plusMinutes(6), start7.plusMinutes(20)),
        )
        val plan = RoutineMaterializer.plan(three, start7, existing)
        assertTrue("expected conflicts", plan.hasConflicts)
        // brush (07:00-07:05) doesn't overlap; shower (07:05-07:15) and
        // deo (07:15-07:18) both touch the 07:06-07:20 existing slot.
        assertEquals(2, plan.conflicts.size)
    }

    @Test fun shiftPastClearsOverlap() {
        val existing = listOf(
            RoutineMaterializer.ExistingSlot(start7, start7.plusMinutes(10)),
        )
        val plan = RoutineMaterializer.shiftPast(three, start7, existing)
        assertFalse(plan.hasConflicts)
        // First planned slot moved past the existing window's end.
        assertTrue(!plan.planned.first().start.isBefore(start7.plusMinutes(10)))
    }

    @Test fun subbeatsCarryOver() {
        val entry = RoutineMaterializer.Entry(
            sourceEventId = "brush", title = "Brush", durationMinutes = 5,
            subbeats = listOf(
                AtomicTemplateSubbeat("a", 25),
                AtomicTemplateSubbeat("b", 25),
            ),
        )
        val plan = RoutineMaterializer.plan(listOf(entry), start7)
        assertEquals(2, plan.planned.single().subbeats.size)
        assertEquals("a", plan.planned.single().subbeats[0].label)
    }
}
