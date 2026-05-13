package com.eight87.strictlykeptboy.widget.common

import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import java.time.Instant

/**
 * Phase VV.1 — narrow port the countdown widget calls to fetch the
 * event it is pinned to.
 */
fun interface PinnedEventSource {
    fun pinnedFor(widgetId: Int, now: Instant): PinnedEvent
}

sealed interface PinnedEvent {
    data class Present(
        val instance: MaterializedInstance,
        val countdown: Countdown,
    ) : PinnedEvent

    data object Missing : PinnedEvent
}

data class Countdown(val totalMinutes: Long) {
    val isPastDue: Boolean get() = totalMinutes <= 0L
    val absMinutes: Long get() = if (totalMinutes < 0L) -totalMinutes else totalMinutes
    val days: Long get() = absMinutes / 1440L
    val hours: Long get() = (absMinutes % 1440L) / 60L
    val minutes: Long get() = absMinutes % 60L
}
