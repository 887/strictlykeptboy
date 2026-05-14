package com.eight87.strictlykeptboy.ui.playing

/**
 * R.D.5 — pure queue-reorder helpers extracted from `QueueSection.kt`.
 * All three are testable without spinning a real drag-reorder host.
 */

/**
 * D.26.2 — translate a visual queue position into a controller index.
 * Now that the queue list renders every item 1:1, the mapping is the
 * identity (when [currentIndex] >= 0). When the queue has no active
 * item we still bottom out at `visual` so the math stays defined.
 */
internal fun translateVisualToReal(currentIndex: Int, visual: Int): Int = visual

/**
 * D.26.2 — clamp a drag-reorder pair so neither endpoint touches the
 * currently-playing index. Returns null if the move is purely
 * within / against the active row (drop the move silently — the
 * disabled drag handle should make this unreachable in normal use).
 *
 * Rules:
 *  - if [from] == [currentIndex] the active row was somehow dragged →
 *    drop the move;
 *  - if [to] == [currentIndex] we shift the destination by one in
 *    whichever direction preserves the user's intent (move past the
 *    active row rather than displacing it);
 *  - if from == to after clamping, drop the move.
 */
internal fun clampMoveAwayFromActive(
  currentIndex: Int,
  from: Int,
  to: Int,
): Pair<Int, Int>? {
  if (currentIndex < 0) return from to to
  if (from == currentIndex) return null
  val clampedTo = if (to == currentIndex) {
    if (from < currentIndex) currentIndex - 1 else currentIndex + 1
  } else to
  if (from == clampedTo) return null
  return from to clampedTo
}

/**
 * D.21.3 — diff two adjacent reorder snapshots.
 *
 * The drag helper produces lists that differ by exactly one
 * `removeAt(from)` + `add(to, moved)` — i.e. all items except one
 * shift in lockstep, and one item jumps to a new index. Returns the
 * `(from, to)` *visual* positions, which callers translate into
 * controller-queue indices via [translateVisualToReal].
 */
internal fun <T> firstDifference(before: List<T>, after: List<T>): Pair<Int, Int>? {
  if (before.size != after.size) return null
  val firstDiff = before.indices.firstOrNull { before[it] != after[it] } ?: return null

  // Hypothesis A: `before[firstDiff]` was moved down.
  val movedDown = before[firstDiff]
  val downTo = after.indexOf(movedDown)
  if (downTo > firstDiff) {
    val rebuilt = before.toMutableList().apply {
      removeAt(firstDiff)
      add(downTo, movedDown)
    }
    if (rebuilt == after) return firstDiff to downTo
  }

  // Hypothesis B: `after[firstDiff]` was moved up (came from a later
  // position in `before`).
  val movedUp = after[firstDiff]
  val upFrom = before.indexOf(movedUp)
  if (upFrom > firstDiff) {
    val rebuilt = before.toMutableList().apply {
      removeAt(upFrom)
      add(firstDiff, movedUp)
    }
    if (rebuilt == after) return upFrom to firstDiff
  }

  return null
}
