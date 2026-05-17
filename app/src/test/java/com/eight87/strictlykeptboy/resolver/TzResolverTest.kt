package com.eight87.strictlykeptboy.resolver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * Round 2.24 / Phase B.4 — `TzResolver` pure-function tests.
 *
 * Covers the precedence chain locked in D-2.24.a + D-2.24.b
 * (event > repo > system) plus the malformed-zone fallback contract
 * required by [TzResolver.parseOrFallback] (must never throw).
 */
class TzResolverTest {

    private val nyc: ZoneId = ZoneId.of("America/New_York")
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    @Test fun sourceZone_eventTzWins() {
        assertEquals(
            nyc,
            TzResolver.sourceZone(
                eventTzId = "America/New_York",
                repoDefaultTzId = "Europe/Berlin",
                systemDefault = tokyo,
            ),
        )
    }

    @Test fun sourceZone_repoDefaultWhenEventUnpinned() {
        assertEquals(
            berlin,
            TzResolver.sourceZone(
                eventTzId = null,
                repoDefaultTzId = "Europe/Berlin",
                systemDefault = tokyo,
            ),
        )
    }

    @Test fun sourceZone_systemWhenBothNull() {
        assertEquals(
            tokyo,
            TzResolver.sourceZone(
                eventTzId = null,
                repoDefaultTzId = null,
                systemDefault = tokyo,
            ),
        )
    }

    @Test fun sourceZone_blankEventTzFallsThrough() {
        assertEquals(
            berlin,
            TzResolver.sourceZone(
                eventTzId = "  ",
                repoDefaultTzId = "Europe/Berlin",
                systemDefault = tokyo,
            ),
        )
    }

    @Test fun sourceZone_malformedEventTzFallsThroughToRepo() {
        assertEquals(
            berlin,
            TzResolver.sourceZone(
                eventTzId = "Not/A_Zone",
                repoDefaultTzId = "Europe/Berlin",
                systemDefault = tokyo,
            ),
        )
    }

    @Test fun sourceZone_malformedRepoFallsThroughToSystem() {
        assertEquals(
            tokyo,
            TzResolver.sourceZone(
                eventTzId = null,
                repoDefaultTzId = "Mars/Olympus_Mons",
                systemDefault = tokyo,
            ),
        )
    }

    @Test fun parseOrFallback_validZone() {
        assertEquals(nyc, TzResolver.parseOrFallback("America/New_York", berlin))
    }

    @Test fun parseOrFallback_malformedReturnsFallback() {
        assertEquals(berlin, TzResolver.parseOrFallback("Not/A_Zone", berlin))
    }

    @Test fun parseOrFallback_nullReturnsFallback() {
        assertEquals(berlin, TzResolver.parseOrFallback(null, berlin))
    }

    @Test fun parseOrFallback_blankReturnsFallback() {
        assertEquals(berlin, TzResolver.parseOrFallback("   ", berlin))
    }
}
