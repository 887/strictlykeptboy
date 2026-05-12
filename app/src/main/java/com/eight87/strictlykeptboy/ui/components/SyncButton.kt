package com.eight87.strictlykeptboy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.eight87.strictlykeptboy.R

const val TestTagSyncButton = "SyncButton"
const val TestTagSyncErrorDot = "SyncErrorDot"
const val TestTagSyncSuccessCheck = "SyncSuccessCheck"

/** Phase J — visual states for the top-bar sync button. */
enum class SyncButtonState { Idle, Syncing, Error, Success }

/** UI-B.3 / Phase J.3 — sync button with idle/syncing/error/success states. */
@Composable
fun SyncButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: SyncButtonState = SyncButtonState.Idle,
    visible: Boolean = true,
) {
    if (!visible) return
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp).testTag(TestTagSyncButton),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                SyncButtonState.Idle -> Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = stringResource(R.string.cd_sync),
                )
                SyncButtonState.Syncing -> {
                    val rot by rememberInfiniteTransition(label = "sync-spin")
                        .animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "sync-rot",
                        )
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = stringResource(R.string.cd_syncing),
                        modifier = Modifier.rotate(rot),
                    )
                }
                SyncButtonState.Error -> {
                    Icon(imageVector = Icons.Filled.Sync, contentDescription = stringResource(R.string.cd_sync_error))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .testTag(TestTagSyncErrorDot),
                    )
                }
                SyncButtonState.Success -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_sync_complete),
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.testTag(TestTagSyncSuccessCheck),
                )
            }
        }
    }
}
