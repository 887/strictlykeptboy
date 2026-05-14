package com.eight87.strictlykeptboy.task

import kotlinx.coroutines.flow.StateFlow

/**
 * Round 2.16.A — narrow facets mirroring tonearmboy's
 * `NowPlayingState` / `TransportCommands` / `QueueCommands`. Names
 * preserved one-for-one so the ported composables compile against the
 * task-domain facade with no signature drift.
 */
interface TaskNowPlayingState {
  val state: StateFlow<TaskPlaybackState>
  val queue: StateFlow<TaskQueueSnapshot>
}

interface TaskTransportCommands {
  fun togglePlayPause()
  fun seekTo(positionMs: Long)
  fun seekBackward()
  fun seekForward()
  fun seekToPrevious()
  fun seekToNext()
  fun stop()
  fun toggleShuffle()
  fun cycleRepeatMode()
}

interface TaskQueueCommands {
  fun seekToQueueIndex(index: Int)
  fun removeQueueItem(index: Int)
  fun moveQueueItem(from: Int, to: Int)
}
