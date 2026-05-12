package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagCatTemplates = "Cat-Templates"

/**
 * Phase S.7 — Templates category.
 *
 * - Browse atomic-template registry (`templates/atomic-*.toml`).
 * - Apply a template to a calendar (callback up).
 * - Custom template repo URL stub — clone wiring lands in Phase WW.
 *
 * R.X.1: takes a narrow `templateIds` list + apply / save-url callbacks
 * rather than a god-state.
 */
@Composable
fun TemplatesCategory(
    templateIds: List<String>,
    onApply: (templateId: String) -> Unit = {},
    onSaveCustomUrl: (url: String) -> Unit = {},
    initialCustomUrl: String = "",
    modifier: Modifier = Modifier,
) {
    CategorySurface(
        testTag = TestTagCatTemplates,
        title = stringResource(R.string.settings_category_templates),
        modifier = modifier,
    ) {
        SectionLabel(stringResource(R.string.settings_templates_registry))
        if (templateIds.isEmpty()) {
            Text(
                stringResource(R.string.settings_category_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            templateIds.forEach { id ->
                ListItem(
                    headlineContent = { Text(id) },
                    trailingContent = {
                        TextButton(
                            onClick = { onApply(id) },
                            modifier = Modifier.testTag("$TestTagCatTemplates-Apply-$id"),
                        ) {
                            Text(stringResource(R.string.settings_templates_apply))
                        }
                    },
                )
            }
        }
        HorizontalDivider()
        SectionLabel(stringResource(R.string.settings_templates_custom_url))
        Text(
            stringResource(R.string.settings_templates_custom_url_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var url by rememberSaveable { mutableStateOf(initialCustomUrl) }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(stringResource(R.string.settings_templates_paste_hint)) },
                modifier = Modifier.fillMaxWidth().testTag("$TestTagCatTemplates-Url"),
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { onSaveCustomUrl(url) },
                modifier = Modifier.testTag("$TestTagCatTemplates-Save"),
            ) {
                Text(stringResource(R.string.settings_templates_save))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
