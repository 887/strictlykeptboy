package com.eight87.strictlykeptboy.ui.repos

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.avatar.AvatarPackPrefs
import com.eight87.strictlykeptboy.avatar.CompositePackStore
import com.eight87.strictlykeptboy.avatar.DEFAULT_PACK_ID_PREFIX
import com.eight87.strictlykeptboy.avatar.StickerPack
import com.eight87.strictlykeptboy.avatar.UserPackLoader
import kotlinx.coroutines.launch

const val TestTagStickerPackSelector = "StickerPackSelector"
const val TestTagStickerPackImportButton = "StickerPackSelector-Import"
const val TestTagStickerPackImportDialog = "StickerPackSelector-ImportDialog"
const val TestTagStickerPackRadio = "StickerPackSelector-Radio"

/**
 * Phase 2.5.C.1 — sticker pack picker for a given species.
 *
 * Lists every pack the composite [packStore] knows about (user-installed
 * packs first, then bundled defaults), each with a sample-sticker
 * thumbnail and a radio for "active for this species". Stateless wrt
 * persistence — mutation goes through [AvatarPackPrefs.setActivePackFor]
 * and (for imports) [UserPackLoader.cloneFrom] + [CompositePackStore.refresh].
 *
 * The species comes from the active repo's `iconSpecies` (D.88). The
 * picker doesn't render an emoji fallback — that role is owned by the
 * bundled "minimal — emoji only" pack (one of the entries in this list)
 * so the metaphor stays uniformly "pack" everywhere.
 */
@Composable
fun StickerPackSelectorScreen(
    species: String,
    packStore: CompositePackStore,
    assetPackLoader: AssetPackLoader,
    userPackLoader: UserPackLoader,
    packPrefs: AvatarPackPrefs,
    onBack: () -> Unit,
    onActiveChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var activePackId by remember(species) {
        mutableStateOf(packPrefs.activePackFor(species))
    }
    var packs by remember { mutableStateOf(packStore.all().toList()) }
    var showImport by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importUrl by remember { mutableStateOf("") }

    fun refreshPacks() {
        packStore.refresh()
        packs = packStore.all().toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTagStickerPackSelector)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Sticker pack — ${species.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            "Pick a sticker pack for this species. Custom packs you import live alongside the bundled defaults; activating one here updates the top-bar avatar and every per-activity sticker that resolves against this species.",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()

        if (packs.isEmpty()) {
            Text(
                "No packs available — try '+ Import custom pack' below.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            packs.forEach { pack ->
                PackRow(
                    pack = pack,
                    species = species,
                    isActive = pack.packId == activePackId,
                    assetPackLoader = assetPackLoader,
                    userPackLoader = userPackLoader,
                    onSelect = {
                        packPrefs.setActivePackFor(species, pack.packId)
                        activePackId = pack.packId
                        onActiveChanged()
                    },
                )
            }
        }

        HorizontalDivider()

        Button(
            onClick = { showImport = true; importError = null; importUrl = "" },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagStickerPackImportButton),
        ) { Text("+ Import custom pack") }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { if (!importing) showImport = false },
            title = { Text("Import sticker pack") },
            text = {
                Column(
                    modifier = Modifier.testTag(TestTagStickerPackImportDialog),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Paste a git clone URL (https://…, ssh://…, or file:// for local fixtures). The pack will appear in the list once cloning completes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        label = { Text("Clone URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !importing,
                    )
                    if (importing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text("  Cloning…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    importError?.let { msg ->
                        Text(
                            "Import failed: $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !importing && importUrl.isNotBlank(),
                    onClick = {
                        importing = true
                        importError = null
                        val url = importUrl.trim()
                        scope.launch {
                            val outcome = runCatching { userPackLoader.cloneFrom(url) }
                            importing = false
                            outcome.onSuccess {
                                refreshPacks()
                                showImport = false
                            }.onFailure { t ->
                                importError = t.message ?: t::class.simpleName ?: "unknown"
                            }
                        }
                    },
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(
                    enabled = !importing,
                    onClick = { showImport = false },
                ) { Text("Cancel") }
            },
        )
    }

    // Re-evaluate active pack id whenever the prefs flow changes (e.g.
    // because a different surface activated a pack for this species).
    LaunchedEffect(species) {
        packPrefs.activePerSpecies.collect { map ->
            map[species.lowercase()]?.let { id -> if (id != activePackId) activePackId = id }
        }
    }
}

@Composable
private fun PackRow(
    pack: StickerPack,
    species: String,
    isActive: Boolean,
    assetPackLoader: AssetPackLoader,
    userPackLoader: UserPackLoader,
    onSelect: () -> Unit,
) {
    val isBundled = pack.packId.startsWith(DEFAULT_PACK_ID_PREFIX)
    val matchesSpecies = pack.species.equals(species, ignoreCase = true)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("StickerPackRow-${pack.packId}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PackThumbnail(pack = pack, assetPackLoader = assetPackLoader, userPackLoader = userPackLoader)
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.name, style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(if (isBundled) "bundled" else "custom")
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isBundled)
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    )
                    Text(
                        "species: ${pack.species}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (matchesSpecies) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                    )
                }
                pack.author?.let {
                    Text("by $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            RadioButton(
                selected = isActive,
                onClick = onSelect,
                modifier = Modifier.testTag("$TestTagStickerPackRadio-${pack.packId}"),
            )
        }
    }
}

@Composable
private fun PackThumbnail(
    pack: StickerPack,
    assetPackLoader: AssetPackLoader,
    userPackLoader: UserPackLoader,
) {
    val isBundled = pack.packId.startsWith(DEFAULT_PACK_ID_PREFIX)
    val sample = pack.stickers["idle"] ?: pack.stickers.values.firstOrNull()
    val bitmap = remember(pack.packId, sample?.file) {
        if (sample == null) return@remember null
        if (isBundled) {
            assetPackLoader.loadBitmap(pack.species, sample.file)
        } else {
            userPackLoader.loadBitmap(pack.packId, sample.file)
        }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            DrawBitmap(bitmap)
        } else {
            Text(
                pack.name.first().uppercaseChar().toString(),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DrawBitmap(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}
