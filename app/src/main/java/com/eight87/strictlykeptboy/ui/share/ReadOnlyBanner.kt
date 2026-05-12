package com.eight87.strictlykeptboy.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagReadOnlyBanner = "ReadOnlyBanner"

/**
 * Phase O.3 — banner shown when a remote is treated as read-only
 * (auto-detected via [com.eight87.strictlykeptboy.git.RemoteBinding.readOnlyDetected]
 * OR user-marked via `treatAsReadOnly`).
 *
 * M3E error-container styling per spec.
 */
@Composable
fun ReadOnlyBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
            .testTag(TestTagReadOnlyBanner),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.read_only_banner_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
