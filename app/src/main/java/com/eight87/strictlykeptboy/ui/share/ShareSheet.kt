package com.eight87.strictlykeptboy.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig

const val TestTagShareSheet = "ShareSheet"
const val TestTagShareModeReadOnly = "Share-Mode-ReadOnly"
const val TestTagShareModeReadWrite = "Share-Mode-ReadWrite"
const val TestTagShareExpiryNone = "Share-Expiry-None"
const val TestTagShareExpiry7 = "Share-Expiry-7d"
const val TestTagShareExpiry30 = "Share-Expiry-30d"
const val TestTagShareIncludeMirrors = "Share-IncludeMirrors"
const val TestTagShareAllowWriteBack = "Share-AllowWriteBack"
const val TestTagShareSingleUse = "Share-SingleUse"
const val TestTagShareLinkField = "Share-LinkField"
const val TestTagShareCopy = "Share-Copy"
const val TestTagShareSend = "Share-Send"
const val TestTagShareQrPreview = "Share-QrPreview"
const val TestTagShareSingleUseNote = "Share-SingleUseNote"

/**
 * Phase O.1 — share-this-repo bottom sheet.
 *
 * Stateless wrt persistence. [onCopy] / [onSend] receive the generated link
 * string; caller wires clipboard + ACTION_SEND chooser (lives in MainActivity
 * per R.X.3 — only composition root touches platform handles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    repo: RepoConfig,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagShareSheet),
    ) {
        ShareSheetContent(repo = repo, onCopy = onCopy, onSend = onSend)
    }
}

@Composable
internal fun ShareSheetContent(
    repo: RepoConfig,
    onCopy: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    var mode by remember { mutableStateOf(ShareMode.ReadOnly) }
    var expiry by remember { mutableStateOf<ShareLinkGenerator.Expiry>(ShareLinkGenerator.Expiry.None) }
    var includeMirrors by remember { mutableStateOf(false) }
    var allowWriteBack by remember { mutableStateOf(false) }
    var singleUse by remember { mutableStateOf(false) }

    val link = ShareLinkGenerator.buildUri(
        repo = repo,
        policy = ShareLinkGenerator.SharePolicy(
            mode = mode,
            expiry = expiry,
            includeMirrorRemotes = includeMirrors,
            allowWriteBack = allowWriteBack,
            singleUseToken = singleUse,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.share_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            stringResource(R.string.share_dialog_permissions_label),
            style = MaterialTheme.typography.titleSmall,
        )
        ModeRow(
            label = stringResource(R.string.share_mode_read_only),
            selected = mode == ShareMode.ReadOnly,
            tag = TestTagShareModeReadOnly,
            onSelect = { mode = ShareMode.ReadOnly },
        )
        ModeRow(
            label = stringResource(R.string.share_mode_read_write),
            selected = mode == ShareMode.ReadWrite,
            tag = TestTagShareModeReadWrite,
            onSelect = { mode = ShareMode.ReadWrite },
        )

        Text(
            stringResource(R.string.share_dialog_expiry_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpiryChip(
                label = stringResource(R.string.share_expiry_none),
                selected = expiry == ShareLinkGenerator.Expiry.None,
                tag = TestTagShareExpiryNone,
                onClick = { expiry = ShareLinkGenerator.Expiry.None },
            )
            ExpiryChip(
                label = stringResource(R.string.share_expiry_7d),
                selected = (expiry as? ShareLinkGenerator.Expiry.Days)?.n == 7,
                tag = TestTagShareExpiry7,
                onClick = { expiry = ShareLinkGenerator.Expiry.Days(7) },
            )
            ExpiryChip(
                label = stringResource(R.string.share_expiry_30d),
                selected = (expiry as? ShareLinkGenerator.Expiry.Days)?.n == 30,
                tag = TestTagShareExpiry30,
                onClick = { expiry = ShareLinkGenerator.Expiry.Days(30) },
            )
        }

        if (repo.remotes.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.share_include_mirror_remotes),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = includeMirrors,
                    onCheckedChange = { includeMirrors = it },
                    modifier = Modifier.testTag(TestTagShareIncludeMirrors),
                )
            }
        }

        OutlinedTextField(
            value = TextFieldValue(link),
            onValueChange = { /* read-only display */ },
            readOnly = true,
            label = { Text(stringResource(R.string.share_link_field_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagShareLinkField),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onCopy(link) },
                modifier = Modifier.testTag(TestTagShareCopy),
            ) { Text(stringResource(R.string.share_copy)) }
            TextButton(
                onClick = { onSend(link) },
                modifier = Modifier.testTag(TestTagShareSend),
            ) { Text(stringResource(R.string.share_send)) }
        }

        // Phase RR.1 — write-back toggle (sender authorises feedback writes).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.share_allow_write_back),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Checkbox(
                checked = allowWriteBack,
                onCheckedChange = { allowWriteBack = it },
                modifier = Modifier.testTag(TestTagShareAllowWriteBack),
            )
        }
        // Phase RR.5 — single-use toggle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.share_single_use_token),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Checkbox(
                checked = singleUse,
                onCheckedChange = { singleUse = it },
                modifier = Modifier.testTag(TestTagShareSingleUse),
            )
        }
        if (singleUse) {
            Text(
                stringResource(R.string.share_link_copied_single_use),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(TestTagShareSingleUseNote),
            )
        }

        // Phase RR.2 — QR preview of the link (Apache-2.0 ZXing).
        SharedRepoQrPreview(
            content = link,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagShareQrPreview),
        )
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .testTag(tag),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ExpiryChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected)
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        else AssistChipDefaults.assistChipColors(),
        modifier = Modifier.testTag(tag),
    )
}
