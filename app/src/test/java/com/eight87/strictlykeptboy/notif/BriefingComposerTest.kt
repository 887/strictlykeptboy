package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class BriefingComposerTest {

    private val zone = ZoneId.of("UTC")
    private val anchorDay = LocalDate.of(2026, 5, 13)
    private fun fixedNow() = ZonedDateTime.of(anchorDay, java.time.LocalTime.of(6, 0), zone)

    private fun instance(
        id: String,
        date: LocalDate,
        hour: Int,
        title: String = "Event-$id",
        priv: Boolean = false,
    ): MaterializedInstance {
        val start = ZonedDateTime.of(date, java.time.LocalTime.of(hour, 0), zone)
        val end = start.plusHours(1L)
        return MaterializedInstance(
            source = InstanceSource.OneOff(EventRef(id)),
            calendar = CalendarRef("cal-a"),
            repo = RepoRef("r1"),
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = title,
            body = "",
            isPrivate = priv,
        )
    }

    @Test fun morningBriefingShowsTodayInstancesSorted() {
        val instances = listOf(
            instance("a", anchorDay, 14),
            instance("b", anchorDay, 9),
            instance("c", anchorDay.minusDays(1), 8), // yesterday — dropped
            instance("d", anchorDay.plusDays(1), 8),  // tomorrow — dropped
        )
        val brief = BriefingComposer.compose(
            BriefingComposer.Slot.Morning, instances, zone = zone, now = ::fixedNow,
        )
        assertEquals(2, brief.lines.size)
        assertTrue(brief.lines[0].startsWith("09:00"))
        assertTrue(brief.lines[1].startsWith("14:00"))
        assertFalse(brief.containsPrivate)
    }

    @Test fun eveningBriefingShowsTomorrow() {
        val instances = listOf(
            instance("a", anchorDay, 9),                  // today — dropped
            instance("b", anchorDay.plusDays(1), 7),
            instance("c", anchorDay.plusDays(1), 19),
        )
        val brief = BriefingComposer.compose(
            BriefingComposer.Slot.Evening, instances, zone = zone, now = ::fixedNow,
        )
        assertEquals(2, brief.lines.size)
        assertEquals("Tomorrow's schedule", brief.title)
    }

    @Test fun emptyDayProducesPlaceholderLine() {
        val brief = BriefingComposer.compose(
            BriefingComposer.Slot.Morning, emptyList(), zone = zone, now = ::fixedNow,
        )
        assertEquals(1, brief.lines.size)
        assertEquals("Nothing scheduled.", brief.lines[0])
    }

    @Test fun privateInstanceCollapsesTitleInLine() {
        val instances = listOf(
            instance("a", anchorDay, 9, title = "Therapist", priv = true),
        )
        val brief = BriefingComposer.compose(
            BriefingComposer.Slot.Morning, instances, zone = zone, now = ::fixedNow,
        )
        assertTrue(brief.containsPrivate)
        assertTrue(brief.lines[0].contains("private event"))
        assertFalse(brief.lines[0].contains("Therapist"))
    }

    @Test fun identityDrivesSalutation() {
        val identity = IdentityTomlData(
            praiseTerm = "good boy",
            altTerms = emptyList(),
            pronouns = IdentityPronouns("he", "him", "his", "himself"),
            honorificForDom = "Sir",
        )
        val brief = BriefingComposer.compose(
            BriefingComposer.Slot.Morning, emptyList(), identity = identity,
            zone = zone, now = ::fixedNow,
        )
        assertEquals("Morning, good boy", brief.salutation)
    }
}
