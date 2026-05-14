@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.notif.LogicalGroup
import com.eight87.strictlykeptboy.notif.NotificationChannels
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState
import java.time.Duration

const val TestTagCatNotifications = "Cat-Notifications"
const val TestTagCatNotificationsDefaultsCard = "Cat-Notifications-Defaults"
const val TestTagCatNotificationsDefaultsChipPrefix = "Cat-Notifications-Defaults-Chip-"
const val TestTagCatNotificationsDefaultsCustomChip = "Cat-Notifications-Defaults-Chip-Custom"
const val TestTagCatNotificationsDefaultsCustomDialog = "Cat-Notifications-Defaults-CustomDialog"
const val TestTagCatNotificationsDefaultsChannelPicker = "Cat-Notifications-Defaults-Channel"

/**
 * Phase S.4 — Notifications category.
 *
 * Combines the Phase M per-channel surface with the new master
 * briefings toggle + per-template-category default lead times. The
 * single outer verticalScroll keeps Compose layout happy (only one
 * vertical-scroll ancestor allowed).
 */
const val TestTagCatNotificationsChannelsPane = "Cat-Notifications-ChannelsPane"
const val TestTagCatNotificationsLeadsPane = "Cat-Notifications-LeadsPane"

@Composable
fun NotificationsCategory(
    prefs: NotificationPrefs,
    modifier: Modifier = Modifier,
    calendarsFlow: StateFlow<List<CalendarMeta>>? = null,
    nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(TestTagCatNotifications),
    ) {
        // 2.2.D.8 — defaults-for-new-events sub-card at the top.
        NotificationDefaultsBlock(prefs)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        NotificationsLeadsBlock(prefs)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()

        // Inline the per-channel surface here to avoid nested verticalScroll.
        Text(
            stringResource(R.string.settings_notifications_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        ChannelRow(prefs, NotificationChannels.EVENTS, R.string.notif_channel_events_name)
        ChannelRow(prefs, NotificationChannels.TASKS, R.string.notif_channel_tasks_name)
        ChannelRow(prefs, NotificationChannels.BRIEFINGS, R.string.notif_channel_briefings_name)
        ChannelRow(prefs, NotificationChannels.SYNC, R.string.notif_channel_sync_name)
        ChannelRow(prefs, NotificationChannels.ERRORS, R.string.notif_channel_errors_name)
        ChannelRow(prefs, NotificationChannels.FOREGROUND, R.string.notif_channel_foreground_name)

        // Phase 2.1.F.3 — per-calendar mute toggle. Backing
        // `cal.<repoId>.<calId>.enabled` keys are now bound to UI.
        // Time-bounded mute (F.4) uses a coarse 24h-from-now stamp.
        if (calendarsFlow != null) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            SectionLabel(stringResource(R.string.settings_notif_calendars_section))
            val cals by calendarsFlow.collectAsState()
            cals.forEach { meta ->
                CalendarMuteRow(prefs, meta, nowEpochMs)
            }
        }
    }
}

@Composable
private fun CalendarMuteRow(
    prefs: NotificationPrefs,
    meta: CalendarMeta,
    nowEpochMs: () -> Long,
) {
    val repoId = meta.repo.id
    val calId = meta.ref.id
    var enabled by remember(repoId, calId) { mutableStateOf(prefs.isCalendarEnabled(repoId, calId)) }
    val now = nowEpochMs()
    var until by remember(repoId, calId) {
        mutableStateOf(prefs.groupMuteUntil(LogicalGroup.Calendar(repoId, calId)))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meta.displayName, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                prefs.setCalendarEnabled(repoId, calId, it)
            }, modifier = Modifier.testTag("$TestTagCatNotifications-Cal-$repoId-$calId-Enabled"))
        }
        val muteActive = (until ?: 0L) > now
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (muteActive) {
                    val left = Duration.ofMillis((until!! - now).coerceAtLeast(0L))
                    stringResource(
                        R.string.settings_notif_mute_until_active,
                        left.toHours().coerceAtLeast(0L),
                    )
                } else {
                    stringResource(R.string.settings_notif_mute_until_inactive)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Switch(checked = muteActive, onCheckedChange = {
                val newUntil = if (it) now + 24L * 60L * 60L * 1000L else null
                prefs.setGroupMute(LogicalGroup.Calendar(repoId, calId), newUntil)
                until = newUntil
            }, modifier = Modifier.testTag("$TestTagCatNotifications-Cal-$repoId-$calId-Mute24h"))
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun NotificationsLeadsBlock(prefs: NotificationPrefs) {
    Text(
        stringResource(R.string.settings_category_notifications),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(12.dp))

    var briefingsOn by remember { mutableStateOf(prefs.isBriefingsEnabled()) }
    ToggleRow(
        label = stringResource(R.string.settings_notif_briefings_master),
        checked = briefingsOn,
        onCheckedChange = { briefingsOn = it; prefs.setBriefingsEnabled(it) },
        testTag = "$TestTagCatNotifications-BriefingsMaster",
    )
    Text(
        stringResource(R.string.settings_notif_briefings_blurb),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    SectionLabel(stringResource(R.string.settings_notif_lead_section))
    LeadTimeRow(prefs, "medical", R.string.settings_notif_lead_medical)
    LeadTimeRow(prefs, "flight", R.string.settings_notif_lead_flight)
    LeadTimeRow(prefs, "household", R.string.settings_notif_lead_household)
    LeadTimeRow(prefs, "general", R.string.settings_notif_lead_general)
}

@Composable
private fun NotificationsChannelsBlock(prefs: NotificationPrefs) {
    Text(
        stringResource(R.string.settings_notifications_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(8.dp))
    ChannelRow(prefs, NotificationChannels.EVENTS, R.string.notif_channel_events_name)
    ChannelRow(prefs, NotificationChannels.TASKS, R.string.notif_channel_tasks_name)
    ChannelRow(prefs, NotificationChannels.BRIEFINGS, R.string.notif_channel_briefings_name)
    ChannelRow(prefs, NotificationChannels.SYNC, R.string.notif_channel_sync_name)
    ChannelRow(prefs, NotificationChannels.ERRORS, R.string.notif_channel_errors_name)
    ChannelRow(prefs, NotificationChannels.FOREGROUND, R.string.notif_channel_foreground_name)
}

@Composable
private fun HorizontalDividerVertical() {
    androidx.compose.material3.VerticalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun LeadTimeRow(prefs: NotificationPrefs, category: String, labelRes: Int) {
    var value by remember { mutableStateOf(prefs.categoryLeadTimes(category) ?: "") }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(stringResource(labelRes))
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                prefs.setCategoryLeadTimes(category, it.ifBlank { null })
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("$TestTagCatNotifications-Lead-$category"),
            placeholder = { Text("15m;1h;1d") },
        )
    }
}

@Composable
private fun ChannelRow(prefs: NotificationPrefs, channelId: String, nameRes: Int) {
    var enabled by remember { mutableStateOf(prefs.isChannelEnabled(channelId)) }
    var silent by remember { mutableStateOf(prefs.isChannelSilent(channelId)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(nameRes), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_notif_channel_enabled),
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                prefs.setChannelEnabled(channelId, it)
            })
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_notif_channel_silent),
                modifier = Modifier.weight(1f),
            )
            Switch(checked = silent, onCheckedChange = {
                silent = it
                prefs.setChannelSilent(channelId, it)
            })
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

/** 2.2.D.8 — common reminder offsets as a multi-select FilterChip group. */
private val COMMON_LEAD_OFFSETS = listOf("5m", "15m", "30m", "1h", "1d", "1w")

/** 2.2.D.8 — channel options shown in the default-channel DropdownMenu. */
private data class ChannelOption(val id: String, val labelRes: Int)
private val CHANNEL_OPTIONS = listOf(
    ChannelOption(NotificationChannels.EVENTS, R.string.notif_channel_events_name),
    ChannelOption(NotificationChannels.TASKS, R.string.notif_channel_tasks_name),
    ChannelOption(NotificationChannels.BRIEFINGS, R.string.notif_channel_briefings_name),
)

@Composable
private fun NotificationDefaultsBlock(prefs: NotificationPrefs) {
    var selected by remember { mutableStateOf(prefs.defaultLeadTimes().toSet()) }
    var channel by remember { mutableStateOf(prefs.defaultChannel()) }
    var customDialogOpen by remember { mutableStateOf(false) }
    var channelMenuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(TestTagCatNotificationsDefaultsCard),
    ) {
        Text(
            stringResource(R.string.settings_notif_defaults_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.settings_notif_defaults_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_notif_defaults_lead_chips),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        // Manual flow row (Compose's FlowRow lives in Foundation; using a
        // simple wrapping Row here is fine for 6+1 chips).
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            COMMON_LEAD_OFFSETS.forEach { offset ->
                FilterChip(
                    selected = offset in selected,
                    onClick = {
                        selected = if (offset in selected) selected - offset else selected + offset
                        prefs.setDefaultLeadTimes(orderedOffsets(selected))
                    },
                    label = { Text(offset) },
                    modifier = Modifier
                        .testTag("$TestTagCatNotificationsDefaultsChipPrefix$offset"),
                )
            }
            // The "+ custom" chip pops a small dialog for arbitrary offsets.
            FilterChip(
                selected = selected.any { it !in COMMON_LEAD_OFFSETS },
                onClick = { customDialogOpen = true },
                label = { Text(stringResource(R.string.settings_notif_defaults_chip_custom)) },
                modifier = Modifier.testTag(TestTagCatNotificationsDefaultsCustomChip),
            )
        }
        // Show any persisted custom offsets as removable chips, so the user
        // can see + clear them.
        val customs = selected.filter { it !in COMMON_LEAD_OFFSETS }
        if (customs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                customs.forEach { c ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            selected = selected - c
                            prefs.setDefaultLeadTimes(orderedOffsets(selected))
                        },
                        label = { Text(c) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_notif_defaults_channel),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        // Channel dropdown.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag(TestTagCatNotificationsDefaultsChannelPicker),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val current = CHANNEL_OPTIONS.firstOrNull { it.id == channel } ?: CHANNEL_OPTIONS.first()
            TextButton(onClick = { channelMenuOpen = true }) {
                Text(stringResource(current.labelRes))
            }
            DropdownMenu(
                expanded = channelMenuOpen,
                onDismissRequest = { channelMenuOpen = false },
            ) {
                CHANNEL_OPTIONS.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(stringResource(opt.labelRes)) },
                        onClick = {
                            channel = opt.id
                            prefs.setDefaultChannel(opt.id)
                            channelMenuOpen = false
                        },
                    )
                }
            }
        }
    }

    if (customDialogOpen) {
        CustomOffsetDialog(
            onDismiss = { customDialogOpen = false },
            onAccept = { offset ->
                selected = selected + offset
                prefs.setDefaultLeadTimes(orderedOffsets(selected))
                customDialogOpen = false
            },
        )
    }
}

/**
 * Order offsets shortest-to-longest in the persisted list so the
 * downstream `setDefaultLeadTimes("15m;1h;1d")` shape stays stable.
 */
internal fun orderedOffsets(set: Set<String>): List<String> =
    set.sortedBy { offsetToMinutes(it) ?: Long.MAX_VALUE }

internal fun offsetToMinutes(s: String): Long? {
    if (s.length < 2) return null
    val n = s.dropLast(1).toLongOrNull() ?: return null
    return when (s.last()) {
        'm' -> n
        'h' -> n * 60
        'd' -> n * 60 * 24
        'w' -> n * 60 * 24 * 7
        else -> null
    }
}

@Composable
private fun CustomOffsetDialog(
    onDismiss: () -> Unit,
    onAccept: (String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf('m') }
    var unitMenuOpen by remember { mutableStateOf(false) }
    val units = listOf(
        'm' to R.string.settings_notif_defaults_custom_unit_minutes,
        'h' to R.string.settings_notif_defaults_custom_unit_hours,
        'd' to R.string.settings_notif_defaults_custom_unit_days,
        'w' to R.string.settings_notif_defaults_custom_unit_weeks,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notif_defaults_custom_dialog_title)) },
        text = {
            Column(modifier = Modifier.testTag(TestTagCatNotificationsDefaultsCustomDialog)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.settings_notif_defaults_custom_amount)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { unitMenuOpen = true }) {
                        Text(stringResource(units.first { it.first == unit }.second))
                    }
                    DropdownMenu(
                        expanded = unitMenuOpen,
                        onDismissRequest = { unitMenuOpen = false },
                    ) {
                        units.forEach { (ch, res) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(res)) },
                                onClick = { unit = ch; unitMenuOpen = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = amount.toIntOrNull()
                    if (a != null && a > 0) onAccept("${a}${unit}")
                },
                enabled = (amount.toIntOrNull() ?: 0) > 0,
            ) { Text(stringResource(R.string.settings_notif_defaults_custom_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_notif_defaults_custom_cancel))
            }
        },
    )
}

