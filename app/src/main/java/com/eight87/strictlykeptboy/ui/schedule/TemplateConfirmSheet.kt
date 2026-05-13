package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.AtomicTemplateSubbeat
import com.eight87.strictlykeptboy.store.TemplateEntry

const val TestTagTemplateConfirm = "TemplateConfirmSheet"
const val TestTagTemplateConfirmConfirmBtn = "TemplateConfirmBtn"
const val TestTagTemplateConfirmCancelBtn = "TemplateConfirmCancelBtn"
const val TestTagTemplateConfirmSubbeat = "TemplateConfirmSubbeat"

/**
 * Phase FFF / EC-C.6 — confirmation sheet for a picked template.
 *
 * Shows the template's title, start time, and a read-only preview of
 * the sub-beats. Confirm invokes [onConfirm] which materializes the
 * template via the controller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateConfirmSheet(
    entry: TemplateEntry,
    previewSubbeats: List<AtomicTemplateSubbeat>,
    startIso: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagTemplateConfirm),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = startIso,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            if (previewSubbeats.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.event_create_template_subbeats_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                previewSubbeats.forEachIndexed { i, sb ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("$TestTagTemplateConfirmSubbeat-$i"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sb.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${sb.durationSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag(TestTagTemplateConfirmCancelBtn),
                ) { Text(stringResource(R.string.event_create_template_cancel)) }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag(TestTagTemplateConfirmConfirmBtn),
                ) { Text(stringResource(R.string.event_create_template_confirm)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
