package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.wizard.ScaffoldProgress
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardScaffold

@Composable
internal fun ScaffoldScreen(progress: ScaffoldProgress, error: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardScaffold),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
    ) {
        Text(
            when (progress) {
                ScaffoldProgress.Idle -> stringResource(R.string.wizard_scaffold_idle)
                ScaffoldProgress.Running -> stringResource(R.string.wizard_scaffold_running)
                ScaffoldProgress.Done -> stringResource(R.string.wizard_scaffold_done)
                ScaffoldProgress.Failed -> stringResource(R.string.wizard_scaffold_failed)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (progress == ScaffoldProgress.Running || progress == ScaffoldProgress.Idle) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        val displayError = error ?: if (progress == ScaffoldProgress.Failed) stringResource(R.string.wizard_scaffold_unknown_error) else null
        if (displayError != null) {
            Text(displayError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
