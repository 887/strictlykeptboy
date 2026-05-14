package com.eight87.strictlykeptboy.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.task.TaskQueueItem
import com.eight87.strictlykeptboy.task.TaskQueueSnapshot
import com.eight87.strictlykeptboy.ui.common.DragReorderColumn

/**
 * Round 2.16.A — verbatim port from tonearmboy's `QueueSection.kt`.
 *
 * Diffs from source:
 *  - `QueueItem` / `QueueSnapshot` → `TaskQueueItem` / `TaskQueueSnapshot`
 *  - `.mediaId` / `.title` / `.artist` → `.taskId` / `.taskName` / `.subStepName`
 *
 * Behaviour (drag-reorder, filter, no-match placeholder, active-row
 * highlight, deferred row mount, remove-confirm dialog) is unchanged.
 */
internal data class QueueEntry(val realIndex: Int, val item: TaskQueueItem, val isActive: Boolean)

@Composable
fun QueueSection(
  snapshot: TaskQueueSnapshot,
  onJumpTo: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onMove: (from: Int, to: Int) -> Unit,
  modifier: Modifier = Modifier,
  noMatchFillModifier: Modifier = Modifier,
  parentViewportHeight: androidx.compose.ui.unit.Dp = 0.dp,
  onDragStateChange: ((Boolean) -> Unit)? = null,
) {
  var filter by remember { mutableStateOf("") }
  val filterActive = filter.isNotBlank()

  val items = snapshot.items
  val currentIndex = snapshot.currentIndex

  val allEntries: List<QueueEntry> = remember(items, currentIndex) {
    items.mapIndexed { i, qi ->
      QueueEntry(realIndex = i, item = qi, isActive = i == currentIndex)
    }
  }

  val needle = filter.trim().lowercase()
  val visibleEntries: List<QueueEntry> = remember(allEntries, needle, filterActive) {
    if (filterActive) {
      allEntries.filter {
        it.item.taskName.lowercase().contains(needle) ||
          it.item.subStepName.lowercase().contains(needle)
      }
    } else allEntries
  }

  var rowsMounted by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    withFrameNanos { /* yield one frame */ }
    rowsMounted = true
  }

  val byRows = (allEntries.size * QUEUE_ROW_HEIGHT_DP).dp
  val outerMin = if (parentViewportHeight > byRows) parentViewportHeight else byRows
  Column(
    modifier = modifier
      .fillMaxWidth()
      .imePadding()
      .heightIn(min = outerMin)
      .semantics { testTag = "queue_section" },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    HorizontalDivider()
    Text(
      text = stringResource(R.string.playing_queue_header),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.semantics { testTag = "queue_up_next_header" },
    )

    OutlinedTextField(
      value = filter,
      onValueChange = { filter = it },
      singleLine = true,
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      trailingIcon = {
        if (filterActive) {
          IconButton(
            onClick = { filter = "" },
            modifier = Modifier.semantics { testTag = "queue_filter_clear" },
          ) {
            Icon(
              Icons.Filled.Close,
              contentDescription = stringResource(R.string.playing_cd_queue_filter_clear),
            )
          }
        }
      },
      placeholder = { Text(stringResource(R.string.playing_queue_filter_placeholder)) },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "queue_filter_field" },
    )

    if (allEntries.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.playing_queue_empty),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    } else if (!rowsMounted) {
      Spacer(
        modifier = Modifier
          .fillMaxWidth()
          .height((allEntries.size * QUEUE_ROW_HEIGHT_DP).dp)
          .semantics { testTag = "queue_rows_pending" },
      )
    } else if (visibleEntries.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = (allEntries.size * QUEUE_ROW_HEIGHT_DP).dp)
          .padding(24.dp)
          .semantics { testTag = "queue_no_match_placeholder" },
        contentAlignment = Alignment.TopCenter,
      ) {
        Text(
          text = stringResource(R.string.playing_queue_no_match),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    } else if (filterActive) {
      Column(modifier = Modifier.fillMaxWidth()) {
        visibleEntries.forEach { e ->
          QueueRow(
            item = e.item,
            isActive = e.isActive,
            dragHandleModifier = Modifier,
            dragHandleEnabled = false,
            onJumpTo = { onJumpTo(e.realIndex) },
            onRemove = { onRemove(e.realIndex) },
          )
          HorizontalDivider()
        }
      }
    } else {
      DragReorderColumn(
        items = allEntries,
        itemKey = { "queue_${it.realIndex}_${it.item.taskId}" },
        rowHeightDp = QUEUE_ROW_HEIGHT_DP,
        testTagPrefix = "queue",
        onReordered = { reordered ->
          val before = allEntries
          val diff = firstDifference(before, reordered) ?: return@DragReorderColumn
          val (fromVisual, toVisual) = diff
          val clamped = clampMoveAwayFromActive(currentIndex, fromVisual, toVisual)
            ?: return@DragReorderColumn
          val (from, to) = clamped
          onMove(from, to)
        },
        onDragStateChange = onDragStateChange,
      ) { entry, handleModifier ->
        QueueRow(
          item = entry.item,
          isActive = entry.isActive,
          dragHandleModifier = handleModifier,
          dragHandleEnabled = !entry.isActive,
          onJumpTo = { onJumpTo(entry.realIndex) },
          onRemove = { onRemove(entry.realIndex) },
        )
      }
    }
  }
}

@Composable
internal fun QueueRow(
  item: TaskQueueItem,
  isActive: Boolean,
  dragHandleModifier: Modifier,
  dragHandleEnabled: Boolean,
  onJumpTo: () -> Unit,
  onRemove: () -> Unit,
) {
  val rowBackground = if (isActive) {
    QueueActivePurple
  } else {
    MaterialTheme.colorScheme.surface
  }
  val rowTag = if (isActive) "queue_row_active" else "queue_row"
  var showConfirm by remember { mutableStateOf(false) }
  val unknownLabel = stringResource(R.string.playing_unknown)
  val title = item.taskName.ifEmpty { unknownLabel }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onJumpTo)
      .background(rowBackground)
      .padding(horizontal = 4.dp, vertical = 8.dp)
      .semantics { testTag = rowTag },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = { showConfirm = true },
      modifier = Modifier
        .padding(horizontal = 4.dp)
        .semantics { testTag = "queue_remove" },
    ) {
      Icon(
        Icons.Filled.Close,
        contentDescription = stringResource(R.string.playing_cd_queue_remove),
      )
    }
    if (isActive) {
      Icon(
        imageVector = Icons.Filled.GraphicEq,
        contentDescription = stringResource(R.string.playing_cd_queue_now_playing),
        modifier = Modifier
          .size(20.dp)
          .padding(end = 4.dp)
          .semantics { testTag = "queue_active_indicator" },
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = if (isActive) {
          MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        } else {
          MaterialTheme.typography.bodyMedium
        },
        maxLines = 1,
      )
      Text(
        text = item.subStepName.ifEmpty { unknownLabel },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
    Box(
      modifier = Modifier
        .size(40.dp)
        .alpha(if (dragHandleEnabled) 1f else 0.3f)
        .then(if (dragHandleEnabled) dragHandleModifier else Modifier)
        .semantics {
          testTag = if (dragHandleEnabled) "queue_drag_handle" else "queue_drag_handle_disabled"
        },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.DragHandle,
        contentDescription = stringResource(R.string.playing_cd_queue_reorder),
      )
    }
  }
  if (showConfirm) {
    AlertDialog(
      onDismissRequest = { showConfirm = false },
      title = {
        Text(
          text = stringResource(R.string.playing_queue_remove_dialog_title),
          modifier = Modifier.semantics { testTag = "queue_remove_confirm_dialog" },
        )
      },
      text = { Text(text = stringResource(R.string.playing_queue_remove_dialog_text, title)) },
      confirmButton = {
        TextButton(
          onClick = {
            showConfirm = false
            onRemove()
          },
          modifier = Modifier.semantics { testTag = "queue_remove_confirm_button" },
        ) { Text(stringResource(R.string.playing_queue_remove_confirm)) }
      },
      dismissButton = {
        TextButton(
          onClick = { showConfirm = false },
          modifier = Modifier.semantics { testTag = "queue_remove_cancel_button" },
        ) { Text(stringResource(R.string.playing_queue_remove_cancel)) }
      },
    )
  }
}

private const val QUEUE_ROW_HEIGHT_DP = 56

private val QueueActivePurple = Color(0xFF4A4458)
