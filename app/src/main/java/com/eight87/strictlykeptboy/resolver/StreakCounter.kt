package com.eight87.strictlykeptboy.resolver

import java.time.LocalDate

/**
 * Phase XX.10 / AT-J — count-only streak counter (no escalation,
 * no flames, no trophies, no nag copy).
 *
 * `streak = consecutive_days_with_no_skip_deviation`, walked
 * backward from `today` until a `skipped` deviation appears,
 * counting only days where the entity was scheduled to occur.
 *
 * Per AT-J.1 LOCKED: `partial` / `completed-early` / `completed-late`
 * do NOT break the streak. Only `skipped` does.
 *
 * Pure function (SOLID-S, SOLID-D): no IO, no `android.*`. Callers
 * supply the set of scheduled dates and the set of skipped dates
 * (cheap to derive from the resolver's materialized instances + the
 * deviation index). The Room layer caches the result in the
 * `streak_count` column and invalidates per AT-J.2 triggers.
 */
object StreakCounter {

    /** Per-day completion status as seen by the streak machine. */
    enum class DayStatus { ScheduledNoSkip, Skipped, NotScheduled }

    /**
     * @param today the "anchor" day we count back from.
     * @param scheduledDates set of dates the entity was scheduled to occur (must include today if it was).
     * @param skippedDates subset of [scheduledDates] on which the user wrote a `skipped` deviation.
     * @return integer streak count. 0 when today is scheduled-and-skipped or when no scheduled day yet.
     */
    fun compute(
        today: LocalDate,
        scheduledDates: Set<LocalDate>,
        skippedDates: Set<LocalDate>,
    ): Int {
        var count = 0
        var cursor = today
        while (true) {
            val status = statusOn(cursor, scheduledDates, skippedDates)
            when (status) {
                DayStatus.Skipped -> return count
                DayStatus.ScheduledNoSkip -> {
                    count += 1
                    cursor = cursor.minusDays(1)
                }
                DayStatus.NotScheduled -> {
                    // Days the entity wasn't scheduled don't count for or
                    // against the streak; we just walk past them. Cap to
                    // a year of look-back so a brand-new schedule with no
                    // history can't loop forever.
                    cursor = cursor.minusDays(1)
                }
            }
            if (today.toEpochDay() - cursor.toEpochDay() > MAX_LOOKBACK_DAYS) return count
        }
    }

    /**
     * Day-status helper. Public to let tests assert the streak's
     * three-way classification without re-deriving it.
     */
    fun statusOn(
        date: LocalDate,
        scheduledDates: Set<LocalDate>,
        skippedDates: Set<LocalDate>,
    ): DayStatus = when {
        date in skippedDates -> DayStatus.Skipped
        date in scheduledDates -> DayStatus.ScheduledNoSkip
        else -> DayStatus.NotScheduled
    }

    /** Guard against a missing-skip / infinite-walk edge case. */
    const val MAX_LOOKBACK_DAYS: Long = 366
}
