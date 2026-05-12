package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagSyncButton = "SyncButton"

/** UI-B.3 — sync button. Visual states (idle / syncing / error / conflict / ahead)
 *  land in Phase J; this is the stub. */
@Composable
fun SyncButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp).testTag(TestTagSyncButton),
    ) {
        Icon(imageVector = Icons.Filled.Sync, contentDescription = "Sync")
    }
}
