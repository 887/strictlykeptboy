package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig

const val TestTagIdentitiesScreen = "IdentitiesScreen"
const val TestTagIdentitiesAdd = "IdentitiesAdd"
const val TestTagIdentityRow = "IdentityRow"

/**
 * Phase I.4 — Thin identity manager. v1: list-only with add/remove
 * affordances. The DM-J multi-identity editor lands later — this surface
 * just exposes the current author identity + lets the user add additional
 * "named" identities (kept in memory until a full DM-J editor ships).
 */
@Composable
fun IdentitiesScreen(
    repo: RepoConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identities = remember(repo.repoId) {
        mutableStateListOf(IdentityEntry(repo.authorIdentity, active = true, signing = false))
    }
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagIdentitiesScreen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.identities_title), style = MaterialTheme.typography.headlineSmall)
        }

        Text(
            stringResource(R.string.identities_partial_note),
            style = MaterialTheme.typography.bodySmall,
        )

        identities.forEachIndexed { idx, entry ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .testTag("$TestTagIdentityRow-$idx")) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(entry.identity.name, style = MaterialTheme.typography.titleSmall)
                    Text(entry.identity.email, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.identities_signing), modifier = Modifier.weight(1f))
                        Switch(
                            checked = entry.signing,
                            onCheckedChange = { v ->
                                identities[idx] = entry.copy(signing = v)
                            },
                        )
                    }
                    Row {
                        if (!entry.active) {
                            TextButton(onClick = {
                                for (i in identities.indices) {
                                    identities[i] = identities[i].copy(active = i == idx)
                                }
                            }) { Text(stringResource(R.string.identities_set_active)) }
                        } else {
                            Text(
                                stringResource(R.string.identities_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (identities.size > 1) {
                            TextButton(onClick = {
                                identities.removeAt(idx)
                                if (identities.none { it.active } && identities.isNotEmpty()) {
                                    identities[0] = identities[0].copy(active = true)
                                }
                            }) { Text(stringResource(R.string.identities_remove)) }
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.identities_add_section), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = newName, onValueChange = { newName = it },
            label = { Text(stringResource(R.string.identities_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = newEmail, onValueChange = { newEmail = it },
            label = { Text(stringResource(R.string.identities_email)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (newName.isNotBlank()) {
                    identities += IdentityEntry(
                        AuthorIdentity(newName, newEmail.ifBlank { "me@example.com" }),
                        active = false, signing = false,
                    )
                    newName = ""; newEmail = ""
                }
            },
            modifier = Modifier.testTag(TestTagIdentitiesAdd),
        ) { Text(stringResource(R.string.identities_add_button)) }
    }
}

private data class IdentityEntry(
    val identity: AuthorIdentity,
    val active: Boolean,
    val signing: Boolean,
)
