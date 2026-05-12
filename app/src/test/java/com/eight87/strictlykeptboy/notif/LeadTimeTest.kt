package com.eight87.strictlykeptboy.notif

import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeadTimeTest {

    @Test fun parsesMinutes() {
        assertEquals(15.minutes, LeadTime.parse("15m"))
        assertEquals(90.minutes, LeadTime.parse("90m"))
    }

    @Test fun parsesHoursDaysWeeks() {
        assertEquals(2.hours, LeadTime.parse("2h"))
        assertEquals(1.days, LeadTime.parse("1d"))
        assertEquals(14.days, LeadTime.parse("2w"))
    }

    @Test fun parsesZeroAndNow() {
        assertEquals(ZERO, LeadTime.parse("now"))
        assertEquals(ZERO, LeadTime.parse("0"))
    }

    @Test fun rejectsMalformed() {
        assertNull(LeadTime.parse("5x"))
        assertNull(LeadTime.parse("1h30m"))
        assertNull(LeadTime.parse("-15m"))
        assertNull(LeadTime.parse(""))
    }

    @Test fun roundTripsFormat() {
        assertEquals("15m", LeadTime.format(15.minutes))
        assertEquals("2h", LeadTime.format(2.hours))
        assertEquals("1d", LeadTime.format(1.days))
        assertEquals("0m", LeadTime.format(ZERO))
        assertEquals("90m", LeadTime.format(90.minutes))
    }
}
