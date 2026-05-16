package com.eight87.strictlykeptboy.system

import android.provider.CalendarContract
import com.eight87.strictlykeptboy.resolver.ExternalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.18.J.10 — exhaustive editability gating across the documented
 * `CAL_ACCESS_*` band.
 *
 * `WritePermissionGateTest` covers the WRITE_CALENDAR permission gate.
 * This test pins the read-only / contributor / owner band of
 * [CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL] against the
 * pure decision in [ExternalEventEditRouter]:
 *
 *   - `CAL_ACCESS_NONE`        (   0) → ReadOnlyExternal
 *   - `CAL_ACCESS_FREEBUSY`    ( 100) → ReadOnlyExternal
 *   - `CAL_ACCESS_READ`        ( 200) → ReadOnlyExternal
 *   - `CAL_ACCESS_RESPOND`     ( 300) → ReadOnlyExternal
 *   - `CAL_ACCESS_OVERRIDE`    ( 400) → ReadOnlyExternal
 *   - `CAL_ACCESS_CONTRIBUTOR` ( 500) → WritableExternal
 *   - `CAL_ACCESS_EDITOR`      ( 600) → WritableExternal
 *   - `CAL_ACCESS_OWNER`       ( 700) → WritableExternal
 *   - `CAL_ACCESS_ROOT`        ( 800) → WritableExternal
 *
 * The cutoff at 500 = `CAL_ACCESS_CONTRIBUTOR` is the documented
 * Android contract — anything below cannot insert / update / delete
 * events on the calendar.
 */
class AccessLevelEditabilityTest {

    @Test fun readOnlyBandRoutesToReadOnlyExternal() {
        val readOnlyLevels = listOf(
            CalendarContract.Calendars.CAL_ACCESS_NONE,
            CalendarContract.Calendars.CAL_ACCESS_FREEBUSY,
            CalendarContract.Calendars.CAL_ACCESS_READ,
            CalendarContract.Calendars.CAL_ACCESS_RESPOND,
            CalendarContract.Calendars.CAL_ACCESS_OVERRIDE,
        )
        for (level in readOnlyLevels) {
            val src = ExternalSource(
                accountType = "com.google",
                accountName = "alex@example.com",
                eventId = 1L,
                accessLevel = level,
            )
            val mode = ExternalEventEditRouter.decide(src)
            assertTrue(
                "accessLevel=$level (below CONTRIBUTOR) should be read-only, got $mode",
                mode is EditorMode.ReadOnlyExternal,
            )
        }
    }

    @Test fun contributorAndAboveRoutesToWritableExternal() {
        val writableLevels = listOf(
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
            CalendarContract.Calendars.CAL_ACCESS_EDITOR,
            CalendarContract.Calendars.CAL_ACCESS_OWNER,
            CalendarContract.Calendars.CAL_ACCESS_ROOT,
        )
        for (level in writableLevels) {
            val src = ExternalSource(
                accountType = "com.google",
                accountName = "alex@example.com",
                eventId = 1L,
                accessLevel = level,
            )
            val mode = ExternalEventEditRouter.decide(src)
            assertTrue(
                "accessLevel=$level (>=CONTRIBUTOR) should be writable, got $mode",
                mode is EditorMode.WritableExternal,
            )
        }
    }

    @Test fun contributorBoundaryIsInclusive() {
        // The cutoff comment in ExternalEventEditRouter says
        // `accessLevel >= CAL_ACCESS_CONTRIBUTOR`. Pin the inclusive
        // boundary explicitly so a future refactor to `>` breaks this
        // test rather than silently disables editing for every
        // contributor-only collaborator.
        assertEquals(500, ExternalEventEditRouter.CAL_ACCESS_CONTRIBUTOR)
        val onTheBoundary = ExternalSource(
            accountType = "com.google",
            accountName = "alex@example.com",
            eventId = 1L,
            accessLevel = ExternalEventEditRouter.CAL_ACCESS_CONTRIBUTOR,
        )
        assertTrue(
            ExternalEventEditRouter.decide(onTheBoundary) is EditorMode.WritableExternal,
        )
        val oneBelow = onTheBoundary.copy(
            accessLevel = ExternalEventEditRouter.CAL_ACCESS_CONTRIBUTOR - 1,
        )
        assertTrue(
            ExternalEventEditRouter.decide(oneBelow) is EditorMode.ReadOnlyExternal,
        )
    }

    @Test fun nullExternalAlwaysRoutesToFileBackedFlow() {
        // The file-backed (skb-repo) path must never be displaced by a
        // missing-source-tag input.
        val mode = ExternalEventEditRouter.decide(null)
        assertEquals(EditorMode.WritableSkbRepo, mode)
    }
}
