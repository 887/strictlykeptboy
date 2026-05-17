package com.eight87.strictlykeptboy.ui.import_export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.port.ics.IcsParseReport

/**
 * Phase P.3 — parse-preview confirmation. Shown after the user picks a
 * `.ics` file but before any commit happens. Per R.X.7 / ISP: takes
 * only the parse report and confirm/cancel callbacks; no god-state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewSheet(
    preview: IcsParseReport,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        modifier = Modifier.testTag(TestTagImportPreviewSheet),
    ) {
        ImportPreviewContent(preview = preview, onConfirm = onConfirm, onCancel = onCancel)
    }
}

/**
 * Phase 2.1.H.4 — inline preview content, hoisted out of the bottom-sheet
 * shell so the tablet master-detail layout can render the same preview
 * inside the right pane without ModalBottomSheet's scrim taking over.
 */
@Composable
fun ImportPreviewContent(
    preview: IcsParseReport,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Text(
                text = stringResource(R.string.import_preview_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(
                    R.string.import_preview_counts,
                    preview.events.size,
                    preview.rules.size,
                    preview.exceptions.size,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (preview.warnings.isNotEmpty()) {
                Text(
                    text = pluralStringResource(R.plurals.import_preview_warnings, preview.warnings.size, preview.warnings.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = androidx.compose.ui.Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag(TestTagImportPreviewCancel),
                ) { Text(stringResource(R.string.import_preview_cancel)) }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag(TestTagImportPreviewConfirm),
                    enabled = preview.totalEntities > 0,
                ) { Text(stringResource(R.string.import_preview_confirm)) }
            }
            Spacer(Modifier.height(8.dp))
        }
}
