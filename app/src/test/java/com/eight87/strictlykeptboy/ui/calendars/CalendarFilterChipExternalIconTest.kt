package com.eight87.strictlykeptboy.ui.calendars

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.18.C.1 — assert the source-icon resolution for external
 * calendar chips. The composable wires this back to a vector mark inside
 * the chip's `leadingIcon` slot; the pure-function mapping is the
 * load-bearing piece (and the only piece worth a unit test — Material
 * vector identity is a render-layer concern covered by AVD smoke).
 */
class CalendarFilterChipExternalIconTest {

    @Test fun googleAccountTypeMapsToG() {
        assertEquals(ExternalSourceGlyph.G, glyphFor("com.google"))
        // Case-insensitive.
        assertEquals(ExternalSourceGlyph.G, glyphFor("COM.GOOGLE"))
    }

    @Test fun exchangeMicrosoftOutlookAllMapToO() {
        assertEquals(ExternalSourceGlyph.O, glyphFor("com.android.exchange"))
        assertEquals(ExternalSourceGlyph.O, glyphFor("eas.acme.corp"))
        assertEquals(ExternalSourceGlyph.O, glyphFor("com.microsoft.outlook"))
        assertEquals(ExternalSourceGlyph.O, glyphFor("com.microsoft.exchange"))
    }

    @Test fun davdroidMapsToCloud() {
        assertEquals(ExternalSourceGlyph.Cloud, glyphFor("at.bitfire.davdroid"))
    }

    @Test fun unknownAccountTypeMapsToGear() {
        assertEquals(ExternalSourceGlyph.Gear, glyphFor("LOCAL"))
        assertEquals(ExternalSourceGlyph.Gear, glyphFor(""))
        assertEquals(ExternalSourceGlyph.Gear, glyphFor("com.somethingelse"))
    }
}
