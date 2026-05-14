package com.eight87.strictlykeptboy.task

/**
 * Round 2.16.A — task-domain facade replacing tonearmboy's
 * `PlaybackUiState`. Shape-compatible per locked-decision D-2.16.b,
 * with music-domain fields renamed:
 *
 *   title          → taskName
 *   artist         → subStepName
 *   album          → (dropped, unused)
 *   mediaStoreAlbumId → (dropped)
 *   positionMs     → subStepElapsedMs
 *   durationMs     → subStepDurationMs
 *
 * Added: `subStepIndex`, `subStepCount`, `taskElapsedMs`,
 * `taskDurationMs` for the two-bar progress idiom (D-2.16.c).
 *
 * Phase A holds a single hardcoded value via
 * [StubTaskPlaybackSource]; Phase B replaces with a real reactive
 * projector.
 */
data class TaskPlaybackState(
  val hasMedia: Boolean,
  val taskName: String,
  val subStepName: String,
  val isPlaying: Boolean,
  val subStepElapsedMs: Long,
  val subStepDurationMs: Long,
  val subStepIndex: Int,
  val subStepCount: Int,
  val taskElapsedMs: Long,
  val taskDurationMs: Long,
  val hasNext: Boolean,
  val hasPrevious: Boolean,
  val shuffleEnabled: Boolean = false,
  val repeatMode: RepeatMode = RepeatMode.OFF,
  val connectionPhase: ConnectionPhase = ConnectionPhase.Connecting,
) {
  companion object {
    val Empty = TaskPlaybackState(
      hasMedia = false,
      taskName = "",
      subStepName = "",
      isPlaying = false,
      subStepElapsedMs = 0L,
      subStepDurationMs = 0L,
      subStepIndex = 0,
      subStepCount = 0,
      taskElapsedMs = 0L,
      taskDurationMs = 0L,
      hasNext = false,
      hasPrevious = false,
      shuffleEnabled = false,
      repeatMode = RepeatMode.OFF,
      connectionPhase = ConnectionPhase.Connecting,
    )
  }
}

/**
 * Local replacement for `androidx.media3.common.Player`'s integer
 * repeat-mode constants. Three values mirror Player.REPEAT_MODE_*.
 */
enum class RepeatMode { OFF, ALL, ONE }

/** Mirror of tonearmboy's `ConnectionPhase` so the ported sub-state
 *  resolver compiles unchanged. */
enum class ConnectionPhase { Connecting, Connected }
