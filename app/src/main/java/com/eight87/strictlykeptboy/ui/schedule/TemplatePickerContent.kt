package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.SectionKind
import com.eight87.strictlykeptboy.store.TemplateEntry
import com.eight87.strictlykeptboy.store.TemplateMerger
import com.eight87.strictlykeptboy.store.TemplateSection

const val TestTagTemplatePicker = "TemplatePicker"
const val TestTagTemplatePickerSearch = "TemplatePickerSearch"
const val TestTagTemplatePickerRow = "TemplatePickerRow"
const val TestTagTemplatePickerEmpty = "TemplatePickerEmpty"
const val TestTagTemplatePickerEmptyFreeFormChip = "TemplatePickerEmptyFreeFormChip"

/**
 * Phase FFF / EC-C — template-picker composable.
 *
 * Pill `TextField` search at top (UI-WW parity convention — pill
 * shape, `surfaceContainerHigh` fill, transparent indicators).
 * Below: grouped sections per [TemplateMerger.group]. Each row is
 * an M3 `ListItem` with a colored leading badge (tint derived from
 * the template-id hash for stability across sessions).
 *
 * Stateless. Caller hands the merged-and-filtered entries; this
 * composable owns the search query and dispatches taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerContent(
    entries: List<TemplateEntry>,
    neutralMode: Boolean,
    onPickTemplate: (TemplateEntry) -> Unit,
    onInfo: (TemplateEntry) -> Unit = {},
    onFlipToFreeForm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filtered = TemplateMerger.filter(entries, query, neutralMode)
    val sections = TemplateMerger.group(filtered)

    Column(modifier = modifier.fillMaxWidth().testTag(TestTagTemplatePicker)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.event_create_template_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(TestTagTemplatePickerSearch),
        )

        if (sections.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .testTag(TestTagTemplatePickerEmpty),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.event_create_template_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = onFlipToFreeForm,
                    label = { Text(stringResource(R.string.event_create_template_switch_to_free_form)) },
                    modifier = Modifier.testTag(TestTagTemplatePickerEmptyFreeFormChip),
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            sections.forEach { section ->
                item("hdr-${section.kind.name}-${section.packId ?: ""}") {
                    SectionHeader(section)
                }
                items(section.entries, key = { it.templateId }) { entry ->
                    TemplateRow(entry = entry, onClick = { onPickTemplate(entry) }, onInfo = { onInfo(entry) })
                }
                item("sp-${section.kind.name}") { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: TemplateSection) {
    val title = when (section.kind) {
        SectionKind.SelfCare -> stringResource(R.string.event_create_template_section_self_care)
        SectionKind.Workout -> stringResource(R.string.event_create_template_section_workout)
        SectionKind.Routine -> stringResource(R.string.event_create_template_section_routine)
        SectionKind.Kink -> stringResource(R.string.event_create_template_section_kink)
        SectionKind.User -> stringResource(R.string.event_create_template_section_user)
        SectionKind.Pack -> section.packId ?: stringResource(R.string.event_create_template_section_pack_fallback)
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun TemplateRow(entry: TemplateEntry, onClick: () -> Unit, onInfo: () -> Unit) {
    val tint = tintFor(entry.templateId)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$TestTagTemplatePickerRow-${entry.templateId}"),
        color = Color.Transparent,
    ) {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tint),
                )
            },
            headlineContent = { Text(entry.displayName) },
            supportingContent = {
                Text(
                    text = entry.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                IconButton(onClick = onInfo) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                }
            },
        )
    }
}

private fun tintFor(templateId: String): Color {
    var h = 0x811C9DC5.toInt()
    for (c in templateId) {
        h = h xor c.code
        h *= 0x01000193
    }
    val hue = ((h ushr 8) and 0xFF) / 255f * 360f
    return hslToColor(hue, 0.45f, 0.55f)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs(((h / 60f) % 2f) - 1f))
    val m = l - c / 2f
    val (r, g, b) = when ((h / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
