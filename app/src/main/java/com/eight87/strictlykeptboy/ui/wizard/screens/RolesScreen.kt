package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.wizard.RoleId
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardRoles
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft

@Composable
internal fun RolesScreen(
    draft: WizardDraft,
    neutralMode: Boolean,
    onUpdate: (WizardDraft) -> Unit,
) {
    val visibleRoles = RoleId.entries.filter { role ->
        !((neutralMode || draft.kinkOff) && role == RoleId.Kink)
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardRoles),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.wizard_roles_prompt), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wizard_roles_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        // Pinned self-care chip.
        Row(verticalAlignment = ComposeAlign.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.cd_wizard_roles_always_on))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.wizard_roles_selfcare_locked))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(520.dp),
        ) {
            items(visibleRoles) { role ->
                val isSelfCare = role == RoleId.SelfCare
                val on = role in draft.roles
                FilterChip(
                    selected = on,
                    onClick = {
                        if (isSelfCare) return@FilterChip // non-toggleable
                        val next = if (on) draft.roles - role else draft.roles + role
                        onUpdate(draft.copy(roles = next))
                    },
                    label = {
                        Row(verticalAlignment = ComposeAlign.CenterVertically) {
                            Text(role.emoji)
                            Spacer(Modifier.size(4.dp))
                            Text(role.labelString())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("Wizard-Role-${role.id}"),
                    enabled = !isSelfCare || on,
                )
            }
        }
    }
}
