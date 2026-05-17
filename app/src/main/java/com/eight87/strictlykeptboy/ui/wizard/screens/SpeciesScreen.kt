package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardSpecies
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft

@Composable
internal fun SpeciesScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    // Round 2.14 — fixed chrome (prompt + explainer) at top; the list of
    // hero-sized species cards scrolls in its own region so the wizard's
    // Continue / Back stay visible. Anime variants (Cat-chan / Fox-chan)
    // sink to the bottom so realistic species lead the list.
    val animeIds = setOf(SpeciesChoice.CatChan, SpeciesChoice.FoxChan)
    val cards = SpeciesChoice.entries.sortedBy { if (it in animeIds) 1 else 0 }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTagWizardSpecies),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.wizard_species_prompt), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wizard_species_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("Wizard-Species-CustomizeInfo"),
        ) {
            Text(
                text = stringResource(R.string.wizard_species_customize_later_info),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cards.forEach { species ->
                val selected = draft.species == species
                Card(
                    onClick = { onUpdate(draft.copy(species = species)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("Wizard-Species-${species.id}"),
                    colors = if (selected) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) else CardDefaults.cardColors(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = ComposeAlign.CenterHorizontally,
                    ) {
                        Text(
                            text = speciesPreviewEmoji(species),
                            style = MaterialTheme.typography.displayLarge,
                        )
                        Text(
                            species.labelString(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            }
        }
    }
}

/** Stock-placeholder emoji preview for each species — replaced when
 *  bundled-pack artwork ships (Phase WW). */
private fun speciesPreviewEmoji(species: SpeciesChoice): String = when (species) {
    SpeciesChoice.Bat -> "🦇"
    SpeciesChoice.Bunny -> "🐰"
    SpeciesChoice.Cat -> "🐱"
    SpeciesChoice.CatChan -> "🐱"
    SpeciesChoice.Fox -> "🦊"
    SpeciesChoice.FoxChan -> "🦊"
    SpeciesChoice.Lion -> "🦁"
    SpeciesChoice.Tiger -> "🐯"
    SpeciesChoice.Wolf -> "🐺"
}
