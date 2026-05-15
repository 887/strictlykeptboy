package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import com.eight87.strictlykeptboy.prefs.ParentLocation
import com.eight87.strictlykeptboy.prefs.RepoStoragePrefs

const val TestTagCatStorage = "Cat-Storage"
const val TestTagCatStorageFolderRow = "Cat-Storage-FolderRow"
const val TestTagCatStorageAdoptRow = "Cat-Storage-AdoptRow"
const val TestTagCatStorageBackupRestoreRow = "Cat-Storage-BackupRestoreRow"

/**
 * Round 2.17 Phase E.2 — Settings → Storage category.
 *
 * Renamed from the 2.7.D `BackupLocationCategory` compile-shim via
 * `git mv` (D-2.17.l preserves history). The new surface owns three
 * rows:
 *  - **Storage folder** — current parent label, tap → [StorageFolderScreen].
 *  - **Adopt existing folder** — picks another folder and lists its
 *    discovered repos via `ParentReconciler.reconcileExternal` before
 *    committing to a parent switch.
 *  - **Backup / Restore** — opens the placeholder Backup/Restore screen;
 *    Phase F (export) + Phase G (restore) fill in the real actions.
 */
@Composable
fun StorageCategory(
    prefs: RepoStoragePrefs,
    onOpenStorageFolder: () -> Unit,
    onAdoptExistingFolder: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    val currentLabel = when (val loc = state) {
        null -> stringResource(R.string.settings_storage_folder_subtitle_none)
        is ParentLocation.Internal -> stringResource(R.string.settings_storage_folder_subtitle_internal)
        is ParentLocation.External -> loc.label
    }
    CategorySurface(
        testTag = TestTagCatStorage,
        title = stringResource(R.string.settings_category_storage),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.settings_storage_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_storage_folder_title)) },
            supportingContent = { Text(currentLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagCatStorageFolderRow)
                .clickable(onClick = onOpenStorageFolder),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_storage_adopt_title)) },
            supportingContent = { Text(stringResource(R.string.settings_storage_adopt_subtitle)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagCatStorageAdoptRow)
                .clickable(onClick = onAdoptExistingFolder),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_storage_backuprestore_title)) },
            supportingContent = { Text(stringResource(R.string.settings_storage_backuprestore_subtitle)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagCatStorageBackupRestoreRow)
                .clickable(onClick = onOpenBackupRestore),
        )
    }
}
