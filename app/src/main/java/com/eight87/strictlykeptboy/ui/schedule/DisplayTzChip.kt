package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import java.time.ZoneId

const val TestTagDisplayTzChip = "DisplayTzChip"
const val TestTagDisplayTzPicker = "DisplayTzPicker"
const val TestTagDisplayTzOptionSystem = "DisplayTzOption-System"
const val TestTagDisplayTzOptionRepo = "DisplayTzOption-Repo"

/**
 * Round 2.24 Phase C.2 (D-2.24.c) — small assist chip that surfaces the
 * active display-tz and opens a picker on tap.
 *
 * Label cycles between three states:
 *  - `displayTzId == null`            → "System (Europe/Berlin)"
 *  - `displayTzId == repoDefaultTzId` → "Repo default (America/New_York)"
 *  - everything else                  → "Custom: <zoneId>"
 *
 * Pure UI — host wires [onSelect] to
 * `CalendarVisibilityPrefs.setDisplayTzId(...)`.
 */
@Composable
fun DisplayTzChip(
    displayTzId: String?,
    repoDefaultTzId: String?,
    systemTzId: String = ZoneId.systemDefault().id,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val label = displayTzChipLabel(
        displayTzId = displayTzId,
        repoDefaultTzId = repoDefaultTzId,
        systemTzId = systemTzId,
    )
    AssistChip(
        onClick = { picking = true },
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                modifier = Modifier.padding(2.dp),
            )
        },
        modifier = modifier.testTag(TestTagDisplayTzChip),
        colors = AssistChipDefaults.assistChipColors(),
    )
    if (picking) {
        DisplayTzPicker(
            current = displayTzId,
            repoDefaultTzId = repoDefaultTzId,
            systemTzId = systemTzId,
            onDismiss = { picking = false },
            onPick = {
                onSelect(it)
                picking = false
            },
        )
    }
}

@Composable
private fun DisplayTzPicker(
    current: String?,
    repoDefaultTzId: String?,
    systemTzId: String,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Display timezone") },
        text = {
            var query by remember { mutableStateOf("") }
            val all = remember {
                ZoneId.getAvailableZoneIds().toList().sorted()
            }
            val filtered = remember(query) {
                val q = query.trim()
                if (q.isEmpty()) all else all.filter { it.contains(q, ignoreCase = true) }
            }
            Column(modifier = Modifier.testTag(TestTagDisplayTzPicker)) {
                Surface(
                    onClick = { onPick(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTagDisplayTzOptionSystem),
                ) {
                    Text(
                        text = "(use system: $systemTzId)",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (repoDefaultTzId != null) {
                    Surface(
                        onClick = { onPick(repoDefaultTzId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTagDisplayTzOptionRepo),
                    ) {
                        Text(
                            text = "Repo default: $repoDefaultTzId",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Filter zones") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(filtered) { zid ->
                        Surface(
                            onClick = { onPick(zid) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Text(
                                    text = zid,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (zid == current) {
                                    Box(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "✓",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/**
 * Pure label formatter (testable without Compose). See [DisplayTzChip]
 * doc for the three branches.
 */
fun displayTzChipLabel(
    displayTzId: String?,
    repoDefaultTzId: String?,
    systemTzId: String,
): String = when {
    displayTzId == null -> "System ($systemTzId)"
    repoDefaultTzId != null && displayTzId == repoDefaultTzId ->
        "Repo default ($repoDefaultTzId)"
    else -> "Custom: $displayTzId"
}
