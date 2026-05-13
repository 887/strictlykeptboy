package com.eight87.strictlykeptboy.auto

import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Phase Q / 2.1.G — [CarAppRuntime] is the rendezvous point between
 * the composition root and [SkbCarAppService]. The contract is
 * "writable from one place, readable from the service". This test
 * pins the "fall back to empty source" + "identity provider null is
 * safe" behaviour the session relies on.
 */
class CarAppRuntimeTest {

    @After fun reset() {
        CarAppRuntime.todayEventSource = null
        CarAppRuntime.identityProvider = null
    }

    @Test fun runtime_handle_round_trips_the_source() {
        val src = TodayEventSource { emptyList() }
        CarAppRuntime.todayEventSource = src
        assertSame(src, CarAppRuntime.todayEventSource)
    }

    @Test fun runtime_handle_starts_null() {
        assertNull(CarAppRuntime.todayEventSource)
        assertNull(CarAppRuntime.identityProvider)
    }

    @Test fun source_interface_returns_list_passthrough() {
        val src = TodayEventSource { emptyList() }
        assertNotNull(src.eventsForToday())
    }

    @Test fun identity_provider_handle_round_trips() {
        val identity = IdentityTomlData.LockedDefaults
        CarAppRuntime.identityProvider = { identity }
        assertEquals(identity, CarAppRuntime.identityProvider!!.invoke())
    }
}
