package com.eight87.strictlykeptboy.widget.common

/**
 * Phase VV.8 / EEE.11 — pure widget time-formatting helpers.
 */
object WidgetTimeFormat {

    fun ddhhmm(c: Countdown): String = "%02d:%02d:%02d".format(c.days, c.hours, c.minutes)

    enum class Bucket { Today, Tomorrow, InDays, DaysAgo }

    fun bucket(c: Countdown): Bucket = when {
        c.isPastDue -> Bucket.DaysAgo
        c.days == 0L -> Bucket.Today
        c.days == 1L -> Bucket.Tomorrow
        else -> Bucket.InDays
    }

    fun exactRemaining(remainingMinutes: Long): String {
        if (remainingMinutes <= 0L) return "0m"
        val h = remainingMinutes / 60L
        val m = remainingMinutes % 60L
        return if (h <= 0L) "${m}m" else "${h}h ${m}m"
    }

    fun coarseRemaining(remainingMinutes: Long): String {
        if (remainingMinutes <= 0L) return "ending"
        return when {
            remainingMinutes < 5L -> "<5m"
            remainingMinutes < 15L -> "<15m"
            remainingMinutes < 30L -> "<30m"
            remainingMinutes < 60L -> "<1h"
            remainingMinutes < 180L -> "<3h"
            else -> "${remainingMinutes / 60L}h+"
        }
    }
}
