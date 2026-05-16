package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagDragRecurrencePrompt = "DragRecurrencePrompt"
const val TestTagDragRecurThisOne = "DragRecurThisOne"
const val TestTagDragRecurThisAndFuture = "DragRecurThisAndFuture"
const val TestTagDragRecurWholeSeries = "DragRecurWholeSeries"

/**
 * Round 2.22 / Phase B UI follow-up — D-2.22.b recurrence-drop prompt.
 *
 * Three-choice M3 [AlertDialog] mounted by [SchedulePane] when a
 * drag-drop lands on a recurring-rule instance. Stateless — caller
 * owns the `pendingRecurringDrop` state.
 */
@Composable
fun DragRescheduleRecurrencePrompt(
    eventTitle: String,
    onChoice: (DragRescheduleController.RecurringChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(TestTagDragRecurrencePrompt),
        onDismissRequest = onDismiss,
        title = { Text(text = "Reschedule recurring event") },
        text = {
            Column {
                Text(
                    text = "“$eventTitle” is part of a series. Apply the move to:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TestTagDragRecurThisOne),
                onClick = { onChoice(DragRescheduleController.RecurringChoice.THIS_ONE) },
            ) { Text("Just this one") }
        },
        dismissButton = {
            Column {
                TextButton(
                    modifier = Modifier.testTag(TestTagDragRecurThisAndFuture),
                    onClick = { onChoice(DragRescheduleController.RecurringChoice.THIS_AND_FUTURE) },
                ) { Text("This and future") }
                TextButton(
                    modifier = Modifier.testTag(TestTagDragRecurWholeSeries),
                    onClick = { onChoice(DragRescheduleController.RecurringChoice.WHOLE_SERIES) },
                ) { Text("Whole series") }
            }
        },
    )
}
