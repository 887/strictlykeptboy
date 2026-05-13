package com.eight87.strictlykeptboy.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

/**
 * Phase RR.2 — small QR preview tile for the share sheet.
 *
 * Renders [content] (the share-link URI) as a 192dp QR image with a
 * `share_qr_label` caption above it. Lives in its own file to keep
 * [ShareSheet] focused on layout (S in SOLID).
 */
@Composable
fun SharedRepoQrPreview(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) {
        runCatching { QrCodeGenerator.encode(content) }.getOrNull()
    }
    Column(
        modifier = modifier.wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.share_qr_label),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(6.dp))
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.share_qr_label),
                modifier = Modifier.size(192.dp),
            )
        }
    }
}
