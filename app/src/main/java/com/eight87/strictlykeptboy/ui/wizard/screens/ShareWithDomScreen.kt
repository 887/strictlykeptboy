package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardShareWithDom
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft

@Composable
internal fun ShareWithDomScreen(
    draft: WizardDraft,
    onGenerateLink: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardShareWithDom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.wizard_share_with_dom_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.wizard_share_with_dom_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onGenerateLink,
            modifier = Modifier.testTag("Wizard-ShareWithDom-Generate"),
        ) {
            Text(stringResource(R.string.wizard_share_with_dom_generate))
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.testTag("Wizard-ShareWithDom-Skip"),
        ) {
            Text(stringResource(R.string.wizard_share_with_dom_skip))
        }
    }
}
