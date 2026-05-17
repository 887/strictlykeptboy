package com.eight87.strictlykeptboy.ui.wizard.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.wizard.Alignment
import com.eight87.strictlykeptboy.ui.wizard.EmojiDensity
import com.eight87.strictlykeptboy.ui.wizard.Honorific
import com.eight87.strictlykeptboy.ui.wizard.PraiseRegistry
import com.eight87.strictlykeptboy.ui.wizard.PronounSet
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardIdentity
import com.eight87.strictlykeptboy.ui.wizard.ToneRegister
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft
import com.eight87.strictlykeptboy.ui.wizard.WrappingChipRow

@Composable
internal fun IdentityScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    val showHonorific = draft.alignment == Alignment.Submissive || draft.alignment == Alignment.Switch
    var customPraise by remember { mutableStateOf("") }
    // Merge defaults with whatever the user already picked (incl.
    // custom-added terms) so custom entries render as their own chips.
    val praiseChipTerms = (PraiseRegistry.defaults + draft.praiseTerms).distinct()
    Column(
        modifier = Modifier.fillMaxSize().testTag(TestTagWizardIdentity),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.wizard_identity_prompt), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wizard_identity_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Praise chips (multi-select) + custom-term input.
            Text(stringResource(R.string.wizard_identity_praise_label), style = MaterialTheme.typography.bodyMedium)
            WrappingChipRow {
                praiseChipTerms.forEach { term ->
                    val on = term in draft.praiseTerms
                    FilterChip(
                        selected = on,
                        onClick = {
                            val next = if (on) draft.praiseTerms - term else draft.praiseTerms + term
                            onUpdate(draft.copy(praiseTerms = next.ifEmpty { listOf("good boy") }))
                        },
                        label = { Text(term) },
                        modifier = Modifier.testTag("Wizard-Praise-${term.replace(' ', '-')}"),
                    )
                }
            }
            Row(
                verticalAlignment = ComposeAlign.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customPraise,
                    onValueChange = { customPraise = it },
                    label = { Text("Add custom term") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("Wizard-Praise-Custom-Input"),
                )
                TextButton(
                    enabled = customPraise.isNotBlank() && customPraise.trim() !in draft.praiseTerms,
                    onClick = {
                        val term = customPraise.trim()
                        if (term.isNotEmpty()) {
                            onUpdate(draft.copy(praiseTerms = draft.praiseTerms + term))
                            customPraise = ""
                        }
                    },
                    modifier = Modifier.testTag("Wizard-Praise-Custom-Add"),
                ) { Text("Add") }
            }

            // Pronouns as compact chips.
            Text(stringResource(R.string.wizard_identity_pronouns_label), style = MaterialTheme.typography.bodyMedium)
            WrappingChipRow {
                PronounSet.defaults.forEach { p ->
                    FilterChip(
                        selected = draft.pronouns == p,
                        onClick = { onUpdate(draft.copy(pronouns = p)) },
                        label = { Text(p.label) },
                        modifier = Modifier.testTag("Wizard-Pronouns-${p.subject}"),
                    )
                }
            }

            if (showHonorific) {
            Text(stringResource(R.string.wizard_identity_honorific_label), style = MaterialTheme.typography.bodyMedium)
            WrappingChipRow {
                Honorific.entries.forEach { h ->
                    FilterChip(
                        selected = draft.honorific == h,
                        onClick = { onUpdate(draft.copy(honorific = h)) },
                        label = { Text(h.labelString()) },
                        modifier = Modifier.testTag("Wizard-Honorific-${h.name}"),
                    )
                }
            }
        }

        Text(stringResource(R.string.wizard_identity_tone_label), style = MaterialTheme.typography.bodyMedium)
        WrappingChipRow {
            ToneRegister.entries.forEach { t ->
                FilterChip(
                    selected = draft.tone == t,
                    onClick = { onUpdate(draft.copy(tone = t)) },
                    label = { Text(t.labelString()) },
                    modifier = Modifier.testTag("Wizard-Tone-${t.id}"),
                )
            }
        }

        Text(stringResource(R.string.wizard_identity_emoji_label), style = MaterialTheme.typography.bodyMedium)
        WrappingChipRow {
            EmojiDensity.entries.forEach { e ->
                FilterChip(
                    selected = draft.emojiDensity == e,
                    onClick = { onUpdate(draft.copy(emojiDensity = e)) },
                    label = { Text(e.labelString()) },
                    modifier = Modifier.testTag("Wizard-Emoji-${e.id}"),
                )
            }
        }

        // Live preview
        Card(
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Identity-Preview"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_identity_preview_label), style = MaterialTheme.typography.labelMedium)
                val honoraryPrefix = if (draft.honorific != Honorific.None) "${draft.honorific.labelString()}, " else ""
                val praiseDefault = stringResource(R.string.wizard_identity_praise_default)
                val praise = draft.praiseTerms.firstOrNull() ?: praiseDefault
                val emoji = when (draft.emojiDensity) {
                    EmojiDensity.Off -> ""
                    EmojiDensity.Light -> " ✓"
                    EmojiDensity.Medium -> " ✓ ;3"
                    EmojiDensity.Heavy -> " ✓ ;3 ✨"
                }
                Text(
                    stringResource(R.string.wizard_identity_preview_line, honoraryPrefix, praise, emoji),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        }
    }
}
