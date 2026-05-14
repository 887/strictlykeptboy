package com.eight87.strictlykeptboy.task

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 2.16.A — Phase A only. Hardcoded "Grooming / brushing teeth
 * 2/6, 2:15 remaining, taskElapsed=8min/30min" source for the
 * verbatim tonearmboy port. Phase B replaces this with a real
 * reactive projector over `ActiveTaskController`.
 *
 * The values match the worked example in the Phase A spec:
 *  - taskName = "Grooming"
 *  - subStepName = "brushing teeth"
 *  - subStepIndex = 2, subStepCount = 6
 *  - subStepElapsedMs corresponds to 2:15 remaining of a 3-minute sub-step
 *  - taskElapsedMs = 8 min, taskDurationMs = 30 min
 */
object StubTaskPlaybackSource : TaskNowPlayingState, TaskTransportCommands, TaskQueueCommands {

  private val _state = MutableStateFlow(
    TaskPlaybackState(
      hasMedia = true,
      taskName = "Grooming",
      subStepName = "brushing teeth",
      isPlaying = true,
      subStepElapsedMs = 45_000L,           // 0:45 elapsed of 3:00 → 2:15 remaining
      subStepDurationMs = 180_000L,
      subStepIndex = 2,
      subStepCount = 6,
      taskElapsedMs = 8L * 60_000L,
      taskDurationMs = 30L * 60_000L,
      hasNext = true,
      hasPrevious = true,
      shuffleEnabled = false,
      repeatMode = RepeatMode.OFF,
      connectionPhase = ConnectionPhase.Connected,
    )
  )
  override val state: StateFlow<TaskPlaybackState> = _state.asStateFlow()

  private val _queue = MutableStateFlow(
    TaskQueueSnapshot(
      items = listOf(
        TaskQueueItem("stub-1", "Make bed", "fluff pillows"),
        TaskQueueItem("stub-2", "Breakfast", "boil eggs"),
        TaskQueueItem("stub-3", "Grooming", "brushing teeth"),
        TaskQueueItem("stub-4", "Commute", "walk to bus"),
        TaskQueueItem("stub-5", "Standup", "stretch"),
        TaskQueueItem("stub-6", "Inbox sweep", "triage P0"),
      ),
      currentIndex = 2,
    )
  )
  override val queue: StateFlow<TaskQueueSnapshot> = _queue.asStateFlow()

  // -- Stubbed transport commands (Phase B wires real ones) --
  override fun togglePlayPause() { /* stub */ }
  override fun seekTo(positionMs: Long) { /* stub */ }
  override fun seekBackward() { /* stub */ }
  override fun seekForward() { /* stub */ }
  override fun seekToPrevious() { /* stub */ }
  override fun seekToNext() { /* stub */ }
  override fun stop() { /* stub */ }
  override fun toggleShuffle() { /* stub */ }
  override fun cycleRepeatMode() { /* stub */ }

  // -- Stubbed queue commands --
  override fun seekToQueueIndex(index: Int) { /* stub */ }
  override fun removeQueueItem(index: Int) { /* stub */ }
  override fun moveQueueItem(from: Int, to: Int) { /* stub */ }
}
