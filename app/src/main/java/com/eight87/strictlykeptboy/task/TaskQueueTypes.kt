package com.eight87.strictlykeptboy.task

/**
 * Round 2.16.A — facade types replacing tonearmboy's `QueueItem` /
 * `QueueSnapshot`. Field names rename music → task:
 *
 *   mediaId → taskId
 *   title   → taskName
 *   artist  → subStepName
 *   mediaStoreAlbumId → (dropped)
 */
data class TaskQueueItem(
  val taskId: String,
  val taskName: String,
  val subStepName: String,
)

data class TaskQueueSnapshot(
  val items: List<TaskQueueItem>,
  val currentIndex: Int,
) {
  companion object {
    val Empty = TaskQueueSnapshot(emptyList(), -1)
  }
}
