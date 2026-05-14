package com.eight87.strictlykeptboy.ui.tasks

/**
 * Round 2.16.E — moved out of the now-deleted `TasksPane.kt`. The
 * data class is still consumed by `SkbAppShell` (top-level `onWriteTask`
 * callback wired via MainActivity) and `TasksDemoSeed`, and used by
 * `TaskQuickAddSheet` to package the user's quick-add input.
 *
 * Pure data type — no Compose dependencies.
 */
data class TaskQuickAddRequest(
    val title: String,
    val target: QuickAddTarget,
)
