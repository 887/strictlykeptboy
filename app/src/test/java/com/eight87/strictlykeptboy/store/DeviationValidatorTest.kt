package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class DeviationValidatorTest {

    private fun header(id: String = "test-id"): EntityHeader = EntityHeader(
        id = id,
        createdAt = "2026-05-12T07:00:00+00:00",
        updatedAt = "2026-05-12T07:00:00+00:00",
        author = "device",
    )

    private fun mk(
        targetId: String = "ev1",
        date: String = "2026-05-12",
        kind: String = "skipped",
        at: String = "2026-05-12T07:30:00+00:00",
        subbeats: List<String> = emptyList(),
    ): Deviation = Deviation(
        header = header("dev-$kind"),
        targetId = targetId,
        instanceDate = date,
        devKind = kind,
        at = at,
        subbeatsCompleted = subbeats,
    )

    private val now = OffsetDateTime.parse("2026-05-12T08:00:00+00:00")

    @Test fun acceptsValidKinds() {
        for (k in DeviationValidator.VALID_KINDS) {
            val res = DeviationValidator.validate(mk(kind = k), now)
            assertEquals("accepts $k", DeviationValidator.Result.Ok, res)
        }
    }

    @Test fun rejectsCompletedKind() {
        val res = DeviationValidator.validate(mk(kind = "completed"), now)
        assertTrue(res is DeviationValidator.Result.Error.InvalidKind)
    }

    @Test fun rejectsFutureAt() {
        val future = "2026-05-12T09:00:00+00:00"
        val res = DeviationValidator.validate(mk(at = future), now)
        assertTrue(res is DeviationValidator.Result.Error.FutureAt)
    }

    @Test fun rejectsSubbeatsOnSkipped() {
        val res = DeviationValidator.validate(
            mk(kind = "skipped", subbeats = listOf("upper-left-molars")),
            now,
        )
        assertTrue(res is DeviationValidator.Result.Error.SubbeatsOnNonPartial)
    }

    @Test fun acceptsSubbeatsOnPartial() {
        val res = DeviationValidator.validate(
            mk(kind = "partial", subbeats = listOf("upper-left-molars")),
            now,
        )
        assertEquals(DeviationValidator.Result.Ok, res)
    }

    @Test fun rejectsUnknownTargetWhenSetProvided() {
        val res = DeviationValidator.validate(
            mk(targetId = "missing"),
            now,
            knownTargets = setOf("ev1" to LocalDate.parse("2026-05-12")),
        )
        assertTrue(res is DeviationValidator.Result.Error.UnknownTarget)
    }

    @Test fun acceptsKnownTarget() {
        val res = DeviationValidator.validate(
            mk(targetId = "ev1"),
            now,
            knownTargets = setOf("ev1" to LocalDate.parse("2026-05-12")),
        )
        assertEquals(DeviationValidator.Result.Ok, res)
    }
}
