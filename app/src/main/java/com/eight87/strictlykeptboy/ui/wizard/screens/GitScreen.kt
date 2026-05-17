package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.wizard.GitChoice
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardGit
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft

@Composable
internal fun GitScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardGit),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.wizard_git_prompt), style = MaterialTheme.typography.titleMedium)

        // Phone-only (default, recommended)
        Card(
            onClick = { onUpdate(draft.copy(gitChoice = GitChoice.PhoneOnly)) },
            colors = if (draft.gitChoice is GitChoice.PhoneOnly) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CardDefaults.cardColors(),
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-PhoneOnly"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_git_phone_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wizard_git_phone_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Self-hosted Forgejo
        val selfHosted = draft.gitChoice as? GitChoice.SelfHostedForgejo
        Card(
            onClick = {
                onUpdate(draft.copy(gitChoice = GitChoice.SelfHostedForgejo("", "")))
            },
            colors = if (selfHosted != null) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CardDefaults.cardColors(),
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_git_forgejo_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wizard_git_forgejo_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (selfHosted != null) {
                    OutlinedTextField(
                        value = selfHosted.instanceUrl,
                        onValueChange = {
                            onUpdate(draft.copy(gitChoice = selfHosted.copy(instanceUrl = it)))
                        },
                        label = { Text(stringResource(R.string.wizard_git_forgejo_instance_url)) },
                        modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo-Url"),
                    )
                    OutlinedTextField(
                        value = selfHosted.oauthClientId,
                        onValueChange = {
                            onUpdate(draft.copy(gitChoice = selfHosted.copy(oauthClientId = it)))
                        },
                        label = { Text(stringResource(R.string.wizard_git_forgejo_oauth_client_id)) },
                        modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo-Client"),
                    )
                    Text(
                        stringResource(R.string.wizard_git_forgejo_oauth_note),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // GitHub
        Card(
            onClick = { onUpdate(draft.copy(gitChoice = GitChoice.GitHub())) },
            colors = if (draft.gitChoice is GitChoice.GitHub) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CardDefaults.cardColors(),
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-GitHub"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_git_github_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wizard_git_github_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (draft.gitChoice is GitChoice.GitHub) {
                    Text(
                        stringResource(R.string.wizard_git_github_oauth_note),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.displayName,
            onValueChange = { onUpdate(draft.copy(displayName = it)) },
            label = { Text(stringResource(R.string.wizard_git_calendar_repo_name)) },
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Name"),
        )
    }
}
