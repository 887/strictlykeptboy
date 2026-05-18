package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.TemplateEntry
import java.time.format.DateTimeFormatter

const val TestTagEventCreateSheet = "EventCreateSheet"
const val TestTagEventCreateSheetTabFreeForm = "EventCreateSheetTabFreeForm"
const val TestTagEventCreateSheetTabTemplate = "EventCreateSheetTabTemplate"
const val TestTagEventCreateOverlapDialog = "EventCreateOverlapDialog"
const val TestTagOverlapScheduleAnyway = "OverlapScheduleAnyway"
const val TestTagOverlapPickDifferent = "OverlapPickDifferent"
const val TestTagOverlapCancel = "OverlapCancel"

/**
 * Phase FFF / EC-A.3 — top-level event-create sheet.
 *
 * Two segmented-button tabs at the top: **Free-form** | **From template**.
 * Each tab renders a dedicated child composable; overlap dialog
 * surfaces from controller state.
 *
 * SOLID-S: orchestration only — rendering of each tab lives in its
 * own composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCreateSheet(
    state: EventCreateSheetState,
    onDismiss: () -> Unit,
    onTabChange: (EventCreateTab) -> Unit,
    onDraftChange: (EventDraft) -> Unit,
    onConfirmFreeForm: () -> Unit,
    onPickTemplate: (TemplateEntry) -> Unit,
    onConfirmTemplate: () -> Unit,
    onCancelTemplate: () -> Unit,
    onOverlapScheduleAnyway: () -> Unit,
    onOverlapPickDifferent: () -> Unit,
    onOverlapCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Full-screen Surface (was a ModalBottomSheet — user found the
    // swipe-down-to-dismiss + partial-height behaviour distracting).
    // Back arrow in the top app bar is the explicit dismiss path now.
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag(TestTagEventCreateSheet),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.event_create_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(android.R.string.cancel),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SegmentedButton(
                        selected = state.tab == EventCreateTab.FreeForm,
                        onClick = { onTabChange(EventCreateTab.FreeForm) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag(TestTagEventCreateSheetTabFreeForm),
                    ) { Text(stringResource(R.string.event_create_tab_free_form)) }
                    SegmentedButton(
                        selected = state.tab == EventCreateTab.Template,
                        onClick = { onTabChange(EventCreateTab.Template) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag(TestTagEventCreateSheetTabTemplate),
                    ) { Text(stringResource(R.string.event_create_tab_template)) }
                }
                when (state.tab) {
                    EventCreateTab.FreeForm -> EventCreateFreeFormForm(
                        draft = state.draft,
                        calendars = state.calendars,
                        errors = EventDraftValidator.validate(state.draft),
                        onDraftChange = onDraftChange,
                        onConfirm = onConfirmFreeForm,
                    )
                    EventCreateTab.Template -> TemplatePickerContent(
                        entries = state.templateEntries,
                        neutralMode = state.neutralMode,
                        onPickTemplate = onPickTemplate,
                        onFlipToFreeForm = { onTabChange(EventCreateTab.FreeForm) },
                    )
                }
            }
        }
    }

    state.confirmingTemplate?.let { tpl ->
        TemplateConfirmSheet(
            entry = tpl,
            previewSubbeats = state.confirmingTemplateSubbeats,
            startIso = state.confirmingTemplateStart,
            onConfirm = onConfirmTemplate,
            onCancel = onCancelTemplate,
        )
    }

    state.overlap?.let { hit ->
        AlertDialog(
            onDismissRequest = onOverlapCancel,
            title = { Text(stringResource(R.string.event_create_overlap_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.event_create_overlap_body,
                        hit.otherTitle,
                        DateTimeFormatter.ofPattern("HH:mm").format(hit.otherStart),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onOverlapPickDifferent,
                    modifier = Modifier.testTag(TestTagOverlapPickDifferent),
                ) { Text(stringResource(R.string.event_create_overlap_pick_different)) }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = onOverlapScheduleAnyway,
                        modifier = Modifier.testTag(TestTagOverlapScheduleAnyway),
                    ) { Text(stringResource(R.string.event_create_overlap_schedule_anyway)) }
                    TextButton(
                        onClick = onOverlapCancel,
                        modifier = Modifier.testTag(TestTagOverlapCancel),
                    ) { Text(stringResource(R.string.event_create_overlap_cancel)) }
                }
            },
            modifier = Modifier.testTag(TestTagEventCreateOverlapDialog),
        )
    }
}

data class EventCreateSheetState(
    val tab: EventCreateTab = EventCreateTab.FreeForm,
    val draft: EventDraft = EventDraft(),
    val calendars: List<CalendarOption> = emptyList(),
    val templateEntries: List<TemplateEntry> = emptyList(),
    val neutralMode: Boolean = false,
    val confirmingTemplate: TemplateEntry? = null,
    val confirmingTemplateSubbeats: List<com.eight87.strictlykeptboy.store.AtomicTemplateSubbeat> = emptyList(),
    val confirmingTemplateStart: String = "",
    val overlap: OverlapHit? = null,
)
