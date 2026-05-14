package com.eight87.strictlykeptboy.task

import com.eight87.strictlykeptboy.ui.tasks.TaskItem
import com.eight87.strictlykeptboy.ui.tasks.TaskSubStep
import com.eight87.strictlykeptboy.ui.tasks.TodolistInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.16.B unit gate (per the plan's verification gates):
 * the controller + projector emit the right [TaskPlaybackState] /
 * [TaskQueueSnapshot] for a known input across the start/pause/
 * resume/next lifecycle.
 *
 * Clock is injected so wall-clock elapsed is deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskPlaybackProjectorTest {

    private val tdl = TodolistInfo(id = "l1", repoId = "r1", name = "Care")

    private val grooming = TaskItem(
        id = "t1",
        title = "Grooming",
        todolist = tdl,
        subSteps = listOf(
            TaskSubStep(name = "brushing teeth", durationMs = 180_000L),
            TaskSubStep(name = "shower", durationMs = 300_000L),
            TaskSubStep(name = "moisturizer", durationMs = 60_000L),
        ),
    )

    private val singleStep = TaskItem(
        id = "t2",
        title = "Make bed",
        todolist = tdl,
        estimatedDurationMs = 120_000L,
    )

    @Test
    fun `empty state when no current task`() = runTest(UnconfinedTestDispatcher()) {
        var now = 0L
        val controller = ActiveTaskController(scope = TestScope(), clock = { now })
        val tasks = MutableStateFlow(listOf(grooming))
        val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

        val s = projector.state.value
        assertFalse(s.hasMedia)
        assertEquals(ConnectionPhase.Connected, s.connectionPhase)
    }

    @Test
    fun `start emits projected playback state with sub-step 1 of 3`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 1000L
            val controller = ActiveTaskController(scope = TestScope(), clock = { now })
            val tasks = MutableStateFlow(listOf(grooming))
            val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

            controller.start("t1")
            val s = projector.state.value
            assertTrue(s.hasMedia)
            assertEquals("Grooming", s.taskName)
            assertEquals("brushing teeth", s.subStepName)
            assertEquals(1, s.subStepIndex)
            assertEquals(3, s.subStepCount)
            assertEquals(180_000L, s.subStepDurationMs)
            assertEquals(0L, s.subStepElapsedMs)
            assertEquals(180_000L + 300_000L + 60_000L, s.taskDurationMs)
            assertTrue(s.isPlaying)
        }

    @Test
    fun `elapsed advances with wall clock while running`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val controller = ActiveTaskController(scope = TestScope(), clock = { now })
            val tasks = MutableStateFlow(listOf(grooming))
            val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

            controller.start("t1")
            now = 30_000L
            // Force re-emit by toggling something the controller flows on; the
            // tick flow is the production driver but tests use clock control.
            // We hit the controller (toggle a no-op pause/resume) to flush.
            controller.pause()
            controller.resume()
            now = 75_000L
            controller.pause()
            // After pause, elapsed should reflect the additional wall-clock
            // delta since resume (45s) on top of the pre-pause 30s.
            val s = projector.state.value
            assertEquals(75_000L, s.subStepElapsedMs)
            assertFalse(s.isPlaying)
        }

    @Test
    fun `next sub-step advances and resets sub-step elapsed`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val controller = ActiveTaskController(scope = TestScope(), clock = { now })
            val tasks = MutableStateFlow(listOf(grooming))
            val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

            controller.start("t1")
            now = 100_000L
            controller.nextSubStep()
            val s = projector.state.value
            assertEquals(2, s.subStepIndex)
            assertEquals("shower", s.subStepName)
            assertEquals(300_000L, s.subStepDurationMs)
            assertEquals(0L, s.subStepElapsedMs)
            // whole-task elapsed includes the completed first sub-step's 100s
            assertEquals(100_000L, s.taskElapsedMs)
        }

    @Test
    fun `single-step task uses estimated duration and reports 1 of 1`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val controller = ActiveTaskController(scope = TestScope(), clock = { now })
            val tasks = MutableStateFlow(listOf(singleStep))
            val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

            controller.start("t2")
            val s = projector.state.value
            assertEquals(1, s.subStepIndex)
            assertEquals(1, s.subStepCount)
            assertEquals(120_000L, s.subStepDurationMs)
            assertEquals(120_000L, s.taskDurationMs)
            // For a 1-step task, the projector substitutes the task title for
            // sub-step name (no separate "step name" to show).
            assertEquals("Make bed", s.subStepName)
        }

    @Test
    fun `queue excludes done tasks and tracks current index`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val controller = ActiveTaskController(scope = TestScope(), clock = { now })
            val tasks = MutableStateFlow(listOf(grooming, singleStep, grooming.copy(id = "t3", done = true)))
            val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

            controller.start("t2")
            val q = projector.queue.value
            assertEquals(2, q.items.size) // done task excluded
            assertEquals("t1", q.items[0].taskId)
            assertEquals("t2", q.items[1].taskId)
            assertEquals(1, q.currentIndex)
        }

    @Test
    fun `pause then resume preserves accumulated sub-step elapsed`() =
        runTest(UnconfinedTestDispatcher()) {
            // Round 2.16.G.1 — explicit guard against losing the
            // already-banked elapsed when transport toggles pause → resume.
            var now = 0L
            val controller = ActiveTaskController(scope = TestScope(), clock = { now })
            val tasks = MutableStateFlow(listOf(grooming))
            val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

            controller.start("t1")
            now = 40_000L
            controller.pause()
            // While paused, wall clock advances — must NOT count against elapsed.
            now = 70_000L
            assertEquals(40_000L, projector.state.value.subStepElapsedMs)
            assertFalse(projector.state.value.isPlaying)
            controller.resume()
            now = 95_000L
            // Flush by toggling — production driver is the tick flow.
            controller.pause()
            // Accumulated elapsed = 40s pre-pause + 25s since resume = 65s.
            val s = projector.state.value
            assertEquals(65_000L, s.subStepElapsedMs)
            assertFalse(s.isPlaying)
        }

    @Test
    fun `stop returns to empty state`() = runTest(UnconfinedTestDispatcher()) {
        var now = 0L
        val controller = ActiveTaskController(scope = TestScope(), clock = { now })
        val tasks = MutableStateFlow(listOf(grooming))
        val projector = TaskPlaybackProjector(controller, tasks, scope = backgroundScope, clock = { now })

        controller.start("t1")
        assertTrue(projector.state.value.hasMedia)
        controller.stop()
        assertFalse(projector.state.value.hasMedia)
    }
}
