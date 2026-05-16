package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * Round 2.23 Phase B (D-2.23.c) — pin the per-weekday emoji map so it
 * never drifts. The Sun = 🦇 entry is deliberate per the user's
 * bat-coded identity feedback (2026-05-16).
 */
class WeekdayEmojiTest {
    @Test fun mapsAllSevenDays() {
        assertEquals("🌅", emojiFor(DayOfWeek.MONDAY))     // 🌅
        assertEquals("🌱", emojiFor(DayOfWeek.TUESDAY))    // 🌱
        assertEquals("🌊", emojiFor(DayOfWeek.WEDNESDAY))  // 🌊
        assertEquals("🌳", emojiFor(DayOfWeek.THURSDAY))   // 🌳
        assertEquals("🌟", emojiFor(DayOfWeek.FRIDAY))     // 🌟
        assertEquals("🌸", emojiFor(DayOfWeek.SATURDAY))   // 🌸
        assertEquals("🦇", emojiFor(DayOfWeek.SUNDAY))     // 🦇
    }
}
