package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.task.TaskPlaybackState

/**
 * Round 2.16.A — verbatim port from tonearmboy's `MiniPlayer.kt`.
 *
 * Diffs from source:
 *  - `PlaybackUiState` → `TaskPlaybackState`
 *  - `state.title` / `state.artist` / `state.album` →
 *    `state.taskName` / `state.subStepName` (album dropped)
 *  - CoverArt(albumId=...) → Material `Icons.Filled.Task` at the same
 *    48 dp size
 *  - `AlbumCoversMode` parameter dropped
 *  - position/duration → subStepElapsedMs / subStepDurationMs
 *
 * Everything else (peek-layout, info row + transport row + 2-dp progress
 * line, vertical drag forwarder, testTags, alpha/draw idioms) is
 * unchanged.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
  state: TaskPlaybackState,
  onTogglePlayPause: () -> Unit,
  onClose: () -> Unit,
  onExpand: () -> Unit,
  onSkipNext: () -> Unit = {},
  onSkipPrevious: () -> Unit = {},
  onPlayButtonLongPress: () -> Unit = {},
  onToggleShuffle: () -> Unit = {},
  onCycleRepeat: () -> Unit = {},
  onSeekTo: (Long) -> Unit = {},
  onSheetDragDelta: (Float) -> Unit = {},
  onSheetDragSettle: () -> Unit = {},
) {
  if (!state.hasMedia) return
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onExpand)
        .pointerInput(Unit) {
          detectVerticalDragGestures(
            onDragEnd = onSheetDragSettle,
            onDragCancel = onSheetDragSettle,
          ) { _, delta -> onSheetDragDelta(delta) }
        }
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .semantics { testTag = "mini_player" },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // CoverArt drop-in replacement (Round 2.16.A): Material Task icon
      // at the same 48 dp slot tonearmboy reserved for the album thumb.
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .semantics { testTag = "mini_player_cover" },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Task,
          contentDescription = null,
          modifier = Modifier.size(28.dp),
        )
      }
      val unknownTitle = stringResource(R.string.playing_unknown)
      val unknownArtist = stringResource(R.string.playing_mini_player_unknown_artist)
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = state.taskName.ifEmpty { unknownTitle },
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          modifier = Modifier.semantics { testTag = "mini_player_title" },
        )
        val subtitle = state.subStepName.ifBlank { unknownArtist }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.semantics { testTag = "mini_player_subtitle" },
        )
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.semantics { testTag = "mini_player_close" },
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.playing_cd_stop))
      }
    }

    PlaybackTransportRow(
      state = state,
      iconSize = 24.dp,
      playIconSize = 24.dp,
      onTogglePlayPause = onTogglePlayPause,
      onSkipPrevious = onSkipPrevious,
      onSkipNext = onSkipNext,
      onToggleShuffle = onToggleShuffle,
      onCycleRepeat = onCycleRepeat,
      testTagPrefix = "mini_player",
      onPlayLongPress = onPlayButtonLongPress,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .semantics { testTag = "mini_player_transport_row" },
    )

    val total = state.subStepDurationMs.coerceAtLeast(0L)
    val pos = state.subStepElapsedMs.coerceIn(0L, total.coerceAtLeast(state.subStepElapsedMs))
    val progress = if (total > 0L) (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .height(2.dp)
        .semantics { testTag = "mini_player_progress" },
      color = MaterialTheme.colorScheme.primary,
      trackColor = MaterialTheme.colorScheme.surfaceContainer,
      gapSize = 0.dp,
      drawStopIndicator = {},
    )
  }
}
