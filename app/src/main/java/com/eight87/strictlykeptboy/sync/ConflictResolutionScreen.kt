package com.eight87.strictlykeptboy.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

const val TestTagConflictScreen = "ConflictResolutionScreen"
const val TestTagKeepMineAll = "ConflictKeepMineAll"
const val TestTagKeepTheirsAll = "ConflictKeepTheirsAll"
const val TestTagAbortRebase = "ConflictAbortRebase"

/**
 * Phase J.6 — full-screen conflict-resolution modal. Minimal v1 surface:
 * file list on the left, raw 3-way text view on the right, per-file
 * keep-mine / keep-theirs / manual-text overlay. Structured TOML field-
 * by-field merge is deferred to a later phase (out of v1 scope).
 *
 * Per ZZ.F the "theirs" column carries the primary remote's display label
 * — passed through [ConflictResolutionViewModel.remoteLabel].
 */
@Composable
fun ConflictResolutionScreen(
    vm: ConflictResolutionViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    if (state.resolved) {
        Surface(modifier = modifier.fillMaxSize().testTag(TestTagConflictScreen)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Conflicts resolved.", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone) { Text("Done") }
            }
        }
        return
    }

    Surface(modifier = modifier.fillMaxSize().testTag(TestTagConflictScreen)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Conflicts — ${vm.remoteLabel}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { scope.launch { vm.keepMineAll() } },
                    modifier = Modifier.testTag(TestTagKeepMineAll),
                ) { Text("Keep mine (all)") }
                Spacer(Modifier.height(0.dp))
                OutlinedButton(
                    onClick = { scope.launch { vm.keepTheirsAll() } },
                    modifier = Modifier.padding(start = 8.dp).testTag(TestTagKeepTheirsAll),
                ) { Text("Keep theirs (all)") }
                TextButton(
                    onClick = { scope.launch { vm.abort() } },
                    modifier = Modifier.padding(start = 8.dp).testTag(TestTagAbortRebase),
                ) { Text("Abort rebase") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.files) { file ->
                        val isSelected = state.files[state.currentIndex] === file
                        TextButton(onClick = { vm.selectFile(state.files.indexOf(file)) }) {
                            Text(
                                (if (isSelected) "▸ " else "  ") + file.path,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                val current = state.files.getOrNull(state.currentIndex)
                if (current != null) {
                    Column(
                        modifier = Modifier.weight(2f).verticalScroll(rememberScrollState()),
                    ) {
                        Text("Mine", style = MaterialTheme.typography.titleSmall)
                        Text(current.oursContent, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(vm.remoteLabel, style = MaterialTheme.typography.titleSmall)
                        Text(current.theirsContent, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
