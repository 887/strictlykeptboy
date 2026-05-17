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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.wizard.EmojiDensity
import com.eight87.strictlykeptboy.ui.wizard.Honorific
import com.eight87.strictlykeptboy.ui.wizard.TestTagWizardPreviewPane
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft
import com.eight87.strictlykeptboy.ui.wizard.WizardScreen

/**
 * Phase 2.1.H.1 — read-only identity-driven live preview that sits in the
 * detail pane on Medium/Expanded widths. Mirrors the inline preview cards
 * inside [IdentityScreen] + [DoneScreen] but stays visible across every
 * wizard step so tablet users see their choices accumulate. No edits
 * happen here — the step forms on the left own state.
 */
@Composable
internal fun WizardIdentityPreviewPane(draft: WizardDraft, screen: WizardScreen) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(TestTagWizardPreviewPane),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.wizard_identity_preview_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val honoraryPrefix = if (draft.honorific != Honorific.None) {
            "${draft.honorific.labelString()}, "
        } else ""
        val praiseDefault = stringResource(R.string.wizard_identity_praise_default)
        val praise = draft.praiseTerms.firstOrNull() ?: praiseDefault
        val emoji = when (draft.emojiDensity) {
            EmojiDensity.Off -> ""
            EmojiDensity.Light -> " ✓"
            EmojiDensity.Medium -> " ✓ ;3"
            EmojiDensity.Heavy -> " ✓ ;3 ✨"
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.wizard_identity_preview_line, honoraryPrefix, praise, emoji),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Summary of choices so far, regardless of which screen we're on.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Species: ${draft.species.labelString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Alignment: ${draft.alignment.labelString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (draft.honorific != Honorific.None) {
                    Text(
                        "Honorific: ${draft.honorific.labelString()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Tone: ${draft.tone.labelString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (draft.praiseTerms.isNotEmpty()) {
                    Text(
                        "Praise: ${draft.praiseTerms.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (draft.roles.isNotEmpty()) {
                    val roleLabels = draft.roles.map { it.labelString() }
                    Text(
                        "Roles: ${roleLabels.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Text(
            "Step: ${screen.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
