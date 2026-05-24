package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Round 2.26.B.1 — sealed entry type for the unified day-of feed.
 *
 * The Tasks destination's "Today" filter merges two heterogeneous streams:
 *  - todolist tasks ([TaskEntry]) — overdue, due-today, pinned-standing,
 *  - timebox bands ([TimeboxEntry]) from the resolver's [RenderedSchedule]
 *    whose [DayBand.kind] is [CalendarKind.Timebox].
 *
 * [sortKey] drives the chronological interleave inside the today-bucket
 * (per D-2.26.c). It is `null` for tasks without a due time AND for
 * overdue/pinned entries that occupy their own buckets.
 */
sealed class UnifiedTodayItem {
    abstract val sortKey: OffsetDateTime?

    data class TaskEntry(
        val task: TaskItem,
        override val sortKey: OffsetDateTime?,
    ) : UnifiedTodayItem()

    data class TimeboxEntry(
        val band: DayBand,
        override val sortKey: OffsetDateTime?,
    ) : UnifiedTodayItem()
}

/**
 * Section bucket — exposed for [TaskUnifiedTodayView] section headers
 * (C.4). [Today] is the chronological interleave; [Overdue] and
 * [PinnedStanding] are the bookends.
 */
enum class UnifiedTodaySection { Overdue, Today, PinnedStanding }

/**
 * Round 2026-05-24 fix-batch W3.2 / R-7 — cap the "Overdue" bucket
 * to the last 14 days so a fresh demo install (whose seeded tasks
 * span weeks) doesn't surface a 21-count overdue list out of the
 * box. Anything older still appears in the per-todolist views; only
 * the Today destination's overdue bookend is bounded.
 */
const val OVERDUE_WINDOW_DAYS: Long = 14L

/**
 * Round 2.26.B.2 — pure-function builder for the unified day-of feed.
 *
 * Inputs:
 *  - [tasks] — full task universe (the builder applies [forToday] itself).
 *  - [schedule] — current [RenderedSchedule]; nullable to let the caller
 *    pass a freshly-collected `StateFlow<RenderedSchedule?>` value
 *    without an `!!`.
 *  - [now] — clock; tests inject a fixed instant.
 *
 * Output order (per D-2.26.c):
 *   1. overdue todolist tasks (due before today, not done), ordered by
 *      due date asc then priority desc;
 *   2. today's items interleaved chronologically — timeboxes by
 *      [DayBand.instance.effectiveStart], tasks (due-today, not done) by
 *      due-date (no per-task time in v1 → all sort to start-of-day),
 *      tasks with no due-time sink to the bottom of this bucket;
 *   3. pinned standing (no due, undone, `pinnedForToday`).
 */
fun buildUnifiedToday(
    tasks: List<TaskItem>,
    schedule: RenderedSchedule?,
    now: ZonedDateTime,
): List<UnifiedTodayItem> {
    val today: LocalDate = now.toLocalDate()
    val offset: ZoneOffset = now.offset

    // --- Bucket 1: overdue (bounded to last 14 days so a fresh
    // demo install doesn't dump every legacy task into Today). Tasks
    // older than the window are intentionally hidden here — they
    // remain visible in the per-todolist views.
    val overdueWindowStart = today.minusDays(OVERDUE_WINDOW_DAYS)
    val overdue = tasks
        .filter {
            !it.done && it.due != null &&
                it.due.isBefore(today) &&
                !it.due.isBefore(overdueWindowStart)
        }
        .sortedWith(
            compareBy<TaskItem> { it.due }
                .thenByDescending { it.priority }
                .thenBy { it.title.lowercase() },
        )
        .map { task ->
            val key = task.due?.atStartOfDay()?.atOffset(offset)
            UnifiedTodayItem.TaskEntry(task, key)
        }

    // --- Bucket 2: today (interleave) ---
    val todayTasks = tasks
        .filter { !it.done && it.due == today }
        .map { task ->
            val key = task.due?.atStartOfDay()?.atOffset(offset)
            UnifiedTodayItem.TaskEntry(task, key)
        }

    val timeboxes: List<UnifiedTodayItem.TimeboxEntry> = schedule
        ?.days?.firstOrNull { it.date == today }
        ?.bands.orEmpty()
        .filter { it.kind == CalendarKind.Timebox }
        .map { band ->
            UnifiedTodayItem.TimeboxEntry(band, band.instance.effectiveStart.toOffsetDateTime())
        }

    val todayBucket: List<UnifiedTodayItem> = (todayTasks + timeboxes)
        .sortedWith(
            // null sort-keys (no-due tasks) sink to the bottom; otherwise
            // ascending by sortKey. Stable secondary on title to keep tests
            // deterministic when two items share a sortKey.
            compareBy<UnifiedTodayItem>(
                { it.sortKey == null },
                { it.sortKey },
                {
                    when (it) {
                        is UnifiedTodayItem.TaskEntry -> "1:" + it.task.title.lowercase()
                        is UnifiedTodayItem.TimeboxEntry -> "0:" + it.band.instance.title.lowercase()
                    }
                },
            ),
        )

    // --- Bucket 3: pinned standing ---
    val pinned = tasks
        .filter { !it.done && it.due == null && it.pinnedForToday }
        .sortedWith(
            compareByDescending<TaskItem> { it.priority }
                .thenBy { it.title.lowercase() },
        )
        .map { UnifiedTodayItem.TaskEntry(it, null) }

    return overdue + todayBucket + pinned
}

/**
 * Round 2.26.C.4 — split the unified list into its three section
 * buckets in the same order [buildUnifiedToday] emitted them. Used by
 * [TaskUnifiedTodayView] to render section headers.
 */
fun List<UnifiedTodayItem>.bySection(today: LocalDate): Map<UnifiedTodaySection, List<UnifiedTodayItem>> {
    val overdue = mutableListOf<UnifiedTodayItem>()
    val todayBucket = mutableListOf<UnifiedTodayItem>()
    val pinned = mutableListOf<UnifiedTodayItem>()
    forEach { item ->
        when (item) {
            is UnifiedTodayItem.TaskEntry -> {
                val t = item.task
                when {
                    t.due != null && t.due.isBefore(today) &&
                        !t.due.isBefore(today.minusDays(OVERDUE_WINDOW_DAYS)) &&
                        !t.done -> overdue.add(item)
                    t.due == today && !t.done -> todayBucket.add(item)
                    t.due == null && t.pinnedForToday -> pinned.add(item)
                    else -> todayBucket.add(item)
                }
            }
            is UnifiedTodayItem.TimeboxEntry -> todayBucket.add(item)
        }
    }
    return buildMap {
        if (overdue.isNotEmpty()) put(UnifiedTodaySection.Overdue, overdue)
        if (todayBucket.isNotEmpty()) put(UnifiedTodaySection.Today, todayBucket)
        if (pinned.isNotEmpty()) put(UnifiedTodaySection.PinnedStanding, pinned)
    }
}
