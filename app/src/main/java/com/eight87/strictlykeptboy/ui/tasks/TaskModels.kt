package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.runtime.Immutable
import java.time.LocalDate

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

/** Public sort used by Combined/Per-list: overdue → due asc → priority desc → title. */
fun List<TaskItem>.sortedForCombined(today: LocalDate = LocalDate.now()): List<TaskItem> {
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
            .thenByDescending { it.priority }
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
