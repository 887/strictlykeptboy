package com.eight87.strictlykeptboy.ui.theming

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagRepoIconPicker = "RepoIconPicker"
const val TestTagRepoIconPickerEmojiSwatch = "RepoIconPicker-Emoji"
const val TestTagRepoIconPickerInitialsBtn = "RepoIconPicker-Initials"
const val TestTagRepoIconPickerPhotoBtn = "RepoIconPicker-Photo"
const val TestTagRepoIconPickerEmojiField = "RepoIconPicker-EmojiField"

/**
 * Phase T.2 — repo icon picker.
 *
 * Stateless wrt persistence; emits a new [RepoIconKind] via [onKindChange]
 * when the user picks. Default subset of mascot-friendly emoji per the
 * brief; user can type any unicode in the custom field.
 */
@Composable
fun RepoIconPicker(
    displayName: String,
    current: RepoIconKind,
    onKindChange: (RepoIconKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onKindChange(RepoIconKind.Photo(uri.toString()))
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag(TestTagRepoIconPicker),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RepoIcon(current, sizeDp = 56.dp)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(stringResource(R.string.repo_icon_picker_preview), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (current) {
                        is RepoIconKind.Emoji -> stringResource(R.string.repo_icon_kind_emoji)
                        is RepoIconKind.AutoInitials -> stringResource(R.string.repo_icon_kind_initials)
                        is RepoIconKind.Photo -> stringResource(R.string.repo_icon_kind_photo)
                        is RepoIconKind.Sticker -> current.species.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Kind chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = current is RepoIconKind.AutoInitials,
                onClick = {
                    onKindChange(
                        RepoIconKind.AutoInitials(
                            initials = initialsFromName(displayName),
                            seedColor = seedColorFromName(displayName),
                        ),
                    )
                },
                label = { Text(stringResource(R.string.repo_icon_kind_initials)) },
                modifier = Modifier.testTag(TestTagRepoIconPickerInitialsBtn),
            )
            FilterChip(
                selected = current is RepoIconKind.Emoji,
                onClick = { onKindChange(RepoIconKind.Emoji(DEFAULT_EMOJI_SUBSET.first())) },
                label = { Text(stringResource(R.string.repo_icon_kind_emoji)) },
            )
            FilterChip(
                selected = current is RepoIconKind.Photo,
                onClick = { photoLauncher.launch(arrayOf("image/*")) },
                label = { Text(stringResource(R.string.repo_icon_kind_photo)) },
                modifier = Modifier.testTag(TestTagRepoIconPickerPhotoBtn),
            )
        }

        if (current is RepoIconKind.Emoji) {
            Text(stringResource(R.string.repo_icon_emoji_subset), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DEFAULT_EMOJI_SUBSET) { glyph ->
                    Surface(
                        shape = CircleShape,
                        color = if (current.glyph == glyph)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("$TestTagRepoIconPickerEmojiSwatch-$glyph"),
                    ) {
                        TextButton(onClick = { onKindChange(RepoIconKind.Emoji(glyph)) }) {
                            Text(glyph)
                        }
                    }
                }
            }
            var custom by remember(current) { mutableStateOf(current.glyph) }
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it
                    if (it.isNotEmpty()) onKindChange(RepoIconKind.Emoji(it))
                },
                label = { Text(stringResource(R.string.repo_icon_emoji_custom)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(TestTagRepoIconPickerEmojiField),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Mascot-friendly default subset. Kept short so the picker fits one
 * row on a phone in portrait. User-typed text in the custom field
 * accepts anything Unicode supports.
 */
val DEFAULT_EMOJI_SUBSET: List<String> = listOf(
    "📅", // 📅
    "⏰",       // ⏰
    "✨",       // ✨
    "❤",       // ❤
    "🐾", // 🐾
    "🦇", // 🦇
    "🦊", // 🦊
    "🐺", // 🐺
    "🦁", // 🦁
    "🐯", // 🐯
    "🐰", // 🐰
    "🐱", // 🐱
)
