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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.2.E.6 — parked-handle round-trip for [BriefingRuntime].
 *
 * Verifies the [com.eight87.strictlykeptboy.auto.CarAppRuntime] analogue:
 * the composition root parks a narrow [BriefingSource] + identity lambda;
 * the worker reads them back; populating both fields is idempotent.
 */
class BriefingSourceHandleTest {

    @After fun tearDown() {
        // Clean up the process-wide handle so the test doesn't leak into
        // sibling tests in the same JVM run.
        BriefingRuntime.source = null
        BriefingRuntime.identityProvider = null
    }

    @Test fun parkAndReadSourceRoundTrip() {
        val date = LocalDate.of(2026, 5, 13)
        val zone = ZoneId.of("UTC")
        val start = ZonedDateTime.of(date, LocalTime.of(9, 0), zone)
        val inst = MaterializedInstance(
            source = InstanceSource.OneOff(EventRef("ev-a")),
            calendar = CalendarRef("cal-a"),
            repo = RepoRef("repo-a"),
            originalStart = start,
            originalEnd = start.plusHours(1L),
            effectiveStart = start,
            effectiveEnd = start.plusHours(1L),
            title = "Standup",
            body = "",
        )
        BriefingRuntime.source = BriefingSource { d, _ ->
            if (d == date) listOf(inst) else emptyList()
        }
        val out = BriefingRuntime.source!!.instancesForDate(date, zone)
        assertEquals(1, out.size)
        assertEquals("Standup", out[0].title)

        // Different date returns empty per the SAM body — exercising the
        // contract that the source filters internally.
        val empty = BriefingRuntime.source!!.instancesForDate(date.plusDays(1L), zone)
        assertEquals(0, empty.size)
    }

    @Test fun identityProviderResolvesLazily() {
        var calls = 0
        val data = IdentityTomlData(
            praiseTerm = "good boy",
            altTerms = emptyList(),
            pronouns = IdentityPronouns("he", "him", "his", "himself"),
            honorificForDom = "Sir",
        )
        BriefingRuntime.identityProvider = {
            calls++
            data
        }
        assertEquals(0, calls)
        val first = BriefingRuntime.identityProvider!!.invoke()
        val second = BriefingRuntime.identityProvider!!.invoke()
        assertEquals(2, calls)
        assertNotNull(first)
        assertEquals("good boy", second!!.praiseTerm)
    }

    @Test fun nullSourceReturnsNullHandle() {
        BriefingRuntime.source = null
        BriefingRuntime.identityProvider = null
        assertNull(BriefingRuntime.source)
        assertNull(BriefingRuntime.identityProvider)
    }
}
