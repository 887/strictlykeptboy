package com.eight87.strictlykeptboy.composition

import com.eight87.strictlykeptboy.task.ActiveTaskController
import com.eight87.strictlykeptboy.task.TaskPlaybackProjector
import com.eight87.strictlykeptboy.task.TaskTransportAdapter
import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TasksViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [M] #8d (audit pass 2026-05-17) — Round 2.16.B cohesive sub-graph
 * extracted out of [AppGraph]: canonical `tasksViewState`, the narrow
 * tasks-only flow the projector reads, the in-memory active-task
 * controller, the playback projector, and the transport facet adapter
 * the sheet host consumes. No behavioural change — the same single
 * instance is shared between the schedule shell's task views and the
 * playback projector, so the UI and projector never diverge.
 */
class TaskPlaybackGraph(private val appScope: CoroutineScope) {

    /** Round 2.16.B — single canonical tasks UI state, shared between
     *  the schedule shell's task views and the playback projector. */
    val tasksViewState: TasksViewState by lazy { TasksViewState() }

    /** Round 2.16.B — derived flow of just the tasks list (for the
     *  projector — narrow ISP surface). */
    @Suppress("OPT_IN_USAGE")
    val tasksFlow: StateFlow<List<TaskItem>> by lazy {
        tasksViewState.state
            .map { it.tasks }
            .stateIn(appScope, SharingStarted.Eagerly, tasksViewState.state.value.tasks)
    }

    /** Round 2.16.B — in-memory active-task controller. NOT persisted. */
    val activeTaskController: ActiveTaskController by lazy {
        ActiveTaskController(scope = appScope)
    }

    /** Round 2.16.B — read-only projection consumed by MiniPlayer /
     *  NowPlayingScreen via [taskTransport]. */
    val taskPlaybackProjector: TaskPlaybackProjector by lazy {
        TaskPlaybackProjector(
            controller = activeTaskController,
            tasksFlow = tasksFlow,
            scope = appScope,
        )
    }

    /** Round 2.16.B — facet adapter that the sheet host passes into
     *  MiniPlayer / NowPlayingScreen / QueueSection. Replaces the
     *  Phase A `StubTaskPlaybackSource`. */
    val taskTransport: TaskTransportAdapter by lazy {
        TaskTransportAdapter(
            controller = activeTaskController,
            projector = taskPlaybackProjector,
        )
    }
}
