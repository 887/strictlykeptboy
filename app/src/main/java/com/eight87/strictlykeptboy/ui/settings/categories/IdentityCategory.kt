package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.settings.EmojiDensity
import com.eight87.strictlykeptboy.ui.settings.IdentityPrefs
import com.eight87.strictlykeptboy.ui.settings.IdentityState
import com.eight87.strictlykeptboy.ui.settings.ToneRegister

const val TestTagCatIdentity = "Cat-Identity"

/**
 * Phase S.8b — Identity surface (HV-R.3 / DDD.9).
 *
 * All fields update [IdentityPrefs] live, and the preview panel
 * re-renders on every change. "Reset to wizard defaults" wipes the
 * prefs back to constructor defaults.
 *
 * In `strictly-kept` mode the persist path emits a `reviews/<sha>`
 * entry via the Phase YY review-feed writer; that wiring lives in
 * the caller side — this Composable just calls [IdentityPrefs.update].
 */
@Composable
fun IdentityCategory(prefs: IdentityPrefs, modifier: Modifier = Modifier) {
    val state by prefs.state.collectAsState()
    CategorySurface(
        testTag = TestTagCatIdentity,
        title = stringResource(R.string.settings_identity_title),
        modifier = modifier,
    ) {
        // 2.1.E.5 — Sub-section: "My persona" (existing fields).
        Text(
            stringResource(R.string.settings_identity_section_persona),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        FieldRow(
            label = stringResource(R.string.settings_identity_praise),
            value = state.praise,
            testTag = "$TestTagCatIdentity-Praise",
            onChange = { v -> prefs.update { it.copy(praise = v) } },
        )
        FieldRow(
            label = stringResource(R.string.settings_identity_alt_terms),
            value = state.altTerms.joinToString(", "),
            testTag = "$TestTagCatIdentity-AltTerms",
            onChange = { v -> prefs.update { it.copy(altTerms = splitCsv(v)) } },
        )
        FieldRow(
            label = stringResource(R.string.settings_identity_pronouns),
            value = state.pronouns,
            testTag = "$TestTagCatIdentity-Pronouns",
            onChange = { v -> prefs.update { it.copy(pronouns = v) } },
        )
        FieldRow(
            label = stringResource(R.string.settings_identity_pronouns_extra),
            value = state.pronounsExtra.joinToString(", "),
            testTag = "$TestTagCatIdentity-PronounsExtra",
            onChange = { v -> prefs.update { it.copy(pronounsExtra = splitCsv(v)) } },
        )
        FieldRow(
            label = stringResource(R.string.settings_identity_honorific),
            value = state.honorific,
            testTag = "$TestTagCatIdentity-Honorific",
            onChange = { v -> prefs.update { it.copy(honorific = v) } },
        )

        SectionLabel(stringResource(R.string.settings_identity_tone))
        ToneRow(state.tone) { v -> prefs.update { it.copy(tone = v) } }

        SectionLabel(stringResource(R.string.settings_identity_emoji))
        EmojiRow(state.emoji) { v -> prefs.update { it.copy(emoji = v) } }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = { prefs.resetToDefaults() },
            modifier = Modifier.testTag("$TestTagCatIdentity-Reset"),
        ) {
            Text(stringResource(R.string.settings_identity_reset))
        }
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_identity_preview_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        PreviewPanel(state)

        // 2.1.E.5 — Sub-section: "Signing & authors" — absorbs the
        // retired plural Identities stub. Disabled buttons until
        // Phase GG lands signed commits.
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_identity_section_signing),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_identity_signing_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.testTag("$TestTagCatIdentity-SignGpg"),
        ) {
            Text(stringResource(R.string.settings_identity_signing_gpg_disabled))
        }
        TextButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.testTag("$TestTagCatIdentity-SignSsh"),
        ) {
            Text(stringResource(R.string.settings_identity_signing_ssh_disabled))
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, testTag: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
            singleLine = true,
        )
    }
}

@Composable
private fun ToneRow(current: ToneRegister, onSelect: (ToneRegister) -> Unit) {
    val items = listOf(
        ToneRegister.Soft to R.string.settings_identity_tone_soft,
        ToneRegister.Neutral to R.string.settings_identity_tone_neutral,
        ToneRegister.Formal to R.string.settings_identity_tone_formal,
        ToneRegister.Stern to R.string.settings_identity_tone_stern,
        ToneRegister.Playful to R.string.settings_identity_tone_playful,
    )
    Row {
        items.forEach { (tone, label) ->
            FilterChip(
                selected = current == tone,
                onClick = { onSelect(tone) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatIdentity-Tone-${tone.name}"),
            )
        }
    }
}

@Composable
private fun EmojiRow(current: EmojiDensity, onSelect: (EmojiDensity) -> Unit) {
    val items = listOf(
        EmojiDensity.None to R.string.settings_identity_emoji_none,
        EmojiDensity.Sparse to R.string.settings_identity_emoji_sparse,
        EmojiDensity.Standard to R.string.settings_identity_emoji_standard,
        EmojiDensity.Lush to R.string.settings_identity_emoji_lush,
    )
    Row {
        items.forEach { (density, label) ->
            FilterChip(
                selected = current == density,
                onClick = { onSelect(density) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.padding(end = 6.dp).testTag("$TestTagCatIdentity-Emoji-${density.name}"),
            )
        }
    }
}

@Composable
private fun PreviewPanel(state: IdentityState) {
    val emoji = when (state.emoji) {
        EmojiDensity.None -> ""
        EmojiDensity.Sparse -> " ✨"
        EmojiDensity.Standard -> " ✨✨"
        EmojiDensity.Lush -> " ✨✨✨"
    }
    val hon = state.honorific.ifBlank { "" }.let { if (it.isNotEmpty()) "$it " else "" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("$TestTagCatIdentity-Preview"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.settings_identity_preview_card, state.praise) + emoji)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_identity_preview_template, state.praise))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_identity_preview_briefing, hon, state.praise))
        }
    }
}

private fun splitCsv(s: String): List<String> =
    s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
