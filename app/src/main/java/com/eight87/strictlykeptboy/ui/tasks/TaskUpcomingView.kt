package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagUpcomingView = "UpcomingView"
const val TestTagUpcomingBucketHeader = "UpcomingBucketHeader"

/**
 * D-29 — Upcoming view with chronological date-bucket section headers.
 *
 * Groups [tasks] into Tomorrow / This week / Next week / Later buckets via
 * [forUpcomingBucketed]. Empty buckets are omitted. Empty overall → delegates
 * to [EmptyTasksState] with an Upcoming-specific copy.
 *
 * SOLID:
 *  - S — single job: render the bucketed upcoming list.
 *  - O — bucket logic lives in [forUpcomingBucketed] (pure); this composable
 *    is only layout.
 *  - I — takes the narrowest surface ([List<TaskItem>] + callbacks).
 *  - D — depends only on [TaskItem] / [TaskRow] abstractions, not on Room or
 *    any concrete data source.
 */
@Composable
fun TaskUpcomingView(
    tasks: List<TaskItem>,
    onToggleDone: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (TaskItem) -> Unit = {},
    multiRepo: Boolean = false,
    activeRepoOwner: String = "",
    onStartTask: ((String) -> Unit)? = null,
) {
    val bucketed = tasks.forUpcomingBucketed()
    if (bucketed.isEmpty()) {
        EmptyTasksState(
            primaryMessage = stringResource(R.string.tasks_empty_upcoming_primary),
            neutralMessage = stringResource(R.string.tasks_empty_upcoming_neutral),
            modifier = modifier.testTag(TestTagUpcomingView),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TestTagUpcomingView),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bucketed.forEach { (bucket, items) ->
            item(key = "bucket-header-${bucket.name}") {
                val label = when (bucket) {
                    UpcomingBucket.Tomorrow -> stringResource(R.string.upcoming_bucket_tomorrow)
                    UpcomingBucket.ThisWeek -> stringResource(R.string.upcoming_bucket_this_week)
                    UpcomingBucket.NextWeek -> stringResource(R.string.upcoming_bucket_next_week)
                    UpcomingBucket.Later -> stringResource(R.string.upcoming_bucket_later)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("$TestTagUpcomingBucketHeader-${bucket.name}"),
                )
            }
            items(items, key = { it.id }) { task ->
                TaskRow(
                    item = task,
                    onToggleDone = { onToggleDone(task) },
                    onClick = { onOpen(task) },
                    onLongClick = { onLongPress(task) },
                    multiRepo = multiRepo,
                    activeRepoOwner = activeRepoOwner,
                    onStartTask = onStartTask,
                )
            }
        }
    }
}
