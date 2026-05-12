package com.eight87.strictlykeptboy.notif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRoleTest {

    @Test fun zeroBecomesAtStart() {
        assertEquals(ReminderRole.AtStart, resolveRole("0"))
        assertEquals(ReminderRole.AtStart, resolveRole("now"))
    }

    @Test fun shortLeadIsPreEvent() {
        val r = resolveRole("15m")
        assertTrue(r is ReminderRole.PreEvent)
    }

    @Test fun dayPlusLeadIsHeadsUp() {
        val r = resolveRole("1d")
        assertTrue(r is ReminderRole.HeadsUp)
        val w = resolveRole("1w")
        assertTrue(w is ReminderRole.HeadsUp)
    }

    @Test fun unparseableReturnsNull() {
        assertNull(resolveRole("5x"))
    }
}
