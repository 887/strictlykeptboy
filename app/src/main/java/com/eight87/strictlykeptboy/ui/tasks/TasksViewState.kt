package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Phase H — five top-level task views per UI-J. */
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

    fun set(state: TasksUiState) { _state.value = state }

    fun toggleDone(taskId: String) {
        _state.value = _state.value.copy(
            tasks = _state.value.tasks.map {
                if (it.id == taskId) it.copy(done = !it.done) else it
            },
        )
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
}
