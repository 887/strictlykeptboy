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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.task.TaskPlaybackState

/**
 * Round 2.16.C — task-adapted MiniPlayer.
 *
 * Phase-A verbatim port preserved structurally; Phase-C visual mapping:
 *  - Info row = three Compose Text nodes per D-2.16.d:
 *      top line = taskName + small "i/n" step-count pill (Surface),
 *      second line = subStepName (bodySmall, single-line, ellipsize),
 *      trailing = right-aligned monospace countdown mm:ss.
 *  - Progress = two stacked bars per D-2.16.c:
 *      wide (4.dp) sub-step bar with darker/brighter split,
 *      thin (2.dp) whole-task bar pinned flush at bottom (tertiary).
 *  - Shuffle + repeat gated off via PlaybackTransportRow's
 *    showShuffleAndRepeat parameter — they have no task meaning.
 *  - Close (X) button + click-to-expand behavior + testTag "mini_player"
 *    retained.
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
      // CoverArt drop-in replacement: Material Task icon at the 48 dp
      // slot tonearmboy reserved for the album thumb.
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
      // C.1 — info column: top line (title + step pill), second line (sub-step).
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = state.taskName.ifEmpty { unknownTitle },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier
              .weight(1f, fill = false)
              .semantics { testTag = "mini_player_title" },
          )
          if (state.subStepCount > 1) {
            Spacer(modifier = Modifier.width(8.dp))
            StepCountPill(
              index = state.subStepIndex,
              total = state.subStepCount,
            )
          }
        }
        Text(
          text = state.subStepName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.semantics { testTag = "mini_player_subtitle" },
        )
      }
      // C.1 — right-aligned monospace countdown mm:ss
      val remainingMs = (state.subStepDurationMs - state.subStepElapsedMs).coerceAtLeast(0L)
      Text(
        text = formatMmSs(remainingMs),
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        maxLines = 1,
        modifier = Modifier.semantics { testTag = "mini_player_countdown" },
      )
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
      showShuffleAndRepeat = false,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .semantics { testTag = "mini_player_transport_row" },
    )

    // C.2 — wide sub-step bar (4 dp) with darker/brighter split.
    SubStepProgressBar(
      elapsedMs = state.subStepElapsedMs,
      durationMs = state.subStepDurationMs,
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .semantics { testTag = "mini_player_substep_progress" },
    )
    // C.2 — thin 2-dp whole-task bar pinned flush at bottom (tertiary).
    TaskProgressBar(
      elapsedMs = state.taskElapsedMs,
      durationMs = state.taskDurationMs,
      modifier = Modifier
        .fillMaxWidth()
        .height(2.dp)
        .semantics { testTag = "mini_player_task_progress" },
    )
  }
}

@Composable
internal fun StepCountPill(index: Int, total: Int) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
  ) {
    Text(
      text = "$index/$total",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(horizontal = 6.dp, vertical = 2.dp)
        .semantics { testTag = "step_count_pill" },
    )
  }
}

@Composable
internal fun SubStepProgressBar(
  elapsedMs: Long,
  durationMs: Long,
  modifier: Modifier = Modifier,
) {
  val total = durationMs.coerceAtLeast(0L)
  val pos = elapsedMs.coerceIn(0L, total.coerceAtLeast(elapsedMs))
  val progress = if (total > 0L) (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
  Box(
    modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(progress)
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.primary),
    )
  }
}

@Composable
internal fun TaskProgressBar(
  elapsedMs: Long,
  durationMs: Long,
  modifier: Modifier = Modifier,
) {
  val total = durationMs.coerceAtLeast(0L)
  val pos = elapsedMs.coerceIn(0L, total.coerceAtLeast(elapsedMs))
  val progress = if (total > 0L) (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
  Box(
    modifier = modifier.background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(progress)
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.tertiary),
    )
  }
}

internal fun formatMmSs(ms: Long): String {
  val totalSeconds = (ms / 1000).coerceAtLeast(0L)
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
