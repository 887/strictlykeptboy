package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.sync.ParentReconciler

const val TestTagAdoptSheet = "AdoptFolderSheet"
const val TestTagAdoptSheetConfirm = "AdoptFolderSheet-Confirm"
const val TestTagAdoptSheetCancel = "AdoptFolderSheet-Cancel"

/**
 * Round 2.17 Phase E.4 — confirmation sheet shown after the user picks
 * a folder via "Adopt existing folder". Runs the supplied [reconcile]
 * suspending probe against the picked folder and lists discoveries; the
 * user confirms (parent is switched + repos registered) or cancels.
 *
 * Marker absence DISABLES the confirm button per the plan — adoption
 * requires the picked folder (or its `strictlykeptboy/` subfolder) to
 * have been a strictlykeptboy parent at some point.
 */
@Composable
fun AdoptFolderSheet(
    request: MainActivity.AdoptSheetState,
    reconcile: suspend () -> List<ParentReconciler.AdoptedRepo>,
    onCancel: () -> Unit,
    onConfirm: (List<ParentReconciler.AdoptedRepo>) -> Unit,
) {
    var discoveries by remember { mutableStateOf<List<ParentReconciler.AdoptedRepo>?>(null) }
    LaunchedEffect(request.parentDir.absolutePath) {
        discoveries = runCatching { reconcile() }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(TestTagAdoptSheet),
        title = { Text(stringResource(R.string.adopt_sheet_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    request.parentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!request.hasMarker) {
                    Text(
                        stringResource(R.string.adopt_sheet_no_marker),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val list = discoveries
                when {
                    list == null -> Text("…")
                    list.isEmpty() -> Text(stringResource(R.string.adopt_sheet_empty))
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        list.forEach { ad ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(ad.repoId, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val list = discoveries.orEmpty()
            val canConfirm = request.hasMarker && list.isNotEmpty()
            Button(
                onClick = { onConfirm(list) },
                enabled = canConfirm,
                modifier = Modifier.testTag(TestTagAdoptSheetConfirm),
            ) {
                Text(pluralStringResource(R.plurals.adopt_sheet_confirm, list.size, list.size))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(TestTagAdoptSheetCancel),
            ) { Text(stringResource(R.string.adopt_sheet_cancel)) }
        },
    )
}
