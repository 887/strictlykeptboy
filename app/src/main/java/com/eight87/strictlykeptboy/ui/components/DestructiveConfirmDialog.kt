package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Round 2.17 Phase G.4 — destructive-confirm dialog per D-2.17.g.
 *
 * Two gates before [onConfirm] fires:
 *  1. The user must type [confirmPhrase] (default `"restore"`)
 *     into the field exactly.
 *  2. The user must press-and-hold the red CTA for [holdMillis]
 *     (default 3000 ms). Releasing the press resets the hold.
 *
 * The hold is realised by polling 30 fps while the press is active —
 * cheaper than `Modifier.combinedClickable(onLongClick=…)` because we
 * want a visible progress bar during the hold. The button visibly
 * disables until the phrase is typed.
 *
 * Reusable for any future destructive flow (delete-repo, wipe-cache,
 * etc.). Keep this composable presentation-only: callers wire the
 * actual destructive work into [onConfirm].
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmPhrase: String = "restore",
    holdMillis: Long = 3000L,
    testTagRoot: String = "DestructiveConfirmDialog",
) {
    var typed by remember { mutableStateOf("") }
    var pressedAt by remember { mutableStateOf<Long?>(null) }
    var holdProgress by remember { mutableStateOf(0f) }
    val phraseMatches = typed == confirmPhrase

    // Tick the progress bar while the press is held. Resets to 0 on
    // release; fires onConfirm when progress hits 1.0 and the phrase
    // is correct. Suspended in remember-scope so we tear down cleanly
    // on dismissal.
    LaunchedEffect(pressedAt, phraseMatches) {
        val started = pressedAt
        if (started == null || !phraseMatches) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            val elapsed = System.currentTimeMillis() - started
            val p = (elapsed.toFloat() / holdMillis).coerceIn(0f, 1f)
            holdProgress = p
            if (p >= 1f) {
                onConfirm()
                return@LaunchedEffect
            }
            delay(33L)
        }
    }

    DisposableEffect(Unit) {
        onDispose { holdProgress = 0f }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text("Type \"$confirmPhrase\" to enable") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$testTagRoot-PhraseField"),
                )
                if (phraseMatches) {
                    Text(
                        "Hold the button for 3 seconds to confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$testTagRoot-HoldProgress"),
                )
            }
        },
        confirmButton = {
            // We deliberately roll a plain Box-as-button here rather
            // than Material's `Button(...)`. Button's internal
            // `clickable` modifier eats the press gesture (so onPress /
            // tryAwaitRelease never run for our hold-detector), which
            // is exactly the opposite of what we want for a 3-second
            // hold-to-confirm.
            val bg = if (phraseMatches) MaterialTheme.colorScheme.error else Color.Transparent
            val fg = if (phraseMatches) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .alpha(if (phraseMatches) 1f else 0.4f)
                    .background(bg, RoundedCornerShape(50))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .testTag("$testTagRoot-ConfirmHold")
                    .pointerInput(phraseMatches) {
                        if (!phraseMatches) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                pressedAt = System.currentTimeMillis()
                                val released = tryAwaitRelease()
                                if (!released || holdProgress < 1f) {
                                    pressedAt = null
                                }
                            },
                        )
                    },
            ) {
                Text(confirmLabel, color = fg)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("$testTagRoot-Cancel"),
            ) {
                Text(cancelLabel)
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .testTag(testTagRoot),
    )
}
