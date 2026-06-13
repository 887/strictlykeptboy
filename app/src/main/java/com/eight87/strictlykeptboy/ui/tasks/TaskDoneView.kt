package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagDoneView = "DoneView"

/**
 * D-3 fix — dedicated Done-filter view.
 *
 * Shows every task with [TaskItem.done] == true, sorted most-recently-done
 * first (falling back to [java.time.LocalDate.MIN] for tasks that were
 * toggled in-memory without a [TaskItem.doneAt] timestamp).
 *
 * Deliberately does NOT route through [TaskStandingView] (which internally
 * calls [sortedForStanding] and filters to `standing == true` only, causing
 * all one-off completed tasks to vanish).
 *
 * Inverted-routines tasks (D.54 — passive habits, `standing = true`,
 * `done = false` by default) are NOT treated as completed here:
 * only tasks with an explicit `done = true` appear, which is correct —
 * checking off an inverted-routine is a *deviation*, not a completion.
 *
 * D-4 fix — empty-state copy is "nothing finished yet" (not "no standing
 * tasks"), keeping the bat mascot on-brand.
 */
@Composable
fun TaskDoneView(
    tasks: List<TaskItem>,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    onStartTask: ((String) -> Unit)? = null,
) {
    // Only show tasks that are explicitly marked done. Inverted-routine
    // tasks (standing = true, done = false) stay out of this list.
    val done = tasks
        .filter { it.done }
        .sortedByDescending { it.doneAt ?: java.time.LocalDate.MIN }

    if (done.isEmpty()) {
        EmptyTasksState(
            primaryMessage = stringResource(R.string.tasks_empty_done_primary),
            neutralMessage = stringResource(R.string.tasks_empty_done_neutral),
            modifier = modifier.testTag(TestTagDoneView),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagDoneView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(done, key = { it.id }) { task ->
            TaskRow(
                item = task,
                onToggleDone = { onToggleDone(task) },
                onClick = { onOpen(task) },
                onLongClick = { onOpen(task) },
                onStartTask = onStartTask,
            )
        }
    }
}
