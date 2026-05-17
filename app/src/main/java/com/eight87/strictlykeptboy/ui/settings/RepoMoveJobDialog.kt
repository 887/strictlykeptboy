package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.sync.RepoMover

const val TestTagRepoMoveDialog = "RepoMoveJobDialog"
const val TestTagRepoMoveCancel = "RepoMoveJobDialog-Cancel"
const val TestTagRepoMoveDone = "RepoMoveJobDialog-Done"

/**
 * Round 2.17 Phase E.5 — modal progress dialog for an in-flight
 * [RepoMover.move]. Observes [RepoMover.progress] and renders the
 * current repo + progress bar; the user can cancel mid-flight (the
 * mover rolls back prefs to the old parent). On `Done` / `Cancelled`
 * / `Failed`, the user closes the dialog via the OK button.
 */
@Composable
fun RepoMoveJobDialog(
    mover: RepoMover,
    request: MainActivity.MoveJobRequest,
    onDone: () -> Unit,
) {
    val progress by mover.progress.collectAsState()
    LaunchedEffect(request) {
        mover.move(
            oldParent = request.oldParent,
            newParent = request.newParent,
            oldParentLocation = request.oldLocation,
            newParentLocation = request.newLocation,
        )
    }
    AlertDialog(
        onDismissRequest = { /* not dismissable during move */ },
        modifier = Modifier.testTag(TestTagRepoMoveDialog),
        title = { Text(stringResource(R.string.repo_mover_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val p = progress) {
                    is RepoMover.Progress.Idle ->
                        LinearProgressIndicator(modifier = Modifier.testTag("RepoMove-Idle"))
                    is RepoMover.Progress.Running -> {
                        Text(
                            stringResource(
                                R.string.repo_mover_progress,
                                p.currentIndex + 1,
                                p.totalCount,
                                p.currentRepoLabel,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val fraction = if (p.totalCount == 0) 0f
                        else (p.currentIndex.toFloat() / p.totalCount.toFloat())
                        LinearProgressIndicator(progress = { fraction })
                    }
                    is RepoMover.Progress.Done -> Text(stringResource(R.string.repo_mover_done))
                    is RepoMover.Progress.Cancelled ->
                        Text(stringResource(R.string.repo_mover_cancelled))
                    is RepoMover.Progress.Failed ->
                        Text(stringResource(R.string.repo_mover_failed, p.message))
                }
            }
        },
        confirmButton = {
            // SOLID fix #7 — exhaustive over RepoMover.Progress so any
            // new variant trips the compiler. Idle / Running render no
            // confirm button (dismiss-side cancel covers in-flight).
            when (progress) {
                is RepoMover.Progress.Done,
                is RepoMover.Progress.Cancelled,
                is RepoMover.Progress.Failed ->
                    Button(
                        onClick = onDone,
                        modifier = Modifier.testTag(TestTagRepoMoveDone),
                    ) { Text("OK") }
                is RepoMover.Progress.Idle,
                is RepoMover.Progress.Running -> Unit
            }
        },
        dismissButton = {
            // SOLID fix #7 — exhaustive over RepoMover.Progress.
            when (progress) {
                is RepoMover.Progress.Running, is RepoMover.Progress.Idle ->
                    TextButton(
                        onClick = { mover.cancel() },
                        modifier = Modifier.testTag(TestTagRepoMoveCancel),
                    ) { Text(stringResource(R.string.repo_mover_cancel)) }
                is RepoMover.Progress.Done,
                is RepoMover.Progress.Cancelled,
                is RepoMover.Progress.Failed -> Unit
            }
        },
    )
}
