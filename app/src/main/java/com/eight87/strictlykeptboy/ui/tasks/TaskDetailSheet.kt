package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.components.MarkdownRenderer

const val TestTagDetailSheet = "TaskDetailSheet"
const val TestTagDetailPane = "TaskDetailPane"
const val TestTagDetailTitle = "TaskDetailTitle"
const val TestTagDetailAuthor = "TaskDetailAuthor"
const val TestTagDetailList = "TaskDetailList"
const val TestTagDetailEdit = "TaskDetailEdit"
const val TestTagDetailAttachment = "TaskDetailAttachment"
const val TestTagDetailSubtask = "TaskDetailSubtask"

/** UI-J.6 — task detail sheet. Modal bottom sheet with full task info. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: TaskItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagDetailSheet),
    ) {
        TaskDetailContent(task = task, onEdit = onEdit, onToggleDone = onToggleDone)
    }
}

/**
 * Phase R.3 — pane-mode renderer for the task detail. Identical inner
 * layout to the modal sheet so the test tags + a11y mapping are stable
 * across Compact and Medium/Expanded.
 */
@Composable
fun TaskDetailContent(
    task: TaskItem,
    onEdit: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
        Column(modifier = modifier.fillMaxWidth().padding(16.dp).testTag(TestTagDetailPane)) {
            // Title row + completion checkbox.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.done, onCheckedChange = { onToggleDone() })
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .testTag(TestTagDetailTitle),
                )
                // completion-state badge
                if (task.done) {
                    Badge { Text(stringResource(R.string.task_detail_done)) }
                } else if (task.isOverdue) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) { Text(stringResource(R.string.task_detail_overdue)) }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(task.todolist.name) },
                    leadingIcon = {
                        Surface(
                            color = colorFromSeed(task.todolist.colorSeed.ifBlank { task.todolist.id }),
                            shape = CircleShape,
                            modifier = Modifier.size(12.dp),
                        ) {}
                    },
                    modifier = Modifier.testTag(TestTagDetailList),
                )
                if (task.author.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text(task.author) },
                        modifier = Modifier.testTag(TestTagDetailAuthor),
                    )
                }
            }

            if (task.due != null) {
                Text(
                    text = stringResource(R.string.task_detail_due, task.due.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (task.doneAt != null) {
                Text(
                    text = stringResource(R.string.task_detail_completed_at, task.doneAt.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (task.priority > 0) {
                Text(
                    text = stringResource(R.string.task_detail_priority, task.priority),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (task.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                ) {
                    task.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text("#$tag") },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }
            }

            if (task.body.isNotBlank()) {
                // Phase EE — inline-markdown body styling. Subtask
                // lines (`- [ ] foo`) are stripped from the rendered
                // body and surfaced as Compose Checkboxes below;
                // everything else flows through Markwon.
                val markdownBody = renderBodyForSheet(task.body)
                if (markdownBody.isNotBlank()) {
                    MarkdownRenderer(
                        markdown = markdownBody,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                // Subtask checkboxes — quick visual per H.6.
                val subtasks = parseSubtasks(task.body)
                if (subtasks.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        subtasks.forEachIndexed { idx, st ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = st.checked,
                                    onCheckedChange = null,
                                    modifier = Modifier.testTag("$TestTagDetailSubtask-$idx"),
                                )
                                Text(st.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (task.attachments.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.task_detail_attachments), style = MaterialTheme.typography.titleSmall)
                    task.attachments.forEachIndexed { idx, att ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .testTag("$TestTagDetailAttachment-$idx"),
                        ) {
                            Icon(iconForAttachment(att.kind), contentDescription = att.kind.name)
                            Text(
                                text = "  ${att.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag(TestTagDetailEdit),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(stringResource(R.string.task_detail_edit))
                }
            }
        }
}

private data class SubtaskLine(val checked: Boolean, val label: String)

private val SubtaskRe = Regex("""^\s*-\s*\[( |x|X)]\s+(.+)$""")

private fun parseSubtasks(body: String): List<SubtaskLine> =
    body.lineSequence().mapNotNull {
        val m = SubtaskRe.matchEntire(it) ?: return@mapNotNull null
        SubtaskLine(checked = m.groupValues[1].equals("x", ignoreCase = true), label = m.groupValues[2])
    }.toList()

private fun renderBodyForSheet(body: String): String =
    body.lineSequence()
        .filterNot { SubtaskRe.matches(it) }
        .joinToString("\n")
        .trim()

private fun iconForAttachment(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.Link -> Icons.Filled.Link
    AttachmentKind.Qr -> Icons.Filled.QrCode
    AttachmentKind.File -> Icons.Filled.AttachFile
    AttachmentKind.Barcode -> Icons.Filled.QrCode
    AttachmentKind.VCard -> Icons.Filled.Person
    AttachmentKind.Location -> Icons.Filled.LocationOn
}
