package com.eight87.strictlykeptboy.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 2.16.B — in-memory active-task state.
 *
 * Owns "which task is running, on which sub-step, and how long it's
 * been running" — the small mutable bit of state that
 * [TaskPlaybackProjector] folds together with the read-only task
 * store snapshot into a [TaskPlaybackState] for the mini-player +
 * NowPlayingScreen.
 *
 * **Round 2.16 Phase B — in-memory only.** Active-task state does
 * NOT survive process death. Restarting the app loses the running
 * task and all accumulated sub-step elapsed time. Persistence is
 * deferred to Round 2.17.
 *
 * SOLID:
 *  - **S:** controller-of-active-task-state, nothing else. The
 *    projector does the read-only fold; queue ordering lives on the
 *    task source; clock injection makes this trivially testable.
 *  - **D:** [scope] + [clock] are constructor params so unit tests
 *    can drive a TestScope + virtual clock.
 *
 * The 0-based [subStepIndex] is an implementation detail; the
 * projector maps it to a 1-based display index per D-2.16.d.
 */
class ActiveTaskController(
    @Suppress("unused") private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _currentTaskId = MutableStateFlow<String?>(null)
    val currentTaskId: StateFlow<String?> = _currentTaskId.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _subStepIndex = MutableStateFlow(0)
    val subStepIndex: StateFlow<Int> = _subStepIndex.asStateFlow()

    /** Wall-clock ms when the current running window of this sub-step began.
     *  Null whenever [isRunning] is false (paused or stopped). */
    private val _subStepStartedAt = MutableStateFlow<Long?>(null)
    val subStepStartedAt: StateFlow<Long?> = _subStepStartedAt.asStateFlow()

    /** Elapsed time accumulated in the current sub-step during prior
     *  running windows (i.e. windows that have already been paused). */
    private val _accumulatedSubStepElapsedMs = MutableStateFlow(0L)
    val accumulatedSubStepElapsedMs: StateFlow<Long> = _accumulatedSubStepElapsedMs.asStateFlow()

    /** Per-completed-sub-step durations; index N = total elapsed in the
     *  N-th sub-step (paused windows included), captured at the moment
     *  [nextSubStep] advanced past it. */
    private val _completedSubStepElapsedMs = MutableStateFlow<List<Long>>(emptyList())
    val completedSubStepElapsedMs: StateFlow<List<Long>> = _completedSubStepElapsedMs.asStateFlow()

    fun start(taskId: String) {
        _currentTaskId.value = taskId
        _subStepIndex.value = 0
        _accumulatedSubStepElapsedMs.value = 0L
        _completedSubStepElapsedMs.value = emptyList()
        _subStepStartedAt.value = clock()
        _isRunning.value = true
    }

    fun pause() {
        if (!_isRunning.value) return
        val started = _subStepStartedAt.value
        if (started != null) {
            _accumulatedSubStepElapsedMs.value += (clock() - started).coerceAtLeast(0L)
        }
        _subStepStartedAt.value = null
        _isRunning.value = false
    }

    fun resume() {
        if (_currentTaskId.value == null) return
        if (_isRunning.value) return
        _subStepStartedAt.value = clock()
        _isRunning.value = true
    }

    /**
     * Commit the current sub-step's elapsed time to
     * [completedSubStepElapsedMs] and increment the index. The
     * "current sub-step count" is enforced by [TaskPlaybackProjector]
     * — this controller is shape-agnostic and just keeps incrementing
     * until the projector or caller invokes [stop].
     *
     * Callers (i.e. the projector / transport-row wiring) should call
     * [stop] when they detect "advanced past last sub-step".
     */
    fun nextSubStep() {
        if (_currentTaskId.value == null) return
        val current = currentSubStepElapsedMs()
        _completedSubStepElapsedMs.value = _completedSubStepElapsedMs.value + current
        _accumulatedSubStepElapsedMs.value = 0L
        _subStepIndex.value += 1
        if (_isRunning.value) {
            _subStepStartedAt.value = clock()
        } else {
            _subStepStartedAt.value = null
        }
    }

    fun previousSubStep() {
        if (_currentTaskId.value == null) return
        if (_subStepIndex.value <= 0) {
            // already at first sub-step — just reset elapsed timers
            _accumulatedSubStepElapsedMs.value = 0L
            _subStepStartedAt.value = if (_isRunning.value) clock() else null
            return
        }
        _subStepIndex.value -= 1
        // drop the most-recently-completed entry if any (so the
        // previous sub-step's running elapsed restarts cleanly)
        val completed = _completedSubStepElapsedMs.value
        if (completed.isNotEmpty()) {
            _completedSubStepElapsedMs.value = completed.dropLast(1)
        }
        _accumulatedSubStepElapsedMs.value = 0L
        _subStepStartedAt.value = if (_isRunning.value) clock() else null
    }

    fun stop() {
        _currentTaskId.value = null
        _isRunning.value = false
        _subStepIndex.value = 0
        _subStepStartedAt.value = null
        _accumulatedSubStepElapsedMs.value = 0L
        _completedSubStepElapsedMs.value = emptyList()
    }

    /** Snapshot of the elapsed time in the current sub-step
     *  (accumulated + the open running window, if any). */
    private fun currentSubStepElapsedMs(): Long {
        val started = _subStepStartedAt.value
        val open = if (started != null && _isRunning.value) {
            (clock() - started).coerceAtLeast(0L)
        } else 0L
        return _accumulatedSubStepElapsedMs.value + open
    }
}
