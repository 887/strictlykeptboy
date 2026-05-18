package com.eight87.strictlykeptboy.ui.tasks

/**
 * Round 2.26.A — Tasks destination filter set (D-2.26.b).
 *
 * Five rail items, default [Today]. Replaces the legacy
 * [TaskViewTab] enum (kept @Deprecated for one round).
 *
 *  - [Today]    — unified day-of feed merging timeboxes + todolist tasks
 *                 (D-2.26.c). Owned by the unified-feed subagent.
 *  - [Upcoming] — tasks with `due` strictly after end-of-today.
 *  - [All]      — every visible task across every list.
 *  - [ByRepo]   — group tasks per-repo. Replaced the prior `PerList`
 *                 category — per-todolist filter chips moved to a
 *                 secondary row on [All].
 *  - [Done]     — completed tasks (`done = true`).
 */
enum class TasksFilter { Today, Upcoming, All, ByRepo, Done }
