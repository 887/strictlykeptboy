package com.eight87.strictlykeptboy.ui.theming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DynamicColorOverrideTest {

    @Test fun `seed-derived scheme has distinct primary from default dark`() {
        val seeded = schemeFromSeedPure(0xFF4DD0E1.toInt(), darkTheme = true)
        val defaultDark = androidx.compose.material3.darkColorScheme()
        assertNotEquals(defaultDark.primary, seeded.primary)
    }

    @Test fun `same seed produces same scheme`() {
        val a = schemeFromSeedPure(0xFFE57373.toInt(), darkTheme = false)
        val b = schemeFromSeedPure(0xFFE57373.toInt(), darkTheme = false)
        assertEquals(a.primary, b.primary)
    }

    @Test fun `dark vs light differ on background`() {
        val light = schemeFromSeedPure(0xFFBA68C8.toInt(), darkTheme = false)
        val dark = schemeFromSeedPure(0xFFBA68C8.toInt(), darkTheme = true)
        assertNotEquals(light.background, dark.background)
    }

    @Test fun `event title preview splits leading emoji`() {
        val (e, rest) = splitLeadingEmoji("🦇 stand-up")
        assertEquals("🦇", e)
        assertEquals("stand-up", rest)
    }

    @Test fun `event title preview returns null for plain title`() {
        val (e, rest) = splitLeadingEmoji("budget review")
        assertEquals(null, e)
        assertEquals("budget review", rest)
    }

    @Test fun `event title preview handles empty`() {
        val (e, rest) = splitLeadingEmoji("")
        assertEquals(null, e)
        assertEquals("", rest)
    }
}
