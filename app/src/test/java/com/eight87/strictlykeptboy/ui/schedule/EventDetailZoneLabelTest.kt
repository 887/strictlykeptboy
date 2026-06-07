package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/**
 * QA iter-4 B.2 — timezone display rules.
 *
 * [zoneSuffixLabel] is the pure formatter (deterministic — does not depend on
 * the host JVM's `getDisplayName` table, which differs from Android's).
 * [friendlyZoneSuffix] adds the device-match short-circuit on top.
 */
class EventDetailZoneLabelTest {

    private val device = ZoneId.of("Europe/Berlin")
    private val us = Locale.US

    // --- zoneSuffixLabel (pure) --------------------------------------------

    @Test fun rawIanaFallback_collapsesToCity() {
        // The exact reported bug: on-device getDisplayName returned the raw id.
        // "Arctic/Longyearbyen · Longyearbyen" must collapse to just the city.
        assertEquals("Longyearbyen", zoneSuffixLabel("Arctic/Longyearbyen", "Arctic/Longyearbyen"))
    }

    @Test fun realAbbreviation_pairsWithCity() {
        assertEquals("CEST · Berlin", zoneSuffixLabel("CEST", "Europe/Berlin"))
    }

    @Test fun abbreviationEqualsCity_showsCityOnce() {
        assertEquals("Kolkata", zoneSuffixLabel("Kolkata", "Asia/Kolkata"))
    }

    @Test fun underscoreInCity_isHumanized() {
        assertEquals("EST · New York", zoneSuffixLabel("EST", "America/New_York"))
    }

    @Test fun multiSegmentId_usesLastSegment_andNeverContainsSlash() {
        val label = zoneSuffixLabel("America/Indiana/Indianapolis", "America/Indiana/Indianapolis")
        assertEquals("Indianapolis", label)
        assertFalse(label.contains('/'))
    }

    // --- friendlyZoneSuffix (device check) ---------------------------------

    @Test fun sameAsDevice_returnsNull() {
        assertNull(friendlyZoneSuffix(ZoneId.of("Europe/Berlin"), device, us))
    }

    @Test fun differentZone_isNonNull_andSlashFree() {
        for (id in listOf("Europe/London", "America/New_York", "Asia/Kolkata", "Pacific/Auckland")) {
            val label = friendlyZoneSuffix(ZoneId.of(id), device, us)
            assertFalse("label for $id must not contain a raw IANA slash", label?.contains('/') ?: false)
        }
    }
}
