package com.eight87.strictlykeptboy.notif

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Phase M / NS-B.3 — lead-time grammar parser.
 *
 *  - `<n>m` minutes, `<n>h` hours, `<n>d` days, `<n>w` weeks.
 *  - Literal `now` accepted as zero.
 *  - One unit per entry. Compound (`1h30m`) is rejected.
 *
 * Returns null on malformed input.
 */
object LeadTime {
    private val regex = Regex("^(\\d+)([mhdw])$")

    fun parse(text: String): Duration? {
        val trimmed = text.trim()
        if (trimmed.equals("now", ignoreCase = true) || trimmed == "0") return ZERO
        val m = regex.matchEntire(trimmed) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        return when (m.groupValues[2]) {
            "m" -> n.minutes
            "h" -> n.hours
            "d" -> n.days
            "w" -> (n * 7).days
            else -> null
        }
    }

    fun format(d: Duration): String = when {
        d == ZERO -> "0m"
        d.inWholeDays > 0 && d.inWholeMinutes % (24 * 60) == 0L -> "${d.inWholeDays}d"
        d.inWholeHours > 0 && d.inWholeMinutes % 60 == 0L -> "${d.inWholeHours}h"
        else -> "${d.inWholeMinutes}m"
    }
}
