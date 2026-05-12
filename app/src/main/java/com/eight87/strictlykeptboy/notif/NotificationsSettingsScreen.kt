package com.eight87.strictlykeptboy.notif

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

/**
 * Phase M.3 — minimal settings surface for per-channel enable + silent.
 *
 * R.X.7 — leaf takes only the [NotificationPrefs] handle (one
 * narrow data object), not a god-state.
 */
@Composable
fun NotificationsSettingsScreen(prefs: NotificationPrefs, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_notifications_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        ChannelRow(prefs, NotificationChannels.EVENTS, R.string.notif_channel_events_name)
        ChannelRow(prefs, NotificationChannels.TASKS, R.string.notif_channel_tasks_name)
        ChannelRow(prefs, NotificationChannels.BRIEFINGS, R.string.notif_channel_briefings_name)
        ChannelRow(prefs, NotificationChannels.SYNC, R.string.notif_channel_sync_name)
        ChannelRow(prefs, NotificationChannels.ERRORS, R.string.notif_channel_errors_name)
        ChannelRow(prefs, NotificationChannels.FOREGROUND, R.string.notif_channel_foreground_name)
    }
}

@Composable
private fun ChannelRow(
    prefs: NotificationPrefs,
    channelId: String,
    nameRes: Int,
) {
    var enabled by remember { mutableStateOf(prefs.isChannelEnabled(channelId)) }
    var silent by remember { mutableStateOf(prefs.isChannelSilent(channelId)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(nameRes), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        ToggleRow(
            label = stringResource(R.string.settings_notif_channel_enabled),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                prefs.setChannelEnabled(channelId, it)
            },
        )
        ToggleRow(
            label = stringResource(R.string.settings_notif_channel_silent),
            checked = silent,
            onCheckedChange = {
                silent = it
                prefs.setChannelSilent(channelId, it)
            },
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
