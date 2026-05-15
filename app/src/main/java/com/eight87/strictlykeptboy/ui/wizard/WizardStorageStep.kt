package com.eight87.strictlykeptboy.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

/** Test tag for the Round 2.17 Phase D wizard storage step. */
const val TestTagWizardStorage: String = "Wizard-Storage"
const val TestTagWizardStorageExternal: String = "Wizard-Storage-External"
const val TestTagWizardStorageInternal: String = "Wizard-Storage-Internal"

/**
 * Round 2.17 Phase D — "Where should your stuff live?" wizard step.
 *
 * Two big tappable cards, no pre-selected default — the user MUST pick
 * one before scaffolding can proceed (per Phase A's
 * `ParentLocationGate`). Visual register matches [LifestyleCardScreen]
 * (title + subtitle Cards stacked in a Column) so the step feels at
 * home in the existing wizard.
 *
 * - **Save on your phone (recommended)** — fires [onPickExternal],
 *   which launches the SAF tree picker via `graph.parentPickerHandle`.
 *   The wizard host watches `RepoStoragePrefs.state` and advances when
 *   it flips to a confirmed parent.
 * - **Keep inside the app** — fires [onPickInternal], which writes
 *   `ParentLocation.Internal(filesDir/strictlykeptboy)`, creates the
 *   `.skb-root` marker, then advances.
 *
 * This screen is auto-skipped by [WizardNavHost] when
 * `ParentLocationGate` already reports `Confirmed` (the returning-user
 * re-run path).
 */
@Composable
fun StorageStep(
    onPickExternal: () -> Unit,
    onPickInternal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTagWizardStorage),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.wizard_storage_prompt),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.wizard_storage_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // External card — recommended, leads.
        Card(
            onClick = onPickExternal,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagWizardStorageExternal),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.wizard_storage_external_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.wizard_storage_external_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Internal card — privacy / no-FS-required fallback.
        Card(
            onClick = onPickInternal,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagWizardStorageInternal),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.wizard_storage_internal_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.wizard_storage_internal_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
