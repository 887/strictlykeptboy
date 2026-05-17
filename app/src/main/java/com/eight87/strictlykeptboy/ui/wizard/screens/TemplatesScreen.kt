package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.wizard.RoleId
import com.eight87.strictlykeptboy.ui.wizard.TemplateRegistry
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardTemplates
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft
import com.eight87.strictlykeptboy.ui.wizard.WrappingChipRow

@Composable
internal fun TemplatesScreen(
    draft: WizardDraft,
    neutralMode: Boolean,
    onUpdate: (WizardDraft) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardTemplates),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.wizard_templates_prompt),
            style = MaterialTheme.typography.titleMedium,
        )
        val orderedRoles = RoleId.entries.filter { it in draft.roles }
        for (role in orderedRoles) {
            val templates = TemplateRegistry.visibleTemplatesFor(
                role = role,
                neutralMode = neutralMode || draft.kinkOff,
            )
            if (templates.isEmpty()) continue
            Card(modifier = Modifier.fillMaxWidth().testTag("Wizard-TemplateGroup-${role.id}")) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        stringResource(R.string.wizard_templates_role_label, role.emoji, role.labelString()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    WrappingChipRow {
                        templates.forEach { t ->
                            val enabledSet = draft.enabledTemplates[role]
                            // Default: all on if no explicit set yet (smart-defaults).
                            val on = enabledSet?.contains(t.atomId) ?: true
                            FilterChip(
                                selected = on,
                                onClick = {
                                    val current = enabledSet
                                        ?: templates.map { it.atomId }.toSet()
                                    val next = if (on) current - t.atomId else current + t.atomId
                                    val merged = draft.enabledTemplates + (role to next)
                                    onUpdate(draft.copy(enabledTemplates = merged))
                                },
                                label = { Text(t.label) },
                                modifier = Modifier.testTag("Wizard-Template-${role.id}-${t.atomId}"),
                            )
                        }
                    }
                }
            }
        }
    }
}
