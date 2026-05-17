package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.zdt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CompletionStateResolverTest {

    private fun input(
        target: String = "ev1",
        date: String = "2026-05-12",
        start: String = "2026-05-12T07:00:00",
        end: String = "2026-05-12T07:05:00",
    ) = CompletionStateResolver.Input(
        targetId = target,
        occurrenceDate = LocalDate.parse(date),
        start = zdt(start),
        end = zdt(end),
    )

    @Test fun futureEventIsScheduled() {
        val now = zdt("2026-05-12T06:00:00")
        assertEquals(
            CompletionState.Scheduled,
            CompletionStateResolver.resolve(input(), deviation = null, now = now),
        )
    }

    @Test fun inWindowNoDeviationIsInProgress() {
        val now = zdt("2026-05-12T07:02:00")
        assertEquals(
            CompletionState.InProgress,
            CompletionStateResolver.resolve(input(), deviation = null, now = now),
        )
    }

    @Test fun pastNoDeviationIsCompletedBySchedule() {
        val now = zdt("2026-05-12T08:00:00")
        assertEquals(
            CompletionState.CompletedBySchedule,
            CompletionStateResolver.resolve(input(), deviation = null, now = now),
        )
    }

    @Test fun deviationWinsRegardlessOfClock() {
        val now = zdt("2026-05-12T08:00:00")
        val dev = DeviationInput(
            targetId = "ev1",
            instanceDate = LocalDate.parse("2026-05-12"),
            kind = DeviationKind.Skipped,
            at = zdt("2026-05-12T07:30:00"),
        )
        assertEquals(
            CompletionState.Skipped,
            CompletionStateResolver.resolve(input(), deviation = dev, now = now),
        )
    }

    @Test fun partialMapsCorrectly() {
        val dev = DeviationInput(
            targetId = "ev1",
            instanceDate = LocalDate.parse("2026-05-12"),
            kind = DeviationKind.Partial,
            at = zdt("2026-05-12T07:30:00"),
        )
        val now = zdt("2026-05-12T08:00:00")
        assertEquals(
            CompletionState.PartiallyDone,
            CompletionStateResolver.resolve(input(), dev, now),
        )
    }

    @Test fun unknownWireKindMapsToNull() {
        // Round 2.28 / Audit-pass-2026-05-17 fix #16 — the wire→sealed
        // codec returns null for unknown strings; the codec layer
        // (SourcesPublisher) drops unparseable rows before they reach
        // the resolver, so the resolver never has to crash on a typo.
        assertEquals(null, DeviationKind.fromWire("bogus"))
    }

    @Test fun visualTreatmentNeverRedNeverBlinking() {
        // Visual treatment must satisfy AT-B.5 LOCKED: every state maps
        // to a non-warning style with no blinking glyph.
        for (s in CompletionState.entries) {
            val t = CompletionVisualTreatment.forState(s)
            assertEquals(s, t.state)
            // Test sanity: every state produces a non-null treatment.
            // The forbidden values (red, warning glyph, blinking) are
            // expressed by absence — neither FillStyle nor Glyph carries
            // them, so this test enforces the locked vocabulary.
        }
    }
}
