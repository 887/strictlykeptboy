package com.eight87.strictlykeptboy.ui.schedule

import java.time.DayOfWeek

/**
 * Round 2.23 Phase B (D-2.23.c) — stable per-weekday emoji map shown in
 * Day / 3-day / Week day-headers. Sun = 🦇 lands the bat-coded identity
 * the user requested in feedback (2026-05-16).
 *
 * Single source of truth so the three views can never drift. Pure
 * function for trivially-testable mapping.
 */
fun emojiFor(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "🌅"     // 🌅
    DayOfWeek.TUESDAY -> "🌱"    // 🌱
    DayOfWeek.WEDNESDAY -> "🌊"  // 🌊
    DayOfWeek.THURSDAY -> "🌳"   // 🌳
    DayOfWeek.FRIDAY -> "🌟"     // 🌟
    DayOfWeek.SATURDAY -> "🌸"   // 🌸
    DayOfWeek.SUNDAY -> "🦇"     // 🦇
}
