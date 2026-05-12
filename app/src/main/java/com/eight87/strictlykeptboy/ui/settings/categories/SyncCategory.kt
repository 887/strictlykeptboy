package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.sync.SyncStatusStore
import com.eight87.strictlykeptboy.ui.settings.ConflictPolicy
import com.eight87.strictlykeptboy.ui.settings.PushPolicy
import com.eight87.strictlykeptboy.ui.settings.SyncSettingsPrefs

const val TestTagCatSync = "Cat-Sync"

/**
 * Phase S.3 — global sync settings.
 *
 * R.X.1: takes only the narrow [SyncSettingsPrefs] handle + a status
 * store for backoff visibility. No god-state.
 */
@Composable
fun SyncCategory(
    prefs: SyncSettingsPrefs,
    statusStore: SyncStatusStore?,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    CategorySurface(
        testTag = TestTagCatSync,
        title = stringResource(R.string.settings_category_sync),
        modifier = modifier,
    ) {
        SectionLabel(stringResource(R.string.settings_sync_default_interval))
        IntervalRow(state.defaultIntervalMinutes) { prefs.setInterval(it) }

        ToggleRow(
            label = stringResource(R.string.settings_sync_wifi_only),
            checked = state.wifiOnly,
            onCheckedChange = { prefs.setWifiOnly(it) },
            testTag = "$TestTagCatSync-WifiOnly",
        )

        SectionLabel(stringResource(R.string.settings_sync_push_policy))
        PushPolicyRow(state.pushPolicy) { prefs.setPushPolicy(it) }

        SectionLabel(stringResource(R.string.settings_sync_conflict_policy))
        ConflictPolicyRow(state.conflictPolicy) { prefs.setConflictPolicy(it) }

        SectionLabel(stringResource(R.string.settings_sync_backoff_header))
        val backoffActive = statusStore
            ?.state
            ?.collectAsState()
            ?.value
            ?.values
            ?.count { snap ->
                snap.perRemote.values.any { it.lastErrorMessage != null }
            } ?: 0
        Text(
            if (backoffActive == 0) {
                stringResource(R.string.settings_sync_backoff_idle)
            } else {
                stringResource(R.string.settings_sync_backoff_active, backoffActive)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IntervalRow(current: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        0 to R.string.settings_sync_interval_manual,
        5 to R.string.settings_sync_interval_5m,
        15 to R.string.settings_sync_interval_15m,
        30 to R.string.settings_sync_interval_30m,
        60 to R.string.settings_sync_interval_60m,
        240 to R.string.settings_sync_interval_240m,
    )
    Row {
        options.forEach { (minutes, label) ->
            FilterChip(
                selected = current == minutes,
                onClick = { onSelect(minutes) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatSync-Interval-$minutes"),
            )
        }
    }
}

@Composable
private fun PushPolicyRow(current: PushPolicy, onSelect: (PushPolicy) -> Unit) {
    Row {
        val items = listOf(
            PushPolicy.Push to R.string.settings_sync_push_immediate,
            PushPolicy.PushLazy to R.string.settings_sync_push_lazy,
            PushPolicy.Manual to R.string.settings_sync_push_manual,
        )
        items.forEach { (policy, label) ->
            FilterChip(
                selected = current == policy,
                onClick = { onSelect(policy) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatSync-Push-${policy.name}"),
            )
        }
    }
}

@Composable
private fun ConflictPolicyRow(current: ConflictPolicy, onSelect: (ConflictPolicy) -> Unit) {
    Row {
        val items = listOf(
            ConflictPolicy.AutoRebase to R.string.settings_sync_conflict_auto,
            ConflictPolicy.ManualOnly to R.string.settings_sync_conflict_manual,
        )
        items.forEach { (policy, label) ->
            FilterChip(
                selected = current == policy,
                onClick = { onSelect(policy) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatSync-Conflict-${policy.name}"),
            )
        }
    }
}
