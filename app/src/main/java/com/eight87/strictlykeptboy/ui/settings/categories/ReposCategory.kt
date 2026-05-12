package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.ui.import_export.ImportExportScreen
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState

const val TestTagCatRepos = "Cat-Repos"

/**
 * Phase S.1 — Repos category.
 *
 * Navigation surface into per-repo settings. We surface the existing
 * Import/Export screen here when state is wired through; the
 * RepoSettingsScreen per-repo flow continues to live where it always
 * lived (under [com.eight87.strictlykeptboy.ui.repos]), reachable from
 * the dedicated Repos top-destination.
 */
@Composable
fun ReposCategory(
    importExportState: ImportExportViewState?,
    onPickImportFile: (RepoConfig) -> Unit,
    onPickExportFile: (RepoConfig) -> Unit,
    onOpenReposList: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CategorySurface(
        testTag = TestTagCatRepos,
        title = stringResource(R.string.settings_category_repos),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.settings_repos_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenReposList,
            modifier = Modifier.testTag("$TestTagCatRepos-OpenList"),
        ) {
            Text(stringResource(R.string.settings_repos_open_full))
        }
        Spacer(Modifier.height(16.dp))
        if (importExportState != null) {
            ImportExportScreen(
                state = importExportState,
                onPickImportFile = onPickImportFile,
                onPickExportFile = onPickExportFile,
            )
        }
    }
}
