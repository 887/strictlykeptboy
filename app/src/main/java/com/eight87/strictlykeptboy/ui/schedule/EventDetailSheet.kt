package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.resolver.InstanceSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.CompletionState
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.ui.components.MarkdownRenderer
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TestTagEventDetailSheet = "EventDetailSheet"
const val TestTagEventDetailPane = "EventDetailPane"
const val TestTagEventDetailTitle = "EventDetailTitle"
const val TestTagEventDetailTime = "EventDetailTime"
const val TestTagEventDetailCalendar = "EventDetailCalendar"
const val TestTagEventDetailBody = "EventDetailBody"
const val TestTagEventDetailTag = "EventDetailTag"
const val TestTagEventDetailAttachment = "EventDetailAttachment"
const val TestTagEventDetailAuthor = "EventDetailAuthor"
const val TestTagEventDetailCompletion = "EventDetailCompletion"
const val TestTagEventDetailEdit = "EventDetailEdit"
/** Phase 2.1.F.2 — per-event mute toggle. */
const val TestTagEventDetailMute = "EventDetailMute"

// 2.1.C.7 — source-section test tags
const val TestTagEventDetailSource = "EventDetailSource"
const val TestTagEventDetailSourceRepo = "EventDetailSourceRepo"
const val TestTagEventDetailSourceKind = "EventDetailSourceKind"
const val TestTagEventDetailSourceAuthor = "EventDetailSourceAuthor"
const val TestTagEventDetailSupersededNote = "EventDetailSupersededNote"

/**
 * Phase G.7 — modal bottom sheet that surfaces an event's full content.
 *
 * Stateless: caller controls visibility via the [band] non-null flag.
 * Markdown is rendered as plain text for now per the brief — full
 * Markdown rendering is Phase EE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    band: DayBand,
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    attachments: List<AttachmentRef> = emptyList(),
    calendarName: String? = null,
    notificationPrefs: NotificationPrefs? = null,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TestTagEventDetailSheet),
    ) {
        EventDetailContent(
            band = band,
            onEdit = onEdit,
            attachments = attachments,
            calendarName = calendarName,
            notificationPrefs = notificationPrefs,
        )
    }
}

/**
 * Phase R.2 — pane-mode renderer for the same content the modal sheet
 * shows on Compact. The same field layout / strings / test tags apply;
 * only the chrome differs (no sheet handle, no drag dismiss).
 */
@Composable
fun EventDetailContent(
    band: DayBand,
    onEdit: () -> Unit = {},
    attachments: List<AttachmentRef> = emptyList(),
    calendarName: String? = null,
    repoName: String? = null,
    supersededByName: String? = null,
    notificationPrefs: NotificationPrefs? = null,
    modifier: Modifier = Modifier,
) {
    val tz = band.instance.effectiveStart.zone
    val fmt = DateTimeFormatter.ofPattern("EEE MMM d  HH:mm", Locale.getDefault())
    val endFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .testTag(TestTagEventDetailPane),
    ) {
            // Title
            Text(
                text = (band.instance.emoji?.let { "$it  " } ?: "") + band.instance.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(TestTagEventDetailTitle),
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Time + tz
            Text(
                text = "${fmt.format(band.instance.effectiveStart)} – " +
                    "${endFmt.format(band.instance.effectiveEnd)}  ($tz)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TestTagEventDetailTime),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Round 2.1.C.7 — source section: repo · calendar · kind · author
            // appears above the calendar chip so the user can tell at a glance
            // which repo the event lives in, especially with cross-repo views.
            Column(modifier = Modifier.testTag(TestTagEventDetailSource)) {
                if (!repoName.isNullOrBlank()) {
                    Text(
                        text = "Repo: $repoName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TestTagEventDetailSourceRepo),
                    )
                }
                Text(
                    text = "Kind: " + when (band.kind) {
                        com.eight87.strictlykeptboy.resolver.CalendarKind.Timebox -> "Timebox"
                        com.eight87.strictlykeptboy.resolver.CalendarKind.Regular -> "Regular"
                        // Round 2.18.A.3 — external (CalendarContract) calendars.
                        com.eight87.strictlykeptboy.resolver.CalendarKind.External -> "External"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TestTagEventDetailSourceKind),
                )
                band.instance.author?.let { author ->
                    Text(
                        text = "Author: ${author.id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TestTagEventDetailSourceAuthor),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Calendar source chip — Round 2.1.C.7 falls back to the calendar
            // ref id only when the caller did not resolve a CalendarMeta.displayName.
            val chipLabel = calendarName?.takeIf { it.isNotBlank() } ?: band.instance.calendar.id
            AssistChip(
                onClick = {},
                label = { Text(chipLabel) },
                modifier = Modifier.testTag(TestTagEventDetailCalendar),
            )
            // Round 2.1.C.4 — pause-by-supersedence explanation.
            if (band.supersededByCalendar != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val byLabel = supersededByName?.takeIf { it.isNotBlank() }
                    ?: band.supersededByCalendar.id
                Text(
                    text = "Paused by $byLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TestTagEventDetailSupersededNote),
                )
            }
            CompletionBadge(state = band.completionState)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Body (Phase EE — inline Markdown via Markwon).
            if (band.instance.body.isNotBlank()) {
                MarkdownRenderer(
                    markdown = band.instance.body,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = TestTagEventDetailBody,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tags
            if (band.instance.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    band.instance.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("#$tag") },
                            modifier = Modifier.testTag("$TestTagEventDetailTag-$tag"),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Attachments (HV-M)
            if (attachments.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.event_detail_attachments),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                attachments.forEach { att ->
                    AttachmentRow(att)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Author
            band.instance.author?.let { author ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(24.dp),
                    ) {}
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.event_detail_author, author.id),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(TestTagEventDetailAuthor),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Phase 2.1.F.2 — per-event mute toggle. Surfaces in both the
            // compact ModalBottomSheet caller and the tablet detail pane
            // since both flow through this composable. Backed by
            // `event.<repoId>.<eventId>.muted` in NotificationPrefs.
            if (notificationPrefs != null) {
                val eventId = when (val s = band.instance.source) {
                    is InstanceSource.OneOff -> s.eventId.id
                    is InstanceSource.RuleInstance -> s.ruleId.id
                }
                val repoId = band.instance.repo.id
                var muted by remember(repoId, eventId) {
                    mutableStateOf(notificationPrefs.isEventMuted(repoId, eventId))
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.event_detail_mute_reminders),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Spacer(modifier = Modifier.size(0.dp).weight(1f))
                    Switch(
                        checked = muted,
                        onCheckedChange = {
                            muted = it
                            notificationPrefs.setEventMuted(repoId, eventId, it)
                        },
                        modifier = Modifier.testTag(TestTagEventDetailMute),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            FilledTonalButton(
                onClick = onEdit,
                modifier = Modifier.testTag(TestTagEventDetailEdit),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.event_detail_edit))
            }
    }
}

@Composable
private fun CompletionBadge(state: CompletionState) {
    val (label, color) = when (state) {
        CompletionState.Scheduled -> stringResource(R.string.event_detail_state_scheduled) to MaterialTheme.colorScheme.surfaceContainer
        CompletionState.InProgress -> stringResource(R.string.event_detail_state_in_progress) to MaterialTheme.colorScheme.primaryContainer
        CompletionState.CompletedBySchedule -> stringResource(R.string.event_detail_state_completed) to MaterialTheme.colorScheme.secondaryContainer
        CompletionState.Skipped -> stringResource(R.string.event_detail_state_skipped) to MaterialTheme.colorScheme.surfaceContainer
        CompletionState.PartiallyDone -> stringResource(R.string.event_detail_state_partially_done) to MaterialTheme.colorScheme.tertiaryContainer
        CompletionState.CompletedEarly -> stringResource(R.string.event_detail_state_completed_early) to MaterialTheme.colorScheme.secondaryContainer
        CompletionState.CompletedLate -> stringResource(R.string.event_detail_state_completed_late) to MaterialTheme.colorScheme.tertiaryContainer
    }
    Spacer(modifier = Modifier.height(6.dp))
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color),
        modifier = Modifier.testTag(TestTagEventDetailCompletion),
    )
}

@Composable
private fun AttachmentRow(att: AttachmentRef) {
    val icon: ImageVector = when (att.kind) {
        AttachmentKind.Link -> Icons.Filled.AttachFile
        AttachmentKind.Qr -> Icons.Filled.QrCode
        AttachmentKind.File -> Icons.Filled.Description
        AttachmentKind.Barcode -> Icons.Filled.QrCode
        AttachmentKind.Vcard -> Icons.Filled.Person
        AttachmentKind.Location -> Icons.Filled.LocationOn
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .testTag("$TestTagEventDetailAttachment-${att.label}"),
    ) {
        Icon(icon, contentDescription = att.kind.name, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = att.label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** HV-M attachment kinds — minimal surface for Phase G; full model in Phase EE. */
enum class AttachmentKind { Link, Qr, File, Barcode, Vcard, Location }

data class AttachmentRef(val kind: AttachmentKind, val label: String)
