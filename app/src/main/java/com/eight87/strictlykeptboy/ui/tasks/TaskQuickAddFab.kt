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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import com.eight87.strictlykeptboy.ui.components.rememberFullHeightSheetState
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
import java.time.LocalDate

const val TestTagQuickAddFab = "QuickAddFab"
const val TestTagQuickAddSheet = "QuickAddSheet"
const val TestTagQuickAddInput = "QuickAddInput"
const val TestTagQuickAddSubmit = "QuickAddSubmit"
const val TestTagQuickAddTarget = "QuickAddTarget"
// D-40 — due-date chip row (only visible when a Todolist target is selected)
const val TestTagQuickAddDueNone = "QuickAddDueNone"
const val TestTagQuickAddDueToday = "QuickAddDueToday"
const val TestTagQuickAddDueTomorrow = "QuickAddDueTomorrow"

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

/** D-40 — due-date selection for the task path in Quick Add. */
enum class QuickAddDue { None, Today, Tomorrow }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskQuickAddSheet(
    todolists: List<TodolistInfo>,
    initialTarget: QuickAddTarget?,
    onDismiss: () -> Unit,
    /**
     * D-40 — extended signature: caller receives the picked [due] date
     * alongside the [title] and [target]. `null` due means no due date
     * (standing task). Event targets always carry today/tomorrow
     * implicitly via the target type, so [due] is only meaningful for
     * [QuickAddTarget.Todolist] targets.
     */
    onSubmit: (title: String, target: QuickAddTarget, due: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFullHeightSheetState()
    // D-23 fix — split targets into primary (todolist) and secondary (event) groups
    // so the user can clearly see adding to a list vs creating an event.
    val listTargets: List<QuickAddTarget> = todolists.map { QuickAddTarget.Todolist(it) }
    val eventTargets: List<QuickAddTarget> = listOf(QuickAddTarget.TodayEvent, QuickAddTarget.TomorrowEvent)
    val targets: List<QuickAddTarget> = listTargets + eventTargets

    var title by remember { mutableStateOf("") }
    var target by remember(initialTarget) {
        mutableStateOf(initialTarget ?: targets.firstOrNull())
    }
    // D-19 fix — track whether the user tried to submit an empty title
    // so we can show an error message on the field.
    var showEmptyError by remember { mutableStateOf(false) }
    // U-3 fix — default due date is Today (not No-date) so quick-added tasks
    // land in the Today view right away. The user can change to No-date or
    // Tomorrow explicitly if needed.
    // D-40 — due-date selection, only relevant for Todolist targets.
    var selectedDue by remember { mutableStateOf(QuickAddDue.Today) }

    // D-18 fix — gate focus on the sheet being fully expanded so the
    // focus request doesn't race the ModalBottomSheet settle animation
    // on first open (which caused the keyboard to not raise first time).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.Expanded) {
            focusRequester.requestFocus()
        }
    }

    // Helper to submit the current input — shared by the Button and the
    // keyboard IME Done action so both paths are identical.
    // U-7 fix — after successful submit, also invoke onDismiss so the sheet
    // closes (previously only the Button path dismissed; IME Done submitted
    // but left the sheet open requiring a separate swipe-down dismiss).
    val submitAction: () -> Unit = submit@{
        val t = target ?: return@submit
        val cleaned = title.trim()
        if (cleaned.isEmpty()) {
            // D-19 fix — surface a visible error instead of silent no-op.
            showEmptyError = true
            return@submit
        }
        showEmptyError = false
        // D-40 — resolve the due date for Todolist targets from selectedDue;
        // event targets carry their due date implicitly in the target type.
        val due: LocalDate? = when {
            t is QuickAddTarget.Todolist -> when (selectedDue) {
                QuickAddDue.Today -> LocalDate.now()
                QuickAddDue.Tomorrow -> LocalDate.now().plusDays(1)
                QuickAddDue.None -> null
            }
            else -> null // event targets handle due themselves via materialize()
        }
        onSubmit(cleaned, t, due)
        // U-7 fix — dismiss the sheet after submit so the user doesn't need to
        // manually swipe it away after the keyboard "Done" key submits a task.
        onDismiss()
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
                onValueChange = {
                    title = it
                    // D-19 fix — clear the error as soon as the user types.
                    if (showEmptyError && it.isNotBlank()) showEmptyError = false
                },
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
                // D-19 fix — show inline error when empty submit attempted.
                isError = showEmptyError,
                supportingText = if (showEmptyError) {
                    { Text(stringResource(R.string.tasks_quick_add_empty_error)) }
                } else null,
            )
            // D-23 fix — two labelled sections: list targets (primary) + event
            // targets (secondary). This makes it obvious you can add to a todolist
            // and the event options are just an alternative.
            if (listTargets.isNotEmpty()) {
                Text(
                    stringResource(R.string.quick_add_section_lists),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                WrappingChipRow(modifier = Modifier.padding(top = 4.dp)) {
                    listTargets.forEach { t ->
                        val key = targetKey(t)
                        FilterChip(
                            selected = target == t,
                            onClick = { target = t },
                            label = { Text(t.labelString()) },
                            modifier = Modifier.testTag("$TestTagQuickAddTarget-$key"),
                        )
                    }
                }
                // D-40 — due-date chips: only shown when a Todolist target is
                // selected (event targets imply today/tomorrow from the target type).
                if (target is QuickAddTarget.Todolist) {
                    Text(
                        stringResource(R.string.quick_add_due_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    WrappingChipRow(modifier = Modifier.padding(top = 4.dp)) {
                        FilterChip(
                            selected = selectedDue == QuickAddDue.None,
                            onClick = { selectedDue = QuickAddDue.None },
                            label = { Text(stringResource(R.string.quick_add_due_none)) },
                            modifier = Modifier.testTag(TestTagQuickAddDueNone),
                        )
                        FilterChip(
                            selected = selectedDue == QuickAddDue.Today,
                            onClick = { selectedDue = QuickAddDue.Today },
                            label = { Text(stringResource(R.string.quick_add_due_today)) },
                            modifier = Modifier.testTag(TestTagQuickAddDueToday),
                        )
                        FilterChip(
                            selected = selectedDue == QuickAddDue.Tomorrow,
                            onClick = { selectedDue = QuickAddDue.Tomorrow },
                            label = { Text(stringResource(R.string.quick_add_due_tomorrow)) },
                            modifier = Modifier.testTag(TestTagQuickAddDueTomorrow),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            Text(
                stringResource(R.string.quick_add_section_events),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (listTargets.isEmpty()) 12.dp else 0.dp),
            )
            WrappingChipRow(modifier = Modifier.padding(top = 4.dp)) {
                eventTargets.forEach { t ->
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
