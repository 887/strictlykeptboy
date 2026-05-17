package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.PromptKind

const val TestTagPromptResponseSheet = "PromptResponseSheet"
const val TestTagPromptResponseBody = "PromptResponseBody"
const val TestTagPromptResponseAttachment = "PromptResponseAttachment"
const val TestTagPromptResponseSend = "PromptResponseSend"
const val TestTagPromptResponseCancel = "PromptResponseCancel"

/**
 * Round 2.27 / Phase D.1 — bottom-sheet UI for responding to a
 * keeper-prompt task. Inputs are pure-Compose; the host (TasksPane /
 * MainActivity) is responsible for translating [onSubmit] into a call
 * to [com.eight87.strictlykeptboy.store.PromptResponseWriter.write] on
 * the right repo root.
 *
 * Send is disabled until at least one of `body` / `attachment` is
 * non-blank — empty submissions are treated as "mark answered offline"
 * (handled separately via long-press, not via this sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptResponseSheet(
    item: TaskItem,
    onDismiss: () -> Unit,
    onSubmit: (body: String, attachment: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var body by rememberSaveable(item.id) { mutableStateOf("") }
    var attachment by rememberSaveable(item.id) { mutableStateOf("") }

    val glyph = when (item.promptKind) {
        PromptKind.Photo -> "📸"
        PromptKind.Text -> "💬"
        PromptKind.CheckIn, null -> "🔒"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagPromptResponseSheet),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$glyph  ${item.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.task_prompt_sheet_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.task_prompt_sheet_reply_label)) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagPromptResponseBody),
            )
            OutlinedTextField(
                value = attachment,
                onValueChange = { attachment = it },
                label = { Text(stringResource(R.string.task_prompt_sheet_attachment_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagPromptResponseAttachment),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(TestTagPromptResponseCancel),
                ) {
                    Text(stringResource(R.string.task_prompt_sheet_cancel))
                }
                Spacer(Modifier.height(0.dp))
                Button(
                    onClick = {
                        val attach = attachment.trim().ifBlank { null }
                        onSubmit(body.trim(), attach)
                    },
                    enabled = body.isNotBlank() || attachment.isNotBlank(),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag(TestTagPromptResponseSend),
                ) {
                    Text(stringResource(R.string.task_prompt_sheet_send))
                }
            }
        }
    }
}
