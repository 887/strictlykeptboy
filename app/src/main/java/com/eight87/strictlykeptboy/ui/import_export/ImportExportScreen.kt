package com.eight87.strictlykeptboy.ui.import_export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig

const val TestTagImportExportScreen = "ImportExportScreen"
const val TestTagImportButtonPrefix = "ImportBtn-"
const val TestTagExportButtonPrefix = "ExportBtn-"
const val TestTagImportPreviewSheet = "ImportPreviewSheet"
const val TestTagImportPreviewConfirm = "ImportPreviewConfirm"
const val TestTagImportPreviewCancel = "ImportPreviewCancel"

/**
 * Phase P.3 + P.4 — Per-calendar import / export entry point.
 *
 * **R.X.1 narrow interface:** takes a small [ImportExportViewState]
 * port, not the whole `RepoStore`. **R.X.3 composition root:** concrete
 * SAF launchers + EntityWriter wiring lives in MainActivity which
 * passes callbacks down.
 */
@Composable
fun ImportExportScreen(
    state: ImportExportViewState,
    onPickImportFile: (RepoConfig) -> Unit,
    onPickExportFile: (RepoConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repos by state.repos.collectAsState()
    val preview by state.pendingPreview.collectAsState()

    Box(modifier = modifier.fillMaxSize().testTag(TestTagImportExportScreen).padding(16.dp)) {
        if (repos.isEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.import_export_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.import_export_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.import_export_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.import_export_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(repos, key = { it.repoId }) { repo ->
                        RepoRow(
                            repo = repo,
                            onImport = { onPickImportFile(repo) },
                            onExport = { onPickExportFile(repo) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        if (preview != null) {
            ImportPreviewSheet(
                preview = preview!!,
                onConfirm = { state.confirmPreview() },
                onCancel = { state.cancelPreview() },
            )
        }
    }
}

@Composable
private fun RepoRow(
    repo: RepoConfig,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(repo.displayName, style = MaterialTheme.typography.titleMedium)
            Text(repo.repoId, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.testTag("$TestTagImportButtonPrefix${repo.repoId}"),
        ) { Text(stringResource(R.string.import_action)) }
        Button(
            onClick = onExport,
            modifier = Modifier.testTag("$TestTagExportButtonPrefix${repo.repoId}"),
        ) { Text(stringResource(R.string.export_action)) }
    }
}
