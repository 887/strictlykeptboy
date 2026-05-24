package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.resolver.Factories.cal
import com.eight87.strictlykeptboy.resolver.Factories.event
import com.eight87.strictlykeptboy.resolver.Factories.snapshot
import com.eight87.strictlykeptboy.resolver.Factories.zdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OverlayResolverTest {

    private val mat = RecurrenceMaterializer()
    private val overlay = OverlayResolver()

    @Test fun threeWayCollision_assignsDistinctLanes() {
        val snap = snapshot(cal("c1"))
        val instances = listOf(
            mat.fromOneOff(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T12:00:00")),
            mat.fromOneOff(event("b", "c1", "2026-05-11T10:30:00", "2026-05-11T11:30:00")),
            mat.fromOneOff(event("c", "c1", "2026-05-11T11:00:00", "2026-05-11T13:00:00")),
        )
        val v = overlay.layer(
            setOf(CalendarRef("c1")), instances, snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            now = zdt("2026-05-11T00:00:00"),
        )
        val bands = v.bandsByDay.getValue(LocalDate.parse("2026-05-11"))
        assertEquals(3, bands.size)
        // All three overlap so each must occupy a unique lane.
        assertEquals(setOf(0, 1, 2), bands.map { it.laneIndex }.toSet())
        bands.forEach { assertEquals(3, it.totalLanes) }
    }

    @Test fun nonOverlapping_sharesLane() {
        val snap = snapshot(cal("c1"))
        val instances = listOf(
            mat.fromOneOff(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T11:00:00")),
            mat.fromOneOff(event("b", "c1", "2026-05-11T11:00:00", "2026-05-11T12:00:00")),
        )
        val v = overlay.layer(
            setOf(CalendarRef("c1")), instances, snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            now = zdt("2026-05-11T00:00:00"),
        )
        val bands = v.bandsByDay.getValue(LocalDate.parse("2026-05-11"))
        assertEquals(2, bands.size)
        // Back-to-back: lane 0 frees up exactly when b starts (algorithm allows ==).
        bands.forEach { assertEquals(0, it.laneIndex) }
        bands.forEach { assertEquals(1, it.totalLanes) }
    }

    @Test fun completionState_inProgress() {
        val snap = snapshot(cal("c1"))
        val e = mat.fromOneOff(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T12:00:00"))
        val v = overlay.layer(
            setOf(CalendarRef("c1")), listOf(e), snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            now = zdt("2026-05-11T11:00:00"),
        )
        assertEquals(CompletionState.InProgress, v.bandsByDay.getValue(LocalDate.parse("2026-05-11")).single().completionState)
    }

    @Test fun completionState_completedBySchedule_inversionDefault() {
        val snap = snapshot(cal("c1"))
        val e = mat.fromOneOff(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T11:00:00"))
        val v = overlay.layer(
            setOf(CalendarRef("c1")), listOf(e), snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            now = zdt("2026-05-11T15:00:00"),
        )
        assertEquals(
            CompletionState.CompletedBySchedule,
            v.bandsByDay.getValue(LocalDate.parse("2026-05-11")).single().completionState,
        )
    }

    @Test fun completionState_skippedDeviationTagsBand() {
        val snap = snapshot(cal("c1"))
        val e = mat.fromOneOff(event("a", "c1", "2026-05-11T10:00:00", "2026-05-11T11:00:00"))
        val dev = DeviationInput(
            targetId = "a",
            instanceDate = LocalDate.parse("2026-05-11"),
            kind = DeviationKind.Skipped,
            at = zdt("2026-05-11T10:30:00"),
        )
        val v = overlay.layer(
            setOf(CalendarRef("c1")), listOf(e), snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            deviations = listOf(dev),
            now = zdt("2026-05-11T15:00:00"),
        )
        assertEquals(
            CompletionState.Skipped,
            v.bandsByDay.getValue(LocalDate.parse("2026-05-11")).single().completionState,
        )
    }

    @Test fun supersedence_skipsOneOffEvents() {
        // Decision 2026-05-24: one-off events the user deliberately
        // created stay visible during a special-base window. Only
        // recurrence-derived bands inherit the suppression. So a
        // one-off "work" event during a vacation day renders normally.
        val snap = snapshot(
            cal("work"),
            cal("vacation", priority = 999, supersedes = listOf("work")),
        )
        val workEvent = mat.fromOneOff(event("a", "work", "2026-05-11T10:00:00", "2026-05-11T11:00:00"))
        val vacationEvent = mat.fromOneOff(event("v", "vacation", "2026-05-11T00:00:00", "2026-05-12T00:00:00"))
        val v = overlay.layer(
            setOf(CalendarRef("work"), CalendarRef("vacation")), listOf(workEvent, vacationEvent), snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            now = zdt("2026-05-11T00:00:00"),
        )
        val workBand = v.bandsByDay.getValue(LocalDate.parse("2026-05-11"))
            .single { it.instance.calendar == CalendarRef("work") }
        assertNull(workBand.supersededByCalendar)
    }

    @Test fun supersedence_overrideClearsTag() {
        val snap = snapshot(
            cal("work"),
            cal("vacation", priority = 999, supersedes = listOf("work")),
        )
        val workEvent = mat.fromOneOff(event("a", "work", "2026-05-11T10:00:00", "2026-05-11T11:00:00"))
        val vacationEvent = mat.fromOneOff(event("v", "vacation", "2026-05-11T00:00:00", "2026-05-12T00:00:00"))
        val ov = OverrideInput(
            supersededCalendar = CalendarRef("work"),
            eventId = "a",
            instanceDate = LocalDate.parse("2026-05-11"),
            kind = OverrideKind.ForceShow,
        )
        val v = overlay.layer(
            setOf(CalendarRef("work"), CalendarRef("vacation")), listOf(workEvent, vacationEvent), snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            overrides = listOf(ov),
            now = zdt("2026-05-11T00:00:00"),
        )
        val workBand = v.bandsByDay.getValue(LocalDate.parse("2026-05-11"))
            .single { it.instance.calendar == CalendarRef("work") }
        assertNull(workBand.supersededByCalendar)
    }

    @Test fun priorityOverride_winsOverCalendarPriority() {
        val snap = snapshot(cal("low", priority = 100), cal("high", priority = 800))
        val instances = listOf(
            mat.fromOneOff(event("a", "low", "2026-05-11T10:00:00", "2026-05-11T12:00:00", priorityOverride = 999)),
            mat.fromOneOff(event("b", "high", "2026-05-11T10:00:00", "2026-05-11T12:00:00")),
        )
        val v = overlay.layer(
            setOf(CalendarRef("low"), CalendarRef("high")), instances, snap,
            zdt("2026-05-11T00:00:00"), zdt("2026-05-12T00:00:00"),
            now = zdt("2026-05-11T00:00:00"),
        )
        val bands = v.bandsByDay.getValue(LocalDate.parse("2026-05-11"))
        val byInstance = bands.associateBy { it.instance.instanceId }
        // Higher-priority gets lane 0
        assertEquals(0, byInstance.getValue("a").laneIndex)
        assertEquals(999, byInstance.getValue("a").priority)
        assertEquals(800, byInstance.getValue("b").priority)
    }
}
