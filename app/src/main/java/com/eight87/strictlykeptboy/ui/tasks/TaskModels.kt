package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.HourRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.1.D.6 — priority bump applied to a task whose todolist's
 * `activeHours` covers the current instant. Genesis-prompt rationale:
 * "work todolist items become higher priority during work hours". The
 * bump applies in `sortedForCombined` only, not to the stored priority.
 */
const val ACTIVE_HOURS_PRIORITY_BUMP = 50

/**
 * Phase H — UI-only task models.
 *
 * These are denormalized for the views: each [TaskItem] already carries
 * the source-list display info (color, name, mode), so we don't need to
 * join across DAOs at render time. Adapter that maps Room rows + typed
 * entities → [TaskItem] lives outside Phase H (Phase G integration).
 *
 * Why a UI-only model: keeps Phase H's view code testable without
 * spinning up Room / RepoSnapshot / Renderer.
 */
enum class TaskSource { Today, FromEvents, Pinned, Other }

enum class TodolistMode { Standard, Shopping }

@Immutable
data class TodolistInfo(
    val id: String,
    val repoId: String,
    val name: String,
    val emoji: String? = null,
    val colorSeed: String = "",
    val mode: TodolistMode = TodolistMode.Standard,
    val priority: Int = 0,
    /**
     * Phase 2.1.D.1 — `false` excludes this todolist's tasks from the
     * Combined view unless the show-inactive toggle is set. Defaults to
     * `true` so existing callers / fixtures stay green.
     */
    val active: Boolean = true,
    /** Phase 2.1.D.6 — active-windows mirror of `TodolistMeta.activeWindows`. */
    val activeWindows: List<DateRange> = emptyList(),
    /** Phase 2.1.D.6 — active-hours mirror of `TodolistMeta.activeHours`. */
    val activeHours: List<HourRange> = emptyList(),
    /** Phase 2.1.D.6 — tz used to evaluate `activeHours`. */
    val tzId: ZoneId = ZoneId.systemDefault(),
)

/**
 * Round 2.16.B — execution-time sub-step on a [TaskItem].
 *
 * Per D-2.16.g: inspection of [TaskItem] showed no existing
 * checklist-items field carrying ordered sub-steps; only a flat task
 * shape with no per-step structure. Per the fallback path in the
 * locked decision, we introduce a lightweight optional list on the
 * per-instance type ([TaskItem]) — NOT on a template / TOML schema.
 * Phase B is in-memory only; persistence is deferred.
 *
 * When [TaskItem.subSteps] is empty, the projector treats the task as
 * a single step (`subStepIndex = 1`, `subStepCount = 1`,
 * `subStepDurationMs` = whole-task estimated duration).
 */
@Immutable
data class TaskSubStep(
    val name: String,
    val durationMs: Long,
)

@Immutable
data class TaskItem(
    val id: String,
    val title: String,
    val todolist: TodolistInfo,
    val due: LocalDate? = null,
    val done: Boolean = false,
    val doneAt: LocalDate? = null,
    val priority: Int = 0,
    val tags: List<String> = emptyList(),
    val standing: Boolean = false,
    val pinnedForToday: Boolean = false,
    val body: String = "",
    val author: String = "",
    val attachments: List<TaskAttachment> = emptyList(),
    /** Source classification (for Today's three-section grouping). */
    val source: TaskSource = TaskSource.Other,
    /**
     * Phase 2.1.D.7 — linked timebox event id (when the user picked
     * "Schedule as timebox" on this task). Empty = unlinked.
     */
    val linkedEventId: String = "",
    /** Display-only — the linked timebox's start for the inline chip. */
    val linkedEventStart: ZonedDateTime? = null,
    /**
     * Round 2.16.B — optional ordered execution-time sub-steps. Empty
     * (default) means the projector renders the task as a single step
     * with its [estimatedDurationMs] (or a 5-minute fallback) as the
     * sole step duration. See [TaskSubStep] kdoc for the decision
     * trail.
     */
    val subSteps: List<TaskSubStep> = emptyList(),
    /**
     * Round 2.16.B — whole-task estimate fallback used when [subSteps]
     * is empty. Default 5 minutes. (TaskModels has no flat duration
     * field today; a TOML-schema-adding round may replace this.)
     */
    val estimatedDurationMs: Long = 5L * 60_000L,
) {
    val isOverdue: Boolean get() = !done && due != null && due.isBefore(LocalDate.now())
}

enum class AttachmentKind { Link, Qr, File, Barcode, VCard, Location }

@Immutable
data class TaskAttachment(
    val kind: AttachmentKind,
    val label: String,
    val target: String,
)

/**
 * Phase 2.1.D.6 — returns `true` iff [list].activeHours is non-empty and
 * contains [at]'s day-of-week + local time (in [list].tzId). Empty
 * `activeHours` means "always" — not a "boost" trigger.
 */
internal fun TodolistInfo.isInsideActiveHours(at: ZonedDateTime): Boolean {
    if (activeHours.isEmpty()) return false
    val local = at.withZoneSameInstant(tzId)
    val dow = local.dayOfWeek
    val t = local.toLocalTime()
    return activeHours.any { h -> matchesHour(h, dow, t) }
}

private fun matchesHour(h: HourRange, dow: DayOfWeek, t: LocalTime): Boolean = when {
    h.to == h.from -> false
    h.to.isAfter(h.from) ->
        h.day == dow && !t.isBefore(h.from) && t.isBefore(h.to)
    else -> {
        // midnight rollover
        (h.day == dow && !t.isBefore(h.from)) ||
            (h.day == dow.minus(1) && t.isBefore(h.to))
    }
}

/**
 * Phase 2.1.D.5 / D.6 — effective priority used by Combined sort:
 * task's own priority + its todolist's priority + active-hours bump.
 */
internal fun TaskItem.effectivePriority(now: ZonedDateTime): Int {
    val bump = if (todolist.isInsideActiveHours(now)) ACTIVE_HOURS_PRIORITY_BUMP else 0
    return priority + todolist.priority + bump
}

/**
 * Public sort used by Combined/Per-list: overdue → due asc →
 * effective priority desc → title.
 *
 * `effectivePriority` folds in `todolist.priority` (D.5) plus the
 * active-hours bump (D.6).
 */
fun List<TaskItem>.sortedForCombined(
    today: LocalDate = LocalDate.now(),
    now: ZonedDateTime = ZonedDateTime.now(),
): List<TaskItem> {
    val (active, done) = partition { !it.done }
    val activeSorted = active.sortedWith(
        compareBy<TaskItem> {
            // overdue first (negative bucket), then upcoming, then no-due
            when {
                it.due == null -> 2
                it.due.isBefore(today) -> 0
                else -> 1
            }
        }
            .thenBy { it.due ?: LocalDate.MAX }
            .thenByDescending { it.effectivePriority(now) }
            .thenBy { it.title.lowercase() },
    )
    val doneSorted = done.sortedByDescending { it.doneAt ?: LocalDate.MIN }
    return activeSorted + doneSorted
}

fun List<TaskItem>.sortedForStanding(): List<TaskItem> =
    filter { it.standing && it.due == null }
        .sortedWith(compareByDescending<TaskItem> { it.priority }.thenBy { it.title.lowercase() })

fun List<TaskItem>.sortedForShopping(): List<TaskItem> {
    val (active, done) = partition { !it.done }
    return active.sortedWith(
        compareByDescending<TaskItem> { it.todolist.priority }
            .thenBy { it.title.lowercase() },
    ) + done.sortedBy { it.title.lowercase() }
}

/** Filter for Today view: today-dated, overdue, spawned-from-events, pinned standing. */
fun List<TaskItem>.forToday(today: LocalDate = LocalDate.now()): List<TaskItem> =
    filter { item ->
        when {
            item.standing && item.pinnedForToday -> true
            item.source == TaskSource.FromEvents -> true
            item.due == today -> true
            item.isOverdue -> true
            else -> false
        }
    }
