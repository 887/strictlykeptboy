package com.eight87.strictlykeptboy.ui.trip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 2.2.D.13 — thin abstraction over "what trips exist".
 *
 * Phase CCC ships the trip resolver feed proper — when it lands, swap
 * the [InMemoryTripFeed] for a real impl that aggregates from
 * `CalendarRegistry` filtering on `calendar.role == "trip"`.
 *
 * Pure-data summary; the Lifestyle Settings card reads
 * [upcoming] + [last] only.
 */
data class TripSummary(
    val tripId: String,
    val displayName: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
)

interface TripFeed {
    val flow: StateFlow<List<TripSummary>>
    fun upcoming(nowEpochMs: Long): TripSummary?
    fun last(nowEpochMs: Long): TripSummary?
}

/**
 * Default impl — empty list. The Lifestyle card renders the "no trips
 * yet" placeholder until a real feed is wired into [AppGraph].
 */
class InMemoryTripFeed(initial: List<TripSummary> = emptyList()) : TripFeed {
    private val _flow = MutableStateFlow(initial)
    override val flow: StateFlow<List<TripSummary>> = _flow.asStateFlow()

    override fun upcoming(nowEpochMs: Long): TripSummary? =
        _flow.value
            .filter { it.startEpochMs >= nowEpochMs }
            .minByOrNull { it.startEpochMs }

    override fun last(nowEpochMs: Long): TripSummary? =
        _flow.value
            .filter { it.endEpochMs < nowEpochMs }
            .maxByOrNull { it.endEpochMs }

    /** Test hook. */
    fun setTrips(trips: List<TripSummary>) { _flow.value = trips }
}
