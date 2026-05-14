package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.ui.import_export.ImportExportScreen
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState
import kotlinx.coroutines.flow.StateFlow

const val TestTagCatRepos = "Cat-Repos"
const val TestTagCatReposRowPrefix = "Cat-Repos-Row-"
const val TestTagCatReposImportExport = "Cat-Repos-ImportExport"

/**
 * Round 2.2.D.2 — Accounts (Repos) category.
 *
 * In-pane list of configured repos. Tapping a row pushes the per-repo
 * [com.eight87.strictlykeptboy.ui.repos.RepoSettingsScreen] into the
 * Settings detail pane on tablet or as a full-screen route on phone
 * (the caller wires [onOpenRepo] to its own push mechanism).
 *
 * The Import / Export surface is rendered as a sub-section card below
 * the repo rows when [importExportState] is wired.
 */
@Composable
fun ReposCategory(
    reposFlow: StateFlow<List<RepoConfig>>?,
    importExportState: ImportExportViewState?,
    onPickImportFile: (RepoConfig) -> Unit,
    onPickExportFile: (RepoConfig) -> Unit,
    onOpenRepo: (RepoConfig) -> Unit = {},
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
        Spacer(Modifier.height(12.dp))

        SectionLabel(stringResource(R.string.settings_repos_inline_header))
        val repos = reposFlow?.collectAsState()?.value ?: emptyList()
        if (repos.isEmpty()) {
            Text(
                stringResource(R.string.settings_repos_inline_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            repos.forEachIndexed { idx, cfg ->
                RepoRow(cfg = cfg, onClick = { onOpenRepo(cfg) })
                if (idx < repos.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }

        if (importExportState != null) {
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_repos_import_export_header))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagCatReposImportExport),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(8.dp)) {
                    ImportExportScreen(
                        state = importExportState,
                        onPickImportFile = onPickImportFile,
                        onPickExportFile = onPickExportFile,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoRow(cfg: RepoConfig, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$TestTagCatReposRowPrefix${cfg.repoId}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    cfg.displayName.ifBlank { cfg.repoId },
                    style = MaterialTheme.typography.titleSmall,
                )
                val remote = cfg.remotes.firstOrNull()?.url
                Text(
                    text = remote ?: cfg.repoId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.settings_repos_row_open_a11y),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
