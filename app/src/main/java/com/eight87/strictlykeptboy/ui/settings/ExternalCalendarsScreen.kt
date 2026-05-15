package com.eight87.strictlykeptboy.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.system.SystemCalendar
import com.eight87.strictlykeptboy.system.SystemCalendarAppDetector
import com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

const val TestTagExternalCalendarsScreen = "ExternalCalendarsScreen"
const val TestTagExternalCalendarsShowSwitch = "ExternalCalendars-Show"
const val TestTagExternalCalendarsAllowEditSwitch = "ExternalCalendars-AllowEdit"
const val TestTagExternalCalendarsCalendarRow = "ExternalCalendars-Cal-"
/** Round 2.18.C.6 — permission-needed empty state container. */
const val TestTagExternalCalendarsPermissionNeeded = "ExternalCalendars-PermissionNeeded"
const val TestTagExternalCalendarsGrantButton = "ExternalCalendars-Grant"
const val TestTagExternalCalendarsOpenSettingsButton = "ExternalCalendars-OpenSettings"
/** Round 2.18.F.7 — Settings → "Suppress system notifications" section. */
const val TestTagExternalCalendarsSuppressSection = "ExternalCalendars-SuppressSection"
const val TestTagExternalCalendarsSuppressRow = "ExternalCalendars-SuppressRow-"

/**
 * Round 2.18.B.2/B.4/B.5 — External (CalendarContract) calendars
 * settings screen.
 *
 * Top-level toggle ("Show system calendars in strictlykeptboy") flips
 * [SystemCalendarPrefsStore.setShowSystemCalendars]. Turning the toggle
 * on requests [Manifest.permission.READ_CALENDAR] via the Activity
 * Result API — if the user denies, the toggle reverts visually and a
 * snackbar explains.
 *
 * Secondary toggle ("Allow editing system calendars") is gated on the
 * top-level (disabled when top-level is off). Flipping it on requests
 * [Manifest.permission.WRITE_CALENDAR]; denied → revert + snackbar.
 * This toggle gates the future Phase D write path; B.2 only wires the
 * permission.
 *
 * Per-calendar visibility list (B.5) groups every `SystemCalendar` by
 * `(accountType, accountName)` and renders a Material3 `Switch` per
 * row (per Round 2.19's switch-not-checkbox convention) that writes
 * the visibility flag into [SystemCalendarPrefsStore.setVisible].
 */
@Composable
fun ExternalCalendarsScreen(
    prefs: SystemCalendarPrefsStore,
    systemCalendarsFlow: StateFlow<List<SystemCalendar>>,
    modifier: Modifier = Modifier,
    /**
     * Round 2.18.F.7 — test seam. Tests can pass a fixed candidate list
     * to bypass `PackageManager` lookup.
     */
    detectInstalledCalendarApps: (android.content.Context) -> List<SystemCalendarAppDetector.InstalledCandidate> =
        { ctx -> SystemCalendarAppDetector.detectInstalled(ctx) },
) {
    val ctx = LocalContext.current
    val global by prefs.globalState.collectAsState()
    val overrides by prefs.state.collectAsState()
    val systemCalendars by systemCalendarsFlow.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Track what the user just attempted so we know how to respond to
    // the permission result. `null` = no pending request.
    var pendingShowGrant by remember { mutableStateOf(false) }
    var pendingAllowEditGrant by remember { mutableStateOf(false) }

    val deniedReadCalendar = stringResource(R.string.settings_external_calendars_permission_denied)
    val deniedWriteCalendar = stringResource(R.string.settings_external_calendars_write_permission_denied)

    val readLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            prefs.setShowSystemCalendars(true)
        } else {
            // Revert — explicit false so the switch UI snaps back.
            prefs.setShowSystemCalendars(false)
            scope.launch { snackbarHost.showSnackbar(deniedReadCalendar) }
        }
        pendingShowGrant = false
    }
    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            prefs.setAllowEditing(true)
        } else {
            prefs.setAllowEditing(false)
            scope.launch { snackbarHost.showSnackbar(deniedWriteCalendar) }
        }
        pendingAllowEditGrant = false
    }

    Box(modifier = modifier.fillMaxSize().testTag(TestTagExternalCalendarsScreen)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.settings_category_external_calendars),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_external_calendars_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            SwitchRow(
                label = stringResource(R.string.settings_external_calendars_show_label),
                checked = global.showSystemCalendars,
                onCheckedChange = { wantOn ->
                    if (wantOn) {
                        val already = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.READ_CALENDAR,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (already) {
                            prefs.setShowSystemCalendars(true)
                        } else {
                            pendingShowGrant = true
                            readLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    } else {
                        prefs.setShowSystemCalendars(false)
                        // Turning the top-level off also disables the
                        // secondary write toggle to keep state coherent.
                        if (global.allowEditing) prefs.setAllowEditing(false)
                    }
                },
                enabled = true,
                testTag = TestTagExternalCalendarsShowSwitch,
            )

            SwitchRow(
                label = stringResource(R.string.settings_external_calendars_allow_edit_label),
                helper = stringResource(R.string.settings_external_calendars_allow_edit_helper),
                checked = global.allowEditing,
                onCheckedChange = { wantOn ->
                    if (wantOn) {
                        val already = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.WRITE_CALENDAR,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (already) {
                            prefs.setAllowEditing(true)
                        } else {
                            pendingAllowEditGrant = true
                            writeLauncher.launch(Manifest.permission.WRITE_CALENDAR)
                        }
                    } else {
                        prefs.setAllowEditing(false)
                    }
                },
                enabled = global.showSystemCalendars,
                testTag = TestTagExternalCalendarsAllowEditSwitch,
            )

            // Round 2.18.C.6 — permission re-check at render time. If the
            // user revoked READ_CALENDAR via system settings while we
            // weren't looking, surface a [Grant] / [Open Settings] block.
            val hasReadCalendar = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
            if (global.showSystemCalendars && !hasReadCalendar) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTagExternalCalendarsPermissionNeeded),
                ) {
                    Text(
                        text = stringResource(R.string.settings_external_calendars_permission_required_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_external_calendars_permission_required_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                pendingShowGrant = true
                                readLauncher.launch(Manifest.permission.READ_CALENDAR)
                            },
                            modifier = Modifier.testTag(TestTagExternalCalendarsGrantButton),
                        ) {
                            Text(stringResource(R.string.settings_external_calendars_permission_grant))
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", ctx.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { ctx.startActivity(intent) }
                            },
                            modifier = Modifier.testTag(TestTagExternalCalendarsOpenSettingsButton),
                        ) {
                            Text(stringResource(R.string.settings_external_calendars_open_system_settings))
                        }
                    }
                }
            }

            if (global.showSystemCalendars && hasReadCalendar) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.settings_external_calendars_per_calendar_header),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (systemCalendars.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_external_calendars_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Group by (accountType, accountName). Sorted by
                    // account name for stable rendering.
                    val grouped = systemCalendars
                        .groupBy { it.accountType to it.accountName }
                        .toSortedMap(compareBy({ it.second }, { it.first }))
                    grouped.forEach { (account, cals) ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                R.string.settings_external_calendars_account_label,
                                account.second,
                                account.first,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        cals.sortedBy { it.displayName }.forEach { cal ->
                            val key = SystemCalendarPrefsStore.keyFor(
                                cal.accountType, cal.accountName, cal.id,
                            )
                            val visible = overrides[key]?.visible ?: true
                            SwitchRow(
                                label = cal.displayName,
                                checked = visible,
                                onCheckedChange = { v ->
                                    prefs.setVisible(
                                        cal.accountType, cal.accountName, cal.id, v,
                                    )
                                },
                                testTag = TestTagExternalCalendarsCalendarRow + cal.id,
                            )
                        }
                    }
                }
            }
            // Round 2.18.F.7 — Suppress system calendar notifications.
            // List the candidate calendar apps installed on the device;
            // tapping a row opens the OS notification settings for that
            // package so the user can mute it.
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagExternalCalendarsSuppressSection),
            ) {
                Text(
                    stringResource(R.string.settings_external_calendars_suppress_header),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_external_calendars_suppress_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val installed = remember(ctx) { detectInstalledCalendarApps(ctx) }
                if (installed.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_external_calendars_suppress_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    installed.forEach { cand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag(TestTagExternalCalendarsSuppressRow + cand.packageName),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    cand.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    cand.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = {
                                val intent = SystemCalendarAppDetector
                                    .appNotificationSettingsIntent(cand.packageName)
                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                runCatching { ctx.startActivity(intent) }
                            }) {
                                Text(stringResource(R.string.settings_external_calendars_suppress_row_action))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(64.dp))
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        ) { data -> Snackbar(snackbarData = data) }
    }

    // Silence unused-warning around pendingX flags — they're observable
    // by tests that want to confirm a permission request was launched.
    LaunchedEffect(pendingShowGrant, pendingAllowEditGrant) { /* no-op */ }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (helper != null) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        )
    }
}
