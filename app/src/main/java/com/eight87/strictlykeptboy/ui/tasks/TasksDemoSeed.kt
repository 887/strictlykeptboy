package com.eight87.strictlykeptboy.ui.tasks

import java.time.LocalDate

/**
 * Phase H — demo seed used by [com.eight87.strictlykeptboy.MainActivity]
 * to render something on the AVD prior to the repo→VM integration. Not
 * referenced from tests (those construct their own fixtures).
 */
object TasksDemoSeed {

    private val today: LocalDate get() = LocalDate.now()

    val groceries = TodolistInfo(
        id = "tl-groceries",
        repoId = "demo",
        name = "Groceries",
        emoji = "🛒",
        colorSeed = "groceries",
        mode = TodolistMode.Shopping,
        priority = 5,
    )
    val errands = TodolistInfo(
        id = "tl-errands",
        repoId = "demo",
        name = "Errands",
        emoji = "📋",
        colorSeed = "errands",
        priority = 3,
    )
    val house = TodolistInfo(
        id = "tl-house",
        repoId = "demo",
        name = "House",
        emoji = "🏠",
        colorSeed = "house",
        priority = 2,
    )

    fun uiState(): TasksUiState {
        val today = today
        return TasksUiState(
            todolists = listOf(groceries, errands, house),
            tasks = listOf(
                TaskItem(
                    id = "t1", title = "Buy milk", todolist = groceries,
                    priority = 4, tags = listOf("dairy"),
                ),
                TaskItem(
                    id = "t2", title = "Bread", todolist = groceries,
                ),
                TaskItem(
                    id = "t3", title = "Eggs", todolist = groceries,
                    done = true, doneAt = today.minusDays(1),
                ),
                TaskItem(
                    id = "t4", title = "Pay phone bill", todolist = errands,
                    due = today, priority = 7,
                    body = "Call provider if line is busy.\n- [ ] confirm autopay\n- [x] note balance",
                ),
                TaskItem(
                    id = "t5", title = "Drop off package", todolist = errands,
                    due = today.minusDays(2), priority = 5,
                    author = "alex",
                    attachments = listOf(
                        TaskAttachment(AttachmentKind.Link, "tracking", "https://example.com/123"),
                    ),
                ),
                TaskItem(
                    id = "t6", title = "Replace smoke alarm battery", todolist = house,
                    standing = true, priority = 6,
                ),
                TaskItem(
                    id = "t7", title = "Dust bookshelf", todolist = house,
                    standing = true, priority = 2, pinnedForToday = true,
                ),
                TaskItem(
                    id = "t8", title = "Pick up dry cleaning",
                    todolist = errands, due = today.plusDays(3),
                ),
                TaskItem(
                    id = "t9", title = "Stretch routine",
                    todolist = house, source = TaskSource.FromEvents,
                    due = today,
                ),
            ),
        )
    }

    /**
     * Round 2.16.C — sub-stepped demo tasks so the Phase-C mini-player
     * has visible content on the AVD when the petkeptbyai demo
     * perspective is active (its real seeder ships no tasks). Two
     * tasks: Grooming (6 sub-steps) and Bedtime routine (3 sub-steps).
     * TODO Phase D — remove once the in-sheet todolist UI lets the
     * user create sub-stepped tasks directly.
     */
    val groomingDemoTask: TaskItem = TaskItem(
        id = "demo-grooming",
        title = "Grooming",
        todolist = house,
        subSteps = listOf(
            TaskSubStep("brush teeth", 3L * 60_000L),
            TaskSubStep("floss", 2L * 60_000L),
            TaskSubStep("shower", 8L * 60_000L),
            TaskSubStep("dry off", 2L * 60_000L),
            TaskSubStep("apply lotion", 3L * 60_000L),
            TaskSubStep("brush hair", 2L * 60_000L),
        ),
    )
    val bedtimeDemoTask: TaskItem = TaskItem(
        id = "demo-bedtime",
        title = "Bedtime routine",
        todolist = house,
        subSteps = listOf(
            TaskSubStep("wash face", 4L * 60_000L),
            TaskSubStep("set alarm", 1L * 60_000L),
            TaskSubStep("read", 15L * 60_000L),
        ),
    )

    /** Tasks-with-substeps demo set for Phase C AVD scenarios. */
    val substeppedDemoTasks: List<TaskItem> = listOf(groomingDemoTask, bedtimeDemoTask)

    fun materialize(req: TaskQuickAddRequest): TaskItem {
        val target = req.target
        val list = when (target) {
            is QuickAddTarget.Todolist -> target.info
            else -> errands
        }
        val due = when (target) {
            QuickAddTarget.TodayEvent -> today
            QuickAddTarget.TomorrowEvent -> today.plusDays(1)
            else -> null
        }
        return TaskItem(
            id = "user-" + System.currentTimeMillis(),
            title = req.title,
            todolist = list,
            due = due,
        )
    }
}
