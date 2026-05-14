package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.task.ConnectionPhase
import com.eight87.strictlykeptboy.task.TaskNowPlayingState
import com.eight87.strictlykeptboy.task.TaskPlaybackState
import com.eight87.strictlykeptboy.task.TaskQueueCommands
import com.eight87.strictlykeptboy.task.TaskQueueSnapshot
import com.eight87.strictlykeptboy.task.TaskTransportCommands
import kotlinx.coroutines.delay

/**
 * Round 2.16.A — verbatim port from tonearmboy's `NowPlayingScreen.kt`.
 *
 * Diffs from source:
 *  - `NowPlayingState` / `TransportCommands` / `QueueCommands` →
 *    `TaskNowPlayingState` / `TaskTransportCommands` / `TaskQueueCommands`
 *  - `PlaybackUiState` → `TaskPlaybackState`; field name renames
 *  - CoverArt → Material `Icons.Filled.Task` at the same 96 dp slot
 *  - AlbumCoversMode parameter dropped
 *  - `onDeleteCurrentTrack` typed-confirm delete dialog dropped (per
 *    Phase A spec — passed null in caller)
 *  - `onSaveQueueAsPlaylist` dropped
 *  - ReplayGain settings bridge bits dropped
 *
 * Round 2.16 follow-up — dropped Scaffold + TopAppBar wrapper. The
 * expanded sheet has no music-chrome (no "Now Playing" title, no back
 * arrow); a bottom-sheet drag-handle pill is rendered at the top
 * instead, and the sheet is dismissed by drag-down or the BackHandler
 * wired by [com.eight87.strictlykeptboy.ui.scaffold.SkbAppShell] which
 * still routes to [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
  nowPlayingState: TaskNowPlayingState,
  transport: TaskTransportCommands,
  queueCommands: TaskQueueCommands,
  onBack: () -> Unit,
  nowPlayingListState: LazyListState? = null,
  /**
   * Round 2.16.D — body slot. Inserted in the merged surface where the
   * music queue used to live. Receives a `LazyItemScope` so the body
   * can use `fillParentMaxHeight` / `fillParentMaxWidth` if it needs
   * to. When null (verbatim-port shape / tests), the legacy
   * `QueueSection` renders.
   */
  bodyContent: (@Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit)? = null,
  /**
   * Round 2.16.D — when false, the hero card (task icon + 3-node info
   * row + 2 progress bars) is hidden, leaving the body content as the
   * primary content. Used by the D.7 entry-point: user reaches the
   * sheet with no active task.
   */
  showHeroCard: Boolean = true,
) {
  val state by nowPlayingState.state.collectAsStateWithLifecycle()
  val queueSnapshot by nowPlayingState.queue.collectAsStateWithLifecycle()
  val fallbackListState = rememberLazyListState()
  val listState = nowPlayingListState ?: fallbackListState

  // Round 2.16.D — when bodyContent is supplied, the screen has a
  // reason to render the merged surface even with no active task
  // (hasMedia=false): the D.7 entry-point opens the sheet to show
  // todo views without a current task. Bypass the auto-pop
  // `ConnectedEmpty` branch in that case.
  val subState = if (bodyContent != null && !state.hasMedia) {
    NowPlayingSubState.ConnectedWithMedia
  } else {
    resolveSubState(state)
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Round 2.16 follow-up — drag-handle pill replaces the
      // Scaffold/TopAppBar back arrow. Sheet is dismissed by drag-down
      // or BackHandler (wired in SkbAppShell to `onBack`), so no
      // explicit back-button affordance is rendered.
      SheetDragHandle()
      when (subState) {
        NowPlayingSubState.Connecting -> NowPlayingConnecting(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        )
        NowPlayingSubState.ConnectedEmpty -> NowPlayingEmpty(
          onBack = onBack,
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        )
        NowPlayingSubState.ConnectedWithMedia -> {
          NowPlayingMergedSurface(
            state = state,
            queueSnapshot = queueSnapshot,
            listState = listState,
            onSeek = transport::seekTo,
            onTogglePlayPause = transport::togglePlayPause,
            onSeekBackward = transport::seekBackward,
            onSeekForward = transport::seekForward,
            onSeekToPrevious = transport::seekToPrevious,
            onSeekToNext = transport::seekToNext,
            onToggleShuffle = transport::toggleShuffle,
            onCycleRepeat = transport::cycleRepeatMode,
            onJumpToQueueIndex = queueCommands::seekToQueueIndex,
            onRemoveQueueItem = queueCommands::removeQueueItem,
            onMoveQueueItem = queueCommands::moveQueueItem,
            bodyContent = bodyContent,
            showHeroCard = showHeroCard && state.hasMedia,
            modifier = Modifier
              .fillMaxSize()
              .semantics { testTag = "now_playing_screen" },
          )
        }
      }
    }
  }
}

/**
 * Round 2.16 follow-up — Material bottom-sheet drag-handle pill. 4-dp tall,
 * 32-dp wide, outline-colored, centered. Replaces the music-chrome
 * TopAppBar back arrow as the visual "this is a drag-up sheet" affordance.
 */
@Composable
private fun SheetDragHandle() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 4.dp),
    horizontalArrangement = Arrangement.Center,
  ) {
    androidx.compose.foundation.layout.Box(
      modifier = Modifier
        .size(width = 32.dp, height = 4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(MaterialTheme.colorScheme.outline)
        .semantics { testTag = "now_playing_drag_handle" },
    )
  }
}

@Composable
internal fun NowPlayingMergedSurface(
  state: TaskPlaybackState,
  queueSnapshot: TaskQueueSnapshot,
  listState: LazyListState,
  onSeek: (Long) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekBackward: () -> Unit,
  onSeekForward: () -> Unit,
  onSeekToPrevious: () -> Unit,
  onSeekToNext: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onJumpToQueueIndex: (Int) -> Unit,
  onRemoveQueueItem: (Int) -> Unit,
  onMoveQueueItem: (Int, Int) -> Unit,
  modifier: Modifier = Modifier,
  bodyContent: (@Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit)? = null,
  showHeroCard: Boolean = true,
) {
  var isQueueDragging by remember { mutableStateOf(false) }
  DisposableEffect(Unit) {
    onDispose { isQueueDragging = false }
  }
  val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
  val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
  androidx.compose.foundation.layout.BoxWithConstraints(
    modifier = modifier.pointerInput(Unit) {
      detectTapGestures(onTap = {
        focusManager.clearFocus()
        keyboard?.hide()
      })
    },
  ) {
    val viewport = maxHeight
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(
        horizontal = 24.dp,
        vertical = 16.dp,
      ),
      userScrollEnabled = !isQueueDragging,
    ) {
      if (showHeroCard) item(key = "now_playing_card") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "now_playing_card" },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // CoverArt drop-in replacement: Material Task icon at the same
          // 96 dp aspect-1 slot tonearmboy's hero cover used.
          androidx.compose.foundation.layout.Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .semantics { testTag = "now_playing_cover" },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Task,
              contentDescription = state.taskName.ifEmpty { null },
              modifier = Modifier.size(96.dp),
            )
          }
          val noTrackPlaceholder = stringResource(R.string.playing_no_track)
          // Round 2.16.C — three-node info row at expanded size per
          // D-2.16.d, mirroring MiniPlayer's pattern.
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = state.taskName.ifEmpty { noTrackPlaceholder },
                  style = MaterialTheme.typography.headlineSmall,
                  maxLines = 2,
                  modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { testTag = "now_playing_title" },
                )
                if (state.subStepCount > 1) {
                  Spacer(modifier = Modifier.size(12.dp))
                  com.eight87.strictlykeptboy.ui.playing.StepCountPill(
                    index = state.subStepIndex,
                    total = state.subStepCount,
                  )
                }
              }
              Text(
                text = if (state.subStepCount > 1) state.subStepName else "",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.semantics { testTag = "now_playing_substep" },
              )
            }
            Spacer(modifier = Modifier.size(12.dp))
            val remainingMs =
              (state.subStepDurationMs - state.subStepElapsedMs).coerceAtLeast(0L)
            Text(
              text = formatMmSs(remainingMs),
              style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
              ),
              maxLines = 1,
              modifier = Modifier.semantics { testTag = "now_playing_countdown" },
            )
          }
          // Round 2.16.C — sub-step progress as a non-draggable wide bar
          // with darker/brighter split (Slider replaced; seeking has no
          // meaning for tasks). Followed by elapsed / total mm:ss labels.
          com.eight87.strictlykeptboy.ui.playing.SubStepProgressBar(
            elapsedMs = state.subStepElapsedMs,
            durationMs = state.subStepDurationMs,
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .semantics { testTag = "now_playing_substep_progress" },
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = formatMmSs(state.subStepElapsedMs),
              style = MaterialTheme.typography.labelMedium,
            )
            Text(
              text = formatMmSs(state.subStepDurationMs),
              style = MaterialTheme.typography.labelMedium,
            )
          }
          // Round 2.16.C — whole-task progress row: thin 2-dp bar +
          // "task: mm:ss / mm:ss" label (monospace).
          com.eight87.strictlykeptboy.ui.playing.TaskProgressBar(
            elapsedMs = state.taskElapsedMs,
            durationMs = state.taskDurationMs,
            modifier = Modifier
              .fillMaxWidth()
              .height(2.dp)
              .semantics { testTag = "now_playing_task_progress" },
          )
          Text(
            text = "task: ${formatMmSs(state.taskElapsedMs)} / ${formatMmSs(state.taskDurationMs)}",
            style = MaterialTheme.typography.labelMedium.copy(
              fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (showHeroCard) item(key = "transport_row") {
        PlaybackTransportRow(
          state = state,
          iconSize = 36.dp,
          playIconSize = 56.dp,
          onTogglePlayPause = onTogglePlayPause,
          onSkipPrevious = onSeekToPrevious,
          onSkipNext = onSeekToNext,
          onToggleShuffle = onToggleShuffle,
          onCycleRepeat = onCycleRepeat,
          testTagPrefix = "now_playing",
          showShuffleAndRepeat = false,
          modifier = Modifier.semantics { testTag = "now_playing_transport_row" },
          extraStart = {
            IconButton(onClick = onSeekBackward) {
              Icon(
                Icons.Filled.Replay10,
                contentDescription = stringResource(R.string.playing_cd_seek_back_10),
                modifier = Modifier.size(36.dp),
              )
            }
          },
          extraEnd = {
            IconButton(onClick = onSeekForward) {
              Icon(
                Icons.Filled.Forward10,
                contentDescription = stringResource(R.string.playing_cd_seek_forward_10),
                modifier = Modifier.size(36.dp),
              )
            }
          },
        )
      }

      item(key = "queue_section") {
        if (bodyContent != null) {
          bodyContent()
        } else {
          QueueSection(
            snapshot = queueSnapshot,
            onJumpTo = onJumpToQueueIndex,
            onRemove = onRemoveQueueItem,
            onMove = onMoveQueueItem,
            noMatchFillModifier = Modifier.fillParentMaxHeight(),
            parentViewportHeight = viewport,
            onDragStateChange = { isQueueDragging = it },
          )
        }
      }
    }
    com.eight87.strictlykeptboy.ui.common.FastScrollbar(
      state = listState,
      modifier = Modifier.align(Alignment.CenterEnd),
    )
  }
}

internal const val QUEUE_LIST_INDEX: Int = 2

internal enum class NowPlayingSubState {
  Connecting,
  ConnectedEmpty,
  ConnectedWithMedia,
}

internal fun resolveSubState(state: TaskPlaybackState): NowPlayingSubState = when {
  state.connectionPhase == ConnectionPhase.Connecting && !state.hasMedia ->
    NowPlayingSubState.Connecting
  !state.hasMedia -> NowPlayingSubState.ConnectedEmpty
  else -> NowPlayingSubState.ConnectedWithMedia
}

@Composable
private fun NowPlayingConnecting(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.semantics { testTag = "now_playing_connecting" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    CircularProgressIndicator()
    Text(
      text = stringResource(R.string.playing_connecting),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun NowPlayingEmpty(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(Unit) {
    delay(EmptyAutoPopMs)
    onBack()
  }
  Column(
    modifier = modifier.semantics { testTag = "now_playing_empty" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = stringResource(R.string.playing_empty_title),
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      text = stringResource(R.string.playing_empty_message),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
      onClick = onBack,
      modifier = Modifier.semantics { testTag = "now_playing_empty_back" },
    ) { Text(stringResource(R.string.playing_empty_back_button)) }
  }
}

private const val EmptyAutoPopMs: Long = 300L

// Round 2.16.C — Scrubber + formatMillis removed; sub-step progress is
// rendered via the shared `SubStepProgressBar`, and time labels use
// `formatMmSs` from MiniPlayer.kt. Seeking has no meaning for tasks.
