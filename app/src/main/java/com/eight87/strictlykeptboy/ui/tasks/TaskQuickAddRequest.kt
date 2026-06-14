package com.eight87.strictlykeptboy.ui.tasks

import java.time.LocalDate

/**
 * Round 2.16.E — moved out of the now-deleted `TasksPane.kt`. The
 * data class is still consumed by `SkbAppShell` (top-level `onWriteTask`
 * callback wired via MainActivity) and `TasksDemoSeed`, and used by
 * `TaskQuickAddSheet` to package the user's quick-add input.
 *
 * Pure data type — no Compose dependencies.
 *
 * D-40 — [due] is optional; only set when the user explicitly picks a due
 * date in the Quick Add sheet for a Todolist target. When null the task
 * is written as a StandingTask (no due date). When set it is written as a
 * dated Task so it appears in the Upcoming view.
 */
data class TaskQuickAddRequest(
    val title: String,
    val target: QuickAddTarget,
    val due: LocalDate? = null,
)
