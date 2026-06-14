package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.resolver.ActiveSetEvaluator
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.TodolistRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Phase H — five top-level task views per UI-J.
 *
 * Phase U.4 / F11 note: [label] is **wire-format** / stable toString fallback.
 * Translatable UI display routes through `TaskViewTab.labelString()` in
 * `ui/a11y/EnumLabels.kt`.
 */
@Deprecated("Use TasksFilter (Round 2.26)")
enum class TaskViewTab(val label: String) {
    Combined("Combined"),
    Today("Today"),
    PerList("Per-list"),
    Shopping("Shopping"),
    Standing("Standing"),
}

@Immutable
data class TasksUiState(
    val tasks: List<TaskItem> = emptyList(),
    val todolists: List<TodolistInfo> = emptyList(),
    /**
     * Phase 2.1.D.2 — per-todolist visibility filter set by the source
     * rail (chip taps). Empty = "all visible" (no chip selected).
     */
    val hiddenTodolistIds: Set<String> = emptySet(),
    /**
     * Phase 2.1.D.1 — when `false` (default), tasks belonging to a
     * todolist outside `ActiveSetEvaluator.activeTodolistsAt(now)` drop
     * out of Combined / Today. When `true`, they all render.
     */
    val showInactive: Boolean = false,
    /**
     * Phase 2.1.D.1 — the set of todolist IDs that the resolver considers
     * active at the current instant. Filled by the binder that wires
     * `ActiveSetEvaluator` into this view-state. Empty set + non-empty
     * `todolists` means "evaluator hasn't run yet" — we treat that as
     * "everything active" to avoid an empty-tasks flash during boot.
     */
    val activeTodolistIds: Set<String> = emptySet(),
    /**
     * Phase 2.1.D.4 — multi-repo overlay flag. When `true`, the task row
     * renders a small repo dot. Driven by AppGraph (repo count > 1).
     */
    val multiRepo: Boolean = false,
    /**
     * Phase 2.1.D.3 — the active repo's owner / identity string (used to
     * decide whether to render `TaskItem.author` chip — only foreign
     * authors show).
     */
    val activeRepoOwner: String = "",
    /**
     * D-12 fix — map from repoId → repo display name for the By Repo
     * section headers. Falls back to the raw repoId when the display name
     * is absent from this map.
     */
    val repoDisplayNames: Map<String, String> = emptyMap(),
)

/**
 * Minimal state holder for Phase H. Mirrors the [com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState]
 * pattern but is intentionally synchronous + push-mutable to keep the
 * pane testable without Room. A real ViewModel-backed version lands when
 * the indexer→view glue is built out (deferred — see Phase H notes).
 */
class TasksViewState(initial: TasksUiState = TasksUiState()) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    /**
     * D-5 fix — optional callback invoked after a task transitions to
     * `done = true`. The composition root wires this to the file-write
     * path (read entity from disk → stamp `done`+`doneAt` → write back
     * via EntityWriter → incremental re-scan). Not called when
     * un-marking (un-done clears the date and the file write is handled
     * symmetrically via the same hook if non-null).
     *
     * Signature: `(updatedItem: TaskItem) -> Unit`. The callback receives
     * the **already-updated** in-memory item so the writer doesn't have
     * to re-derive the new state.
     */
    var onTaskMarkedDone: ((TaskItem) -> Unit)? = null

    fun set(state: TasksUiState) { _state.value = state }

    /**
     * D-5 fix — toggle `done` on [taskId], stamping [doneAt] to today
     * when transitioning to done, and clearing it when un-marking.
     * Invokes [onTaskMarkedDone] after the state update so the
     * composition root can persist the change to the task file.
     */
    fun toggleDone(taskId: String, today: LocalDate = LocalDate.now()) {
        var updated: TaskItem? = null
        _state.value = _state.value.copy(
            tasks = _state.value.tasks.map { task ->
                if (task.id == taskId) {
                    val nowDone = !task.done
                    val item = task.copy(
                        done = nowDone,
                        doneAt = if (nowDone) today else null,
                    )
                    updated = item
                    item
                } else {
                    task
                }
            },
        )
        updated?.let { onTaskMarkedDone?.invoke(it) }
    }

    fun pinStanding(taskId: String, pinned: Boolean) {
        _state.value = _state.value.copy(
            tasks = _state.value.tasks.map {
                if (it.id == taskId) it.copy(pinnedForToday = pinned) else it
            },
        )
    }

    fun addTask(item: TaskItem) {
        _state.value = _state.value.copy(tasks = _state.value.tasks + item)
    }

    /** Phase 2.1.D.2 — flip a todolist's chip-filter on/off. */
    fun toggleListVisibility(todolistId: String) {
        val cur = _state.value.hiddenTodolistIds
        _state.value = _state.value.copy(
            hiddenTodolistIds = if (todolistId in cur) cur - todolistId else cur + todolistId,
        )
    }

    /** Phase 2.1.D.1 — flip the show-inactive toggle. */
    fun setShowInactive(value: Boolean) {
        _state.value = _state.value.copy(showInactive = value)
    }

    /** Phase 2.1.D.1 — push a freshly-evaluated active-todolist set in. */
    fun setActiveTodolistIds(ids: Set<String>) {
        _state.value = _state.value.copy(activeTodolistIds = ids)
    }

    /** Phase 2.1.D.7 — record a task→event link. */
    fun linkToEvent(taskId: String, eventId: String, start: ZonedDateTime?) {
        _state.value = _state.value.copy(
            tasks = _state.value.tasks.map {
                if (it.id == taskId) it.copy(linkedEventId = eventId, linkedEventStart = start) else it
            },
        )
    }
}

/**
 * Phase 2.1.D.1 — pure-function evaluator binding. Caller owns the
 * `ActiveSetEvaluator` + `RepoSnapshot` lifecycle and pumps the result
 * into `TasksViewState.setActiveTodolistIds`. Kept top-level (not a
 * method on TasksViewState) so the view-state stays Android-free /
 * unit-testable without a snapshot dependency.
 */
fun evaluateActiveTodolistIds(
    evaluator: ActiveSetEvaluator,
    snapshot: RepoSnapshot,
    now: ZonedDateTime = ZonedDateTime.now(),
): Set<String> = evaluator.activeTodolistsAt(now, snapshot)
    .map { it: TodolistRef -> it.id }
    .toSet()

/**
 * Round 2.5.D.2 — filter a [RepoSnapshot] down to only those todolists
 * whose owning repo has `drawTasksFrom = true`. Repos / calendars are
 * left intact; only the `todolists` list is filtered. Empty
 * [drawTasksFromRepoIds] ⇒ identity (back-compat).
 */
fun filterSnapshotForTasks(
    snapshot: RepoSnapshot,
    drawTasksFromRepoIds: Set<String>,
): RepoSnapshot {
    if (drawTasksFromRepoIds.isEmpty()) return snapshot
    return snapshot.copy(
        todolists = snapshot.todolists.filter { it.repo.id in drawTasksFromRepoIds },
    )
}

/**
 * Phase 2.1.D.1 / D.2 — final filter applied by views over
 * [TasksUiState.tasks]. Drops:
 *   - tasks whose todolist is in [TasksUiState.hiddenTodolistIds];
 *   - tasks whose todolist is NOT in [TasksUiState.activeTodolistIds]
 *     when [TasksUiState.showInactive] is false (and the set is
 *     non-empty — empty set means "evaluator didn't run yet, show all").
 */
fun TasksUiState.visibleTasks(): List<TaskItem> {
    val activeKnown = activeTodolistIds.isNotEmpty()
    return tasks.filter { t ->
        if (t.todolist.id in hiddenTodolistIds) return@filter false
        if (!showInactive && activeKnown && t.todolist.id !in activeTodolistIds) return@filter false
        true
    }
}
