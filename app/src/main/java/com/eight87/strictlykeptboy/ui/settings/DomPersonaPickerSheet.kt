package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.store.DomPersonaStore

const val TestTagDomPersonaSheet = "DomPersonaPickerSheet"

/**
 * Phase 2.1.K.7 / DDD.14 — full dom-persona picker bottom-sheet.
 *
 * Per-persona prompt preview + cadence override + custom-prompt editor.
 * The custom slot persists through [DomPersonaStore.writeCustom]; the
 * six builtins remain read-only.
 *
 * SOLID:
 *  - **S:** Picker UI only. Persistence lives in [DomPersonaStore] +
 *    [ModePrefs].
 *  - **I:** Takes the narrow callbacks the parent needs; no graph leak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomPersonaPickerSheet(
    home: String,
    currentPersonaId: String?,
    currentCadence: DomCadence,
    onSelectPersona: (String) -> Unit,
    onSelectCadence: (DomCadence) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var personas by remember {
        mutableStateOf(runCatching { DomPersonaStore.list(home) }.getOrDefault(DomPersonaStore.BUILTINS))
    }
    var selectedId by remember { mutableStateOf(currentPersonaId ?: personas.firstOrNull()?.id.orEmpty()) }
    val selectedPersona = personas.firstOrNull { it.id == selectedId }
    var customPrompt by remember {
        mutableStateOf(
            personas.firstOrNull { it.id == DomPersonaStore.CUSTOM_ID }?.prompt.orEmpty(),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TestTagDomPersonaSheet),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Dom personas",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            personas.forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("$TestTagDomPersonaSheet-Row-${p.id}"),
                ) {
                    RadioButton(
                        selected = selectedId == p.id,
                        onClick = {
                            selectedId = p.id
                            onSelectPersona(p.id)
                        },
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(p.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            p.prompt.take(120) + if (p.prompt.length > 120) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Cadence", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                val cadenceItems = listOf(
                    DomCadence.EndOfDay to "End-of-day",
                    DomCadence.Realtime to "Midday",
                    DomCadence.Weekly to "Weekly",
                )
                cadenceItems.forEach { (c, label) ->
                    FilterChip(
                        selected = currentCadence == c,
                        onClick = { onSelectCadence(c) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("$TestTagDomPersonaSheet-Cadence-${c.name}"),
                    )
                }
            }

            // Custom-prompt editor (the custom slot is always available;
            // writing it materializes the CUSTOM_ID persona on disk).
            Spacer(Modifier.height(12.dp))
            Text("Custom prompt", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("$TestTagDomPersonaSheet-CustomEditor"),
                placeholder = { Text("Write your own dom prompt…") },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        runCatching {
                            DomPersonaStore.writeCustom(home, customPrompt)
                            personas = DomPersonaStore.list(home)
                            selectedId = DomPersonaStore.CUSTOM_ID
                            onSelectPersona(DomPersonaStore.CUSTOM_ID)
                        }
                    },
                    enabled = customPrompt.isNotBlank(),
                    modifier = Modifier.testTag("$TestTagDomPersonaSheet-SaveCustom"),
                ) { Text("Save custom") }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            // Reference selectedPersona so it survives partial-update path.
            selectedPersona?.let { _ ->
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
