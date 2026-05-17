package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.wizard.RoleId
import com.eight87.strictlykeptboy.ui.wizard.TemplateRegistry
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardDone
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft

@Composable
internal fun DoneScreen(draft: WizardDraft, onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardDone),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
    ) {
        Text(stringResource(R.string.wizard_done_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.wizard_done_blurb, draft.species.labelString()),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Live-ish preview
        val fallbackNowCard = stringResource(R.string.wizard_done_fallback_now_card_title)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_done_now_card_label), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.wizard_done_next_up, chooseFirstNowCardTitle(draft, fallbackNowCard)),
                    style = MaterialTheme.typography.titleMedium,
                )
                val praiseDefault = stringResource(R.string.wizard_identity_praise_default)
                val praise = draft.praiseTerms.firstOrNull() ?: praiseDefault
                Text(stringResource(R.string.wizard_done_praise_line, praise), style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.testTag("Wizard-Done-Open"),
        ) { Text(stringResource(R.string.wizard_done_open)) }
    }
}

private fun chooseFirstNowCardTitle(draft: WizardDraft, fallback: String): String {
    // Prefer a self-care or workout atom; falls back to first role/template.
    val ordered = listOf(RoleId.SelfCare, RoleId.Workout, RoleId.Work).filter { it in draft.roles }
    for (role in ordered) {
        val tmpls = TemplateRegistry.templatesFor(role)
        if (tmpls.isNotEmpty()) return tmpls.first().label
    }
    return fallback
}
