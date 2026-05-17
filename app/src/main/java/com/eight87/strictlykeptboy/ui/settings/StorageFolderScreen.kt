package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.prefs.ParentLocation
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs
import kotlinx.coroutines.flow.StateFlow

const val TestTagStorageFolderScreen = "StorageFolderScreen"
const val TestTagStorageFolderChange = "StorageFolderScreen-Change"
const val TestTagStorageFolderSwitchInternal = "StorageFolderScreen-SwitchInternal"

/**
 * Round 2.17 Phase E.3 — Settings → Storage → Storage folder screen.
 *
 * Shows the current parent location (kind + label + on-disk path) and
 * the repo count under it, with two buttons:
 *  - **Change folder** — fires the SAF parent picker via [onChangeFolder].
 *  - **Switch to internal** — flips back to `ParentLocation.Internal`.
 *
 * Both paths trigger the move-job (Phase E.5) at the caller. This
 * screen is presentation-only; it does not own the picker launcher or
 * the prefs write.
 */
@Composable
fun StorageFolderScreen(
    prefs: RepoStoragePrefs,
    reposFlow: StateFlow<List<RepoConfig>>,
    onChangeFolder: () -> Unit,
    onSwitchToInternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    val repos by reposFlow.collectAsState()
    val parentPath = when (val loc = state) {
        null -> null
        is ParentLocation.Internal -> loc.absPath
        is ParentLocation.External -> loc.cachedRealPath ?: loc.treeUri
    }
    val repoCount = if (parentPath == null) 0 else repos.count {
        java.io.File(it.rootDir).absolutePath.startsWith(parentPath)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(TestTagStorageFolderScreen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.storage_folder_screen_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        when (val loc = state) {
            null -> Text(
                stringResource(R.string.storage_folder_kind_none),
                style = MaterialTheme.typography.bodyMedium,
            )
            is ParentLocation.Internal -> Text(
                stringResource(R.string.storage_folder_kind_internal),
                style = MaterialTheme.typography.titleSmall,
            )
            is ParentLocation.External -> Text(
                stringResource(R.string.storage_folder_kind_external, loc.label),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (parentPath != null) {
            Text(
                stringResource(R.string.storage_folder_path_label, parentPath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                pluralStringResource(R.plurals.storage_folder_repo_count, repoCount, repoCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onChangeFolder,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagStorageFolderChange),
        ) { Text(stringResource(R.string.storage_folder_change_button)) }
        // Only show "Switch to internal" when currently external.
        if (state is ParentLocation.External) {
            OutlinedButton(
                onClick = onSwitchToInternal,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagStorageFolderSwitchInternal),
            ) { Text(stringResource(R.string.storage_folder_switch_internal_button)) }
        }
    }
}
