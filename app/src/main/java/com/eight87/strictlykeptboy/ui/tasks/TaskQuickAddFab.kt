package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.components.WrappingChipRow
import com.eight87.strictlykeptboy.ui.a11y.labelString

const val TestTagQuickAddFab = "QuickAddFab"
const val TestTagQuickAddSheet = "QuickAddSheet"
const val TestTagQuickAddInput = "QuickAddInput"
const val TestTagQuickAddSubmit = "QuickAddSubmit"
const val TestTagQuickAddTarget = "QuickAddTarget"

/**
 * UI-J.7 — quick-add target.
 *
 * Phase U.4 / F11 note: [label] on [TodayEvent] / [TomorrowEvent] is
 * **wire-format / fallback only**. Translatable UI display routes through
 * `QuickAddTarget.labelString()` in `ui/a11y/EnumLabels.kt`. The
 * [Todolist] variant's label is user-supplied (todolist name + emoji),
 * not framework chrome, so it is not localised.
 */
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
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_tasks_quick_add))
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

    // D-14 fix — auto-focus the input field so the keyboard raises
    // immediately when Quick Add opens, removing the extra tap.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Helper to submit the current input — shared by the Button and the
    // keyboard IME Done action so both paths are identical.
    val submitAction: () -> Unit = submit@{
        val t = target ?: return@submit
        val cleaned = title.trim()
        if (cleaned.isEmpty()) return@submit
        onSubmit(cleaned, t)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagQuickAddSheet),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.tasks_quick_add_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .focusRequester(focusRequester)
                    .testTag(TestTagQuickAddInput),
                // D-14 fix — singleLine + ImeAction.Done + KeyboardActions
                // wired to submit so the keyboard Done key adds the task
                // (3 interactions: open → type → Done, not 4–5).
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitAction() }),
            )
            Text(
                stringResource(R.string.tasks_quick_add_target_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            WrappingChipRow(modifier = Modifier.padding(top = 4.dp)) {
                targets.forEach { t ->
                    val key = targetKey(t)
                    FilterChip(
                        selected = target == t,
                        onClick = { target = t },
                        label = { Text(t.labelString()) },
                        modifier = Modifier.testTag("$TestTagQuickAddTarget-$key"),
                    )
                }
            }
            Button(
                onClick = submitAction,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(TestTagQuickAddSubmit),
            ) {
                Text(stringResource(R.string.tasks_quick_add_submit))
            }
        }
    }
}

internal fun targetKey(t: QuickAddTarget): String = when (t) {
    is QuickAddTarget.Todolist -> "list-${t.info.id}"
    QuickAddTarget.TodayEvent -> "today-event"
    QuickAddTarget.TomorrowEvent -> "tomorrow-event"
}
