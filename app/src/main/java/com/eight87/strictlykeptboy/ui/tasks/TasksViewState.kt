package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.eight87.strictlykeptboy.resolver.ActiveSetEvaluator
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.TodolistRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * D-27 — CompositionLocal carrying the set of task IDs currently in the
 * completion beat window. [NowPlayingSheetHost] provides this from
 * [TasksUiState.animatingDoneIds] so [TaskTodayView] can apply the
 * fade-out alpha without needing an explicit parameter threaded through
 * [ExpandedNowPlayingTaskBody] (which is owned by another agent).
 */
val LocalAnimatingDoneIds = compositionLocalOf<Set<String>> { emptySet() }

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
     * D-27 — set of task IDs currently in the "completion beat" animation
     * window (~600ms strikethrough+dim before the row leaves Today). While
     * a task's ID is in this set, Today keeps it visible with done=true
     * styling even though forToday() would normally exclude it once done.
     */
    val animatingDoneIds: Set<String> = emptySet(),
    /**
     * D-27 — task IDs that have finished their beat window and should no
     * longer appear in Today even though they are done-today tasks.
     * Populated 600ms after mark-done; cleared on un-mark.
     */
    val beatCompletedIds: Set<String> = emptySet(),
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
class TasksViewState(
    initial: TasksUiState = TasksUiState(),
    /**
     * D-27 / D-28 — coroutine scope for the completion beat timer (defaults
     * to a supervisor scope on [Dispatchers.Default]). [MutableStateFlow.value]
     * is thread-safe so updates from a non-Main dispatcher are fine here.
     * Tests can pass a controlled scope to advance time manually.
     */
    private val beatScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    /**
     * D-5 fix — optional callback invoked after a task transitions to
     * `done = true` or back to `done = false` (via [undoDone]). The
     * composition root wires this to the file-write path (read entity
     * from disk → stamp `done`+`doneAt` → write back via EntityWriter →
     * incremental re-scan).
     *
     * Signature: `(updatedItem: TaskItem) -> Unit`. The callback receives
     * the **already-updated** in-memory item so the writer doesn't have
     * to re-derive the new state. When `item.done == false` the writer
     * clears `doneAt` (its own `if (item.done)` guard handles both
     * directions correctly).
     */
    var onTaskMarkedDone: ((TaskItem) -> Unit)? = null

    /**
     * D-28 — optional callback invoked with the task ID immediately after
     * the completion beat starts, so the host can show a snackbar with an
     * Undo action. The host calls [undoDone] if the user taps Undo within
     * the snackbar timeout.
     */
    var onShowUndoSnackbar: ((taskId: String) -> Unit)? = null

    fun set(state: TasksUiState) { _state.value = state }

    /**
     * D-5 fix + D-27 / D-28 extension — toggle `done` on [taskId].
     *
     * When marking a task **done**:
     *  1. Stamps [doneAt] to today and flips `done = true`.
     *  2. Adds [taskId] to [TasksUiState.animatingDoneIds] so Today keeps
     *     the row visible with strikethrough+dim for the beat window.
     *  3. Fires [onShowUndoSnackbar] so the host can show the Undo snackbar.
     *  4. After [beatMs] ms removes [taskId] from [animatingDoneIds] so the
     *     row finally slides out of Today.
     *  5. Fires [onTaskMarkedDone] for the file-write path.
     *
     * When **un-marking** (done → not done), clears `done`+`doneAt` and
     * removes from [animatingDoneIds] immediately — no beat, no snackbar.
     */
    fun toggleDone(
        taskId: String,
        today: LocalDate = LocalDate.now(),
        beatMs: Long = 600L,
    ) {
        var updated: TaskItem? = null
        val wasAlreadyDone = _state.value.tasks.firstOrNull { it.id == taskId }?.done ?: false
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
            // D-27 — when un-marking, clear any in-progress beat immediately.
            animatingDoneIds = if (wasAlreadyDone) {
                _state.value.animatingDoneIds - taskId
            } else {
                _state.value.animatingDoneIds + taskId
            },
            // D-27 — un-marking clears it from the beat-completed (hidden) set too.
            beatCompletedIds = if (wasAlreadyDone) {
                _state.value.beatCompletedIds - taskId
            } else {
                _state.value.beatCompletedIds
            },
        )
        val item = updated ?: return
        if (item.done) {
            // D-28 — notify host to show the Undo snackbar.
            onShowUndoSnackbar?.invoke(taskId)
            // D-27 — after beat window, move from animating set to beat-completed
            // (hidden) set so the row finally disappears from Today.
            beatScope.launch {
                delay(beatMs)
                _state.value = _state.value.copy(
                    animatingDoneIds = _state.value.animatingDoneIds - taskId,
                    beatCompletedIds = _state.value.beatCompletedIds + taskId,
                )
            }
            onTaskMarkedDone?.invoke(item)
        }
    }

    /**
     * D-28 — undo a completion: un-marks [taskId] without triggering the
     * beat or snackbar again. Clears `done`+`doneAt` and removes from
     * [animatingDoneIds] and [beatCompletedIds] immediately.
     *
     * D-44 fix — also calls [onTaskMarkedDone] with the un-done item so
     * the disk write is reversed (the same callback handles `done=false`
     * by clearing `doneAt` and reindexing). Without this the reactive
     * Room snapshot would overwrite the in-memory undo on the next update,
     * silently keeping the task in Done.
     */
    fun undoDone(taskId: String) {
        var undoneItem: TaskItem? = null
        _state.value = _state.value.copy(
            tasks = _state.value.tasks.map { task ->
                if (task.id == taskId) {
                    val item = task.copy(done = false, doneAt = null)
                    undoneItem = item
                    item
                } else task
            },
            animatingDoneIds = _state.value.animatingDoneIds - taskId,
            beatCompletedIds = _state.value.beatCompletedIds - taskId,
        )
        // D-44 fix — record a pending override so the reactive collector
        // re-applies done=false even when the Room snapshot fires before
        // the async disk write + re-scan complete (which would otherwise
        // overwrite the in-memory undo with done=true from the old cache).
        _pendingDoneOverrides[taskId] = false
        // D-44 — reverse the disk write so the Room snapshot also reflects
        // done=false on the next re-scan. The onTaskMarkedDone callback
        // handles both directions: when item.done==false it writes
        // done=false + clears doneAt to disk and triggers incrementalScan.
        // After incrementalScan the Room snapshot delivers done=false and
        // the collector calls clearDoneOverride(taskId) to clean up.
        undoneItem?.let { onTaskMarkedDone?.invoke(it) }
    }

    fun pinStanding(taskId: String, pinned: Boolean) {
        _state.value = _state.value.copy(
            tasks = _state.value.tasks.map {
                if (it.id == taskId) it.copy(pinnedForToday = pinned) else it
            },
        )
    }

    /**
     * D-44 fix — pending done-state overrides. When [undoDone] is called
     * the in-memory flip happens immediately, but the Room snapshot may
     * overwrite it before the async disk write + re-scan complete. Storing
     * the pending override here lets [set] re-apply it when merging from
     * disk on the next collect cycle. The key is taskId; value is the
     * forced done state. Cleared by [clearDoneOverride] once the Room
     * snapshot delivers the on-disk value (called from the collector after
     * the disk write + incrementalScan confirm the new state).
     */
    private val _pendingDoneOverrides = ConcurrentHashMap<String, Boolean>()

    /** D-44 — record a pending done override that survives the next set(). */
    fun setPendingDoneOverride(taskId: String, done: Boolean) {
        _pendingDoneOverrides[taskId] = done
    }

    /** D-44 — remove an override once the Room snapshot has caught up. */
    fun clearDoneOverride(taskId: String) {
        _pendingDoneOverrides.remove(taskId)
    }

    /** D-44 — snapshot the pending overrides for the reactive collector. */
    fun pendingDoneOverridesSnapshot(): Map<String, Boolean> = _pendingDoneOverrides.toMap()

    /**
     * D-45 fix — in-memory tasks added via [addTask] that are NOT backed
     * by a real disk entity (demo-list / read-only targets). The reactive
     * Room snapshot collector calls [set] and replaces `tasks` from disk,
     * which would overwrite purely in-memory additions. Storing them here
     * separately lets the collector merge them back in on every refresh so
     * they stay visible. Items are removed when [set] is called with a
     * `tasks` list that already contains an entity with the same id (i.e.
     * the disk write eventually lands and the Room scan picks it up).
     */
    private val _inMemoryTasks = mutableListOf<TaskItem>()

    fun addTask(item: TaskItem) {
        // D-45 fix — record the item in the in-memory list so it
        // survives the next reactive set() call that rebuilds from disk.
        _inMemoryTasks.add(item)
        _state.value = _state.value.copy(tasks = _state.value.tasks + item)
    }

    /**
     * D-45 — expose the in-memory task set for the reactive collector so
     * it can merge them into mergedBase on every snapshot refresh.
     */
    fun inMemoryTasksSnapshot(): List<TaskItem> = _inMemoryTasks.toList()

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
