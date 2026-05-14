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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
  nowPlayingState: TaskNowPlayingState,
  transport: TaskTransportCommands,
  queueCommands: TaskQueueCommands,
  onBack: () -> Unit,
  nowPlayingListState: LazyListState? = null,
) {
  val state by nowPlayingState.state.collectAsStateWithLifecycle()
  val queueSnapshot by nowPlayingState.queue.collectAsStateWithLifecycle()
  val fallbackListState = rememberLazyListState()
  val listState = nowPlayingListState ?: fallbackListState

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.playing_top_bar_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.playing_cd_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    when (resolveSubState(state)) {
      NowPlayingSubState.Connecting -> NowPlayingConnecting(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(24.dp),
      )
      NowPlayingSubState.ConnectedEmpty -> NowPlayingEmpty(
        onBack = onBack,
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
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
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .semantics { testTag = "now_playing_screen" },
        )
      }
    }
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
      item(key = "now_playing_card") {
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
          Text(
            text = state.taskName.ifEmpty { noTrackPlaceholder },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            modifier = Modifier.semantics { testTag = "now_playing_title" },
          )
          Text(
            text = state.subStepName.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
          )
          Scrubber(
            positionMs = state.subStepElapsedMs,
            durationMs = state.subStepDurationMs,
            onSeek = onSeek,
          )
        }
      }

      item(key = "transport_row") {
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

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
  val total = durationMs.coerceAtLeast(0L)
  val pos = positionMs.coerceIn(0L, total.coerceAtLeast(positionMs))
  var dragValue by remember(positionMs) { mutableStateOf<Float?>(null) }
  val sliderValue = dragValue ?: pos.toFloat()
  val sliderMax = total.toFloat().coerceAtLeast(1f)

  Column {
    Slider(
      value = sliderValue.coerceIn(0f, sliderMax),
      onValueChange = { dragValue = it },
      onValueChangeFinished = {
        dragValue?.let { onSeek(it.toLong()) }
        dragValue = null
      },
      valueRange = 0f..sliderMax,
      modifier = Modifier.fillMaxWidth().semantics { testTag = "now_playing_scrubber" },
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(formatMillis(sliderValue.toLong()), style = MaterialTheme.typography.labelMedium)
      Text(formatMillis(total), style = MaterialTheme.typography.labelMedium)
    }
  }
}

private fun formatMillis(ms: Long): String {
  if (ms <= 0) return "0:00"
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
