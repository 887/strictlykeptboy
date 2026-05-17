package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.wizard.LifestyleCard
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardLifestyle
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft

/**
 * Phase 2.2.B — collapsed Lifestyle screen. Six mutually-exclusive
 * cards (D-2.2.c); each card atomically writes (alignment, lifestyle,
 * modePick, hasPartner) via [WizardDraft.applyLifestyleCard]. Pre-
 * selected default = [LifestyleCard.PetKeptByAi] (solo-sub kept by an
 * AI dom — the project's genesis use-case).
 */
@Composable
internal fun LifestyleCardScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    // Reverse-lookup the currently-active card; falls back to Default
    // when the draft is fresh (no card chosen yet) or to null when the
    // user has hand-edited via Settings into a non-card combination.
    val current = LifestyleCard.fromDraft(draft) ?: LifestyleCard.Default

    // Phase 2.2.B — auto-apply the default card on first entry so the
    // user can simply "Continue" without tapping and have the wizard
    // emit the matching (alignment, lifestyle, modePick, hasPartner)
    // tuple on materialization. Idempotent: re-applying the same card
    // is a no-op for the equality check via [LifestyleCard.fromDraft].
    LaunchedEffect(Unit) {
        val matched = LifestyleCard.fromDraft(draft)
        if (matched == null || matched == LifestyleCard.JustCalendar) {
            onUpdate(draft.applyLifestyleCard(LifestyleCard.Default))
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardLifestyle),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.wizard_lifestyle_card_prompt),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.wizard_lifestyle_card_blurb),
            style = MaterialTheme.typography.bodySmall,
        )
        // Round 2.12 — language-equivalence explainer. The app defaults
        // to pet-coded wording everywhere (matching the project name),
        // but sub/pet/bottom describe the same role here, and dom/owner
        // likewise. Every label is customizable post-wizard in
        // Settings → Identity (praise terms, honorifics, role labels).
        Card(
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Lifestyle-LanguageExplainer"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.wizard_lifestyle_language_explainer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        // Round 2.12 — JustCalendar is now picked on the FramingChoice
        // screen, not here. Hiding it keeps the kink-path picker focused.
        for (card in LifestyleCard.entries.filter { it != LifestyleCard.JustCalendar }) {
            val selected = card == current
            Card(
                onClick = { onUpdate(draft.applyLifestyleCard(card)) },
                colors = if (selected) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else CardDefaults.cardColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Wizard-LifestyleCard-${card.name}"),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${card.emoji}  ${stringResource(card.titleRes)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(card.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@get:androidx.annotation.StringRes
private val LifestyleCard.titleRes: Int
    get() = when (this) {
        LifestyleCard.PetKeptByAi -> R.string.lifestyle_card_pet_kept_by_ai_title
        LifestyleCard.PetKeptByPartner -> R.string.lifestyle_card_pet_kept_by_partner_title
        LifestyleCard.PetSelfKept -> R.string.lifestyle_card_pet_self_kept_title
        LifestyleCard.DomKeepingPets -> R.string.lifestyle_card_dom_keeping_pets_title
        LifestyleCard.Switch -> R.string.lifestyle_card_switch_title
        LifestyleCard.JustCalendar -> R.string.lifestyle_card_just_calendar_title
    }

@get:androidx.annotation.StringRes
private val LifestyleCard.subtitleRes: Int
    get() = when (this) {
        LifestyleCard.PetKeptByAi -> R.string.lifestyle_card_pet_kept_by_ai_subtitle
        LifestyleCard.PetKeptByPartner -> R.string.lifestyle_card_pet_kept_by_partner_subtitle
        LifestyleCard.PetSelfKept -> R.string.lifestyle_card_pet_self_kept_subtitle
        LifestyleCard.DomKeepingPets -> R.string.lifestyle_card_dom_keeping_pets_subtitle
        LifestyleCard.Switch -> R.string.lifestyle_card_switch_subtitle
        LifestyleCard.JustCalendar -> R.string.lifestyle_card_just_calendar_subtitle
    }
