package com.eight87.strictlykeptboy.widget.common

import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import java.time.Instant

/**
 * Phase EEE.2 — narrow port the now-widget calls to learn what the user
 * is scheduled to be doing right now.
 *
 * Single-method interface (ISP). The concrete implementation lives in
 * the view-model / data layer where it can call `Renderer.render(...)`
 * and pick the band whose `effectiveStart ≤ now < effectiveEnd`. The
 * widget code itself depends only on this abstraction (DIP).
 */
fun interface ActiveEventSource {
    fun activeAt(now: Instant): ActiveEvent
}

sealed interface ActiveEvent {
    data class Present(
        val instance: MaterializedInstance,
        val remainingMinutes: Long,
        val subbeatIndex: Int? = null,
        val subbeatStickerId: String? = null,
        val subbeatLabel: String? = null,
        val upcoming: List<UpcomingItem> = emptyList(),
    ) : ActiveEvent

    data object Absent : ActiveEvent
}

data class UpcomingItem(
    val title: String,
    val startEpochMillis: Long,
    val isPrivate: Boolean,
)
