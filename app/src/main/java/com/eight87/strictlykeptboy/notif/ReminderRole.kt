package com.eight87.strictlykeptboy.notif

import kotlin.time.Duration

/**
 * Phase M / NS-Z — semantic role of a reminder relative to its event.
 *
 * Each role has a lead-time (negative = before, zero = at-start, positive
 * = after). Used to drive notification copy and the post-event-checkin
 * action set per D.82.
 *
 * Sealed hierarchy per R.X.2 — branching on `when` is exhaustive at the
 * one consumer (the receiver's content builder).
 */
sealed interface ReminderRole {
    val offset: Duration

    /** Generic heads-up reminder ("dentist in 1 day"). */
    data class HeadsUp(override val offset: Duration) : ReminderRole

    /** Pre-event reminder (e.g. -15m / -2h per category). */
    data class PreEvent(override val offset: Duration) : ReminderRole

    /** Tomorrow-briefing slot (D.81). */
    data class TomorrowBriefing(override val offset: Duration) : ReminderRole

    /** Fires at event start (`0`). */
    data object AtStart : ReminderRole {
        override val offset: Duration = Duration.ZERO
    }

    /** Post-event check-in (D.82). Opt-in only. */
    data class PostEventCheckin(override val offset: Duration) : ReminderRole

    /** Snoozed re-fire of an existing reminder. */
    data class Snoozed(val originalRole: ReminderRole, override val offset: Duration) : ReminderRole
}

/**
 * Resolve a lead-time string to a [ReminderRole]. Negative lead times
 * (the typical case) map to [ReminderRole.PreEvent] unless explicitly a
 * day boundary (-1d / -1w) which map to [ReminderRole.HeadsUp]. `0` /
 * `now` becomes [ReminderRole.AtStart]. Returns null if the lead-time
 * is unparseable.
 */
fun resolveRole(leadTimeText: String): ReminderRole? {
    val d = LeadTime.parse(leadTimeText) ?: return null
    return when {
        d == Duration.ZERO -> ReminderRole.AtStart
        d.inWholeDays >= 1 -> ReminderRole.HeadsUp(d)
        else -> ReminderRole.PreEvent(d)
    }
}
