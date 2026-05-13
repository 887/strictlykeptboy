package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.2.E.6 — end-to-end composer renders real events when wired
 * through the [BriefingSource] parked handle. Where
 * [BriefingComposerTest] exercises the pure composer in isolation, this
 * test verifies the wiring contract: source emits → composer renders →
 * the placeholder "Nothing scheduled." line is replaced.
 */
class BriefingComposerRealEventsTest {

    private val zone = ZoneId.of("UTC")
    private val anchor = LocalDate.of(2026, 5, 13)

    private fun inst(id: String, hour: Int, date: LocalDate = anchor, title: String = "Event-$id") =
        ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).let { start ->
            MaterializedInstance(
                source = InstanceSource.OneOff(EventRef(id)),
                calendar = CalendarRef("cal-a"),
                repo = RepoRef("repo-a"),
                originalStart = start,
                originalEnd = start.plusHours(1L),
                effectiveStart = start,
                effectiveEnd = start.plusHours(1L),
                title = title,
                body = "",
            )
        }

    @After fun tearDown() {
        BriefingRuntime.source = null
        BriefingRuntime.identityProvider = null
    }

    @Test fun parkedSourceFeedsComposerForMorningSlot() {
        val events = listOf(inst("a", 9, title = "Standup"), inst("b", 14, title = "Lunch"))
        BriefingRuntime.source = BriefingSource { d, _ ->
            if (d == anchor) events else emptyList()
        }
        // Simulate BriefingWorker.doWork() materialisation path.
        val instances = BriefingRuntime.source!!.instancesForDate(anchor, zone)
        val brief = BriefingComposer.compose(
            slot = BriefingComposer.Slot.Morning,
            instances = instances,
            identity = null,
            zone = zone,
            now = { ZonedDateTime.of(anchor, LocalTime.of(6, 0), zone) },
        )
        assertEquals(2, brief.lines.size)
        assertTrue(brief.lines[0].contains("Standup"))
        assertTrue(brief.lines[1].contains("Lunch"))
        // Must NOT be the empty placeholder when real events are wired.
        assertNotEquals("Nothing scheduled.", brief.lines[0])
    }

    @Test fun parkedSourceFeedsComposerForEveningSlot() {
        val tomorrow = anchor.plusDays(1L)
        val events = listOf(inst("c", 7, date = tomorrow, title = "Run"))
        BriefingRuntime.source = BriefingSource { d, _ ->
            if (d == tomorrow) events else emptyList()
        }
        val instances = BriefingRuntime.source!!.instancesForDate(tomorrow, zone)
        val brief = BriefingComposer.compose(
            slot = BriefingComposer.Slot.Evening,
            instances = instances,
            identity = null,
            zone = zone,
            now = { ZonedDateTime.of(anchor, LocalTime.of(21, 0), zone) },
        )
        assertEquals(1, brief.lines.size)
        assertTrue(brief.lines[0].contains("Run"))
        assertEquals("Tomorrow's schedule", brief.title)
    }

    @Test fun identityHandleThreadsThroughSalutation() {
        val identity = IdentityTomlData(
            praiseTerm = "good boy",
            altTerms = emptyList(),
            pronouns = IdentityPronouns("he", "him", "his", "himself"),
            honorificForDom = "Sir",
        )
        BriefingRuntime.source = BriefingSource { _, _ -> emptyList() }
        BriefingRuntime.identityProvider = { identity }
        val instances = BriefingRuntime.source!!.instancesForDate(anchor, zone)
        val resolved = BriefingRuntime.identityProvider!!.invoke()
        val brief = BriefingComposer.compose(
            slot = BriefingComposer.Slot.Morning,
            instances = instances,
            identity = resolved,
            zone = zone,
            now = { ZonedDateTime.of(anchor, LocalTime.of(6, 0), zone) },
        )
        assertEquals("Morning, good boy", brief.salutation)
    }

    @Test fun nullSourceFallsBackToEmptyPlaceholder() {
        // No source parked — worker code path is `source?.instancesForDate(...) ?: emptyList()`.
        BriefingRuntime.source = null
        val instances = BriefingRuntime.source?.instancesForDate(anchor, zone) ?: emptyList()
        val brief = BriefingComposer.compose(
            slot = BriefingComposer.Slot.Morning,
            instances = instances,
            identity = null,
            zone = zone,
            now = { ZonedDateTime.of(anchor, LocalTime.of(6, 0), zone) },
        )
        assertEquals(1, brief.lines.size)
        assertEquals("Nothing scheduled.", brief.lines[0])
    }
}
