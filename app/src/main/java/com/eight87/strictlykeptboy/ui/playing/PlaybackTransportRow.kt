package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.task.RepeatMode
import com.eight87.strictlykeptboy.task.TaskPlaybackState

/**
 * Round 2.16.A — verbatim port from tonearmboy of `PlaybackTransportRow`,
 * with the only diffs being:
 *  - state type renamed to `TaskPlaybackState`
 *  - `Player.REPEAT_MODE_*` integer constants → `RepeatMode` enum
 *
 * R.F.3 — shared transport row used by both [MiniPlayer] and
 * [NowPlayingScreen]. Five canonical buttons (shuffle, prev,
 * play-pause, next, repeat) in a `SpaceEvenly` Row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaybackTransportRow(
  state: TaskPlaybackState,
  iconSize: Dp,
  playIconSize: Dp,
  onTogglePlayPause: () -> Unit,
  onSkipPrevious: () -> Unit,
  onSkipNext: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  testTagPrefix: String,
  modifier: Modifier = Modifier,
  onPlayLongPress: (() -> Unit)? = null,
  extraStart: (@Composable () -> Unit)? = null,
  extraEnd: (@Composable () -> Unit)? = null,
  /**
   * Round 2.16.C — gates the shuffle + repeat IconToggleButtons. Tasks
   * have no meaningful shuffle/repeat semantics, so skb call-sites pass
   * `false`. Verbatim-port shape preserved (the calls are additively
   * gated, not deleted).
   */
  showShuffleAndRepeat: Boolean = true,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showShuffleAndRepeat) {
      IconToggleButton(
        checked = state.shuffleEnabled,
        onCheckedChange = { onToggleShuffle() },
        modifier = Modifier.semantics { testTag = "${testTagPrefix}_shuffle" },
      ) {
        Icon(
          imageVector = if (state.shuffleEnabled) Icons.Filled.ShuffleOn else Icons.Filled.Shuffle,
          contentDescription = stringResource(
            if (state.shuffleEnabled) R.string.playing_cd_shuffle_on
            else R.string.playing_cd_shuffle_off,
          ),
          modifier = Modifier.size(iconSize),
        )
      }
    }
    IconButton(
      onClick = onSkipPrevious,
      enabled = state.hasPrevious,
      modifier = Modifier.semantics { testTag = "${testTagPrefix}_prev" },
    ) {
      Icon(
        Icons.Filled.SkipPrevious,
        contentDescription = stringResource(R.string.playing_cd_skip_previous),
        modifier = Modifier.size(iconSize),
      )
    }
    extraStart?.invoke()
    if (onPlayLongPress != null) {
      val interaction = remember { MutableInteractionSource() }
      val playLabel = stringResource(R.string.playing_cd_play)
      val pauseLabel = stringResource(R.string.playing_cd_pause)
      val customActionLabel = stringResource(R.string.playing_cd_custom_action)
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .combinedClickable(
            interactionSource = interaction,
            indication = ripple(bounded = false, radius = 20.dp),
            onClick = onTogglePlayPause,
            onLongClick = onPlayLongPress,
            onClickLabel = if (state.isPlaying) pauseLabel else playLabel,
            onLongClickLabel = customActionLabel,
          )
          .semantics { testTag = "${testTagPrefix}_play_button" },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (state.isPlaying) pauseLabel else playLabel,
          tint = LocalContentColor.current,
        )
      }
    } else {
      val playLabel = stringResource(R.string.playing_cd_play)
      val pauseLabel = stringResource(R.string.playing_cd_pause)
      IconButton(onClick = onTogglePlayPause) {
        Icon(
          imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (state.isPlaying) pauseLabel else playLabel,
          modifier = Modifier.size(playIconSize),
        )
      }
    }
    extraEnd?.invoke()
    IconButton(
      onClick = onSkipNext,
      enabled = state.hasNext,
      modifier = Modifier.semantics { testTag = "${testTagPrefix}_next" },
    ) {
      Icon(
        Icons.Filled.SkipNext,
        contentDescription = stringResource(R.string.playing_cd_skip_next),
        modifier = Modifier.size(iconSize),
      )
    }
    if (showShuffleAndRepeat) {
      IconToggleButton(
        checked = state.repeatMode != RepeatMode.OFF,
        onCheckedChange = { onCycleRepeat() },
        modifier = Modifier.semantics { testTag = "${testTagPrefix}_repeat" },
      ) {
        val (icon, descRes) = when (state.repeatMode) {
          RepeatMode.ONE -> Icons.Filled.RepeatOneOn to R.string.playing_cd_repeat_one
          RepeatMode.ALL -> Icons.Filled.RepeatOn to R.string.playing_cd_repeat_all
          RepeatMode.OFF -> Icons.Filled.Repeat to R.string.playing_cd_repeat_off
        }
        Icon(
          imageVector = icon,
          contentDescription = stringResource(descRes),
          modifier = Modifier.size(iconSize),
        )
      }
    }
  }
}
