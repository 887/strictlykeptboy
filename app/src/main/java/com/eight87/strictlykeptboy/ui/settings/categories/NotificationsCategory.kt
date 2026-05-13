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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.eight87.strictlykeptboy.notif.NotificationChannels
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass

const val TestTagCatNotifications = "Cat-Notifications"

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
fun NotificationsCategory(prefs: NotificationPrefs, modifier: Modifier = Modifier) {
    val widthClass = LocalWindowWidthSizeClass.current
    // Phase 2.1.H.5 — only Expanded (≥840dp) splits into two columns;
    // Medium tablets in portrait keep the single-column flow because the
    // SettingsPane already consumes the master pane at that breakpoint
    // (the category's *own* sub-layout would force three columns).
    if (widthClass is WindowWidthSizeClass.Expanded) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag(TestTagCatNotifications),
        ) {
            // Left — per-channel rows.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag(TestTagCatNotificationsChannelsPane),
            ) {
                NotificationsChannelsBlock(prefs)
            }
            HorizontalDividerVertical()
            // Right — lead times + briefings master.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag(TestTagCatNotificationsLeadsPane),
            ) {
                NotificationsLeadsBlock(prefs)
            }
        }
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(TestTagCatNotifications),
    ) {
        NotificationsLeadsBlock(prefs)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        NotificationsChannelsBlock(prefs)
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
