package com.eight87.strictlykeptboy.task

import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TaskSubStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Round 2.16.B — read-only projector.
 *
 * Folds the small mutable state from [ActiveTaskController] together
 * with the read-only task-source snapshot ([tasksFlow]) into a
 * [TaskPlaybackState] + [TaskQueueSnapshot] pair that
 * [com.eight87.strictlykeptboy.ui.playing.MiniPlayer] /
 * [com.eight87.strictlykeptboy.ui.playing.NowPlayingScreen] consume
 * via the [TaskNowPlayingState] facet.
 */
class TaskPlaybackProjector(
    private val controller: ActiveTaskController,
    private val tasksFlow: StateFlow<List<TaskItem>>,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Drives the per-second re-emission while a task is running so the
     *  on-screen countdown ticks every wall-clock second. The cancellable
     *  delay is sufficient — the consumer's scope cancels this flow when
     *  the scope is torn down. */
    private val tickFlow: Flow<Long> = flow {
        var n = 0L
        while (true) {
            emit(n++)
            delay(1_000L)
        }
    }

    /** Fold over controller's individual flows so the projector can
     *  re-emit on any controller change. We bundle them into a single
     *  data class first to stay inside [combine]'s 5-input fast path
     *  for the outer combiner. */
    private data class ControllerSnapshot(
        val currentTaskId: String?,
        val isRunning: Boolean,
        val subStepIndex: Int,
        val subStepStartedAt: Long?,
        val accumulatedSubStepElapsedMs: Long,
        val completedSubStepElapsedMs: List<Long>,
    )

    private val controllerSnapshotFlow: Flow<ControllerSnapshot> = combine(
        controller.currentTaskId,
        controller.isRunning,
        controller.subStepIndex,
        controller.subStepStartedAt,
        combine(
            controller.accumulatedSubStepElapsedMs,
            controller.completedSubStepElapsedMs,
        ) { acc, completed -> acc to completed },
    ) { id, running, idx, startedAt, (acc, completed) ->
        ControllerSnapshot(id, running, idx, startedAt, acc, completed)
    }

    val state: StateFlow<TaskPlaybackState> = combine(
        controllerSnapshotFlow,
        tasksFlow,
        tickFlow,
    ) { snap, tasks, _ ->
        if (snap.currentTaskId == null) {
            TaskPlaybackState.Empty.copy(connectionPhase = ConnectionPhase.Connected)
        } else {
            val task = tasks.firstOrNull { it.id == snap.currentTaskId }
            if (task == null) {
                TaskPlaybackState.Empty.copy(connectionPhase = ConnectionPhase.Connected)
            } else {
                val steps = effectiveSubSteps(task)
                val safeIndex = snap.subStepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
                val currentStep = steps[safeIndex]
                val openWindow = if (snap.isRunning && snap.subStepStartedAt != null) {
                    (clock() - snap.subStepStartedAt).coerceAtLeast(0L)
                } else 0L
                val subStepElapsed = snap.accumulatedSubStepElapsedMs + openWindow
                val taskElapsed = snap.completedSubStepElapsedMs.sum() + subStepElapsed
                val taskDuration = steps.sumOf { it.durationMs }
                TaskPlaybackState(
                    hasMedia = true,
                    taskName = task.title,
                    subStepName = if (steps.size > 1) currentStep.name else task.title,
                    isPlaying = snap.isRunning,
                    subStepElapsedMs = subStepElapsed,
                    subStepDurationMs = currentStep.durationMs,
                    subStepIndex = safeIndex + 1,
                    subStepCount = steps.size,
                    taskElapsedMs = taskElapsed,
                    taskDurationMs = taskDuration,
                    hasNext = safeIndex < steps.size - 1,
                    hasPrevious = safeIndex > 0,
                    shuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    connectionPhase = ConnectionPhase.Connected,
                )
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, TaskPlaybackState.Empty)

    val queue: StateFlow<TaskQueueSnapshot> = combine(
        controller.currentTaskId,
        tasksFlow,
    ) { currentTaskId, tasks ->
        val items = tasks
            .filter { !it.done }
            .map { t ->
                val steps = effectiveSubSteps(t)
                TaskQueueItem(
                    taskId = t.id,
                    taskName = t.title,
                    subStepName = if (steps.size > 1) steps.first().name else "",
                )
            }
        val idx = if (currentTaskId == null) -1
        else items.indexOfFirst { it.taskId == currentTaskId }
        TaskQueueSnapshot(items = items, currentIndex = idx)
    }.stateIn(scope, SharingStarted.Eagerly, TaskQueueSnapshot.Empty)

    /** Resolve a task to its effective sub-step list per D-2.16.g. */
    fun effectiveSubSteps(task: TaskItem): List<TaskSubStep> =
        if (task.subSteps.isNotEmpty()) task.subSteps
        else listOf(TaskSubStep(name = task.title, durationMs = task.estimatedDurationMs))
}

/**
 * Round 2.16.B — adapter that satisfies the existing
 * [TaskNowPlayingState] + [TaskTransportCommands] + [TaskQueueCommands]
 * facets the ported tonearmboy composables consume, by forwarding to
 * the [ActiveTaskController] + [TaskPlaybackProjector].
 *
 * Transport semantics per D-2.16.h:
 *  - togglePlayPause → resume / pause
 *  - seekToNext → controller.nextSubStep(); on overrun past last
 *    sub-step, controller.stop()
 *  - seekToPrevious → controller.previousSubStep()
 *  - stop → controller.stop()
 *  - shuffle / repeat / seekTo are no-ops in Phase B (still wired
 *    through so MiniPlayer / NowPlayingScreen don't crash).
 */
class TaskTransportAdapter(
    private val controller: ActiveTaskController,
    private val projector: TaskPlaybackProjector,
) : TaskNowPlayingState, TaskTransportCommands, TaskQueueCommands {

    override val state: StateFlow<TaskPlaybackState> get() = projector.state
    override val queue: StateFlow<TaskQueueSnapshot> get() = projector.queue

    override fun togglePlayPause() {
        if (controller.isRunning.value) controller.pause() else controller.resume()
    }

    override fun seekTo(positionMs: Long) { /* Phase B no-op */ }
    override fun seekBackward() { /* Phase B no-op */ }
    override fun seekForward() { /* Phase B no-op */ }

    override fun seekToPrevious() {
        controller.previousSubStep()
    }

    override fun seekToNext() {
        val cur = projector.state.value
        if (!cur.hasMedia) return
        // 1-based subStepIndex; on the last sub-step, "next" stops the task.
        if (cur.subStepIndex >= cur.subStepCount) {
            controller.stop()
        } else {
            controller.nextSubStep()
        }
    }

    override fun stop() {
        controller.stop()
    }

    override fun toggleShuffle() { /* Phase B no-op */ }
    override fun cycleRepeatMode() { /* Phase B no-op */ }

    override fun seekToQueueIndex(index: Int) {
        val items = projector.queue.value.items
        val target = items.getOrNull(index) ?: return
        controller.start(target.taskId)
    }

    override fun removeQueueItem(index: Int) { /* Phase B no-op */ }
    override fun moveQueueItem(from: Int, to: Int) { /* Phase B no-op */ }
}
