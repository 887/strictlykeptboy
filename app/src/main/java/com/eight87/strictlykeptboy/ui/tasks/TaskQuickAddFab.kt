package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagQuickAddFab = "QuickAddFab"
const val TestTagQuickAddSheet = "QuickAddSheet"
const val TestTagQuickAddInput = "QuickAddInput"
const val TestTagQuickAddSubmit = "QuickAddSubmit"
const val TestTagQuickAddTarget = "QuickAddTarget"

/** UI-J.7 — quick-add target. */
sealed interface QuickAddTarget {
    val label: String
    data class Todolist(val info: TodolistInfo) : QuickAddTarget {
        override val label: String get() = (info.emoji?.plus(" ") ?: "") + info.name
    }
    data object TodayEvent : QuickAddTarget { override val label: String = "Today as event" }
    data object TomorrowEvent : QuickAddTarget { override val label: String = "Tomorrow as event" }
}

@Composable
fun TaskQuickAddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.testTag(TestTagQuickAddFab),
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Quick add")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskQuickAddSheet(
    todolists: List<TodolistInfo>,
    initialTarget: QuickAddTarget?,
    onDismiss: () -> Unit,
    onSubmit: (title: String, target: QuickAddTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val targets: List<QuickAddTarget> = todolists.map { QuickAddTarget.Todolist(it) } +
        QuickAddTarget.TodayEvent + QuickAddTarget.TomorrowEvent

    var title by remember { mutableStateOf("") }
    var target by remember(initialTarget) {
        mutableStateOf(initialTarget ?: targets.firstOrNull())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagQuickAddSheet),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("what needs doing?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag(TestTagQuickAddInput),
                singleLine = true,
            )
            Text(
                "Target",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            ) {
                targets.forEach { t ->
                    val key = targetKey(t)
                    FilterChip(
                        selected = target == t,
                        onClick = { target = t },
                        label = { Text(t.label) },
                        modifier = Modifier.testTag("$TestTagQuickAddTarget-$key"),
                    )
                }
            }
            Button(
                onClick = {
                    val t = target ?: return@Button
                    val cleaned = title.trim()
                    if (cleaned.isEmpty()) return@Button
                    onSubmit(cleaned, t)
                },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(TestTagQuickAddSubmit),
            ) {
                Text("Add")
            }
        }
    }
}

internal fun targetKey(t: QuickAddTarget): String = when (t) {
    is QuickAddTarget.Todolist -> "list-${t.info.id}"
    QuickAddTarget.TodayEvent -> "today-event"
    QuickAddTarget.TomorrowEvent -> "tomorrow-event"
}
