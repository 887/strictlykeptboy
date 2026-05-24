package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.git.RepoConfig

// -----------------------------------------------------------------------
// Round 2.19 — per-repo expandable Material3 card.
//
// Replaces the dense, tiny-checkbox row layout the user complained about
// ("the toggle checkboxes do not work for me on the repo. they're too
// tiny."). Each repo is now its own elevated Card with:
//
//   - header: avatar + display name + write-target radio (mutex via
//     activeRepoId).  Tapping anywhere in the header EXCEPT the radio
//     toggles expand/collapse.
//   - body: every binary repo setting as a M3 Switch, indented 16dp
//     from card padding so they read as "settings hanging from the
//     repo header" — the "Python tab" the user asked for.
//   - footer: "More settings…" TextButton opens RepoSettingsScreen
//     for the long-tail edits (display name, avatar, ssh remote,
//     danger zone, etc).
//
// Expand state is persisted with rememberSaveable(repoId) so the user's
// fold choices survive config changes.
// -----------------------------------------------------------------------

const val TestTagRepoCard = "RepoCard"
const val TestTagRepoCardHeader = "RepoCard-Header"
const val TestTagRepoCardWriteTarget = "RepoCard-WriteTarget"
const val TestTagRepoCardExpandIcon = "RepoCard-ExpandIcon"
const val TestTagRepoCardMoreSettings = "RepoCard-MoreSettings"
const val TestTagRepoCardSwitchShowOnSchedule = "RepoCard-Switch-ShowOnSchedule"
const val TestTagRepoCardSwitchDrawTasksFrom = "RepoCard-Switch-DrawTasksFrom"
const val TestTagRepoCardSwitchAutoSync = "RepoCard-Switch-AutoSync"
const val TestTagRepoCardSwitchWifiOnly = "RepoCard-Switch-WifiOnly"
const val TestTagRepoCardSwitchImportStickers = "RepoCard-Switch-ImportStickers"
const val TestTagRepoCardSwitchAdjustToLocalTimezone = "RepoCard-Switch-AdjustToLocalTimezone"

/**
 * One repo as an expandable Material3 card. See file-level comment for
 * the layout / wiring decisions.
 */
@Composable
fun RepoCard(
    repo: RepoConfig,
    isWriteTarget: Boolean,
    onSelectWriteTarget: () -> Unit,
    onToggleShowOnSchedule: (Boolean) -> Unit,
    onToggleDrawTasksFrom: (Boolean) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleImportStickers: (Boolean) -> Unit,
    onToggleAdjustToLocalTimezone: (Boolean) -> Unit = {},
    onOpenMoreSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Default: expanded for the write-target repo, collapsed otherwise.
    var expanded by rememberSaveable(repo.repoId) { mutableStateOf(isWriteTarget) }

    // Round 2.19 — sticker-pack import side-effect, hoisted from
    // RepoSettingsScreen along with the toggle. Flipping the switch on
    // copies the active bundled pack into <repoRoot>/stickers/<packId>/
    // and commits via GitRepoRegistry. We track the previous value via
    // `remember` so we only fire on the off→on transition.
    val assetLoader = LocalAssetPackLoader.current
    val packPrefs = LocalAvatarPackPrefs.current
    val didImport = remember(repo.repoId) { mutableStateOf(repo.importStickersToRepo) }
    LaunchedEffect(repo.importStickersToRepo, repo.iconSpecies, repo.repoId) {
        val on = repo.importStickersToRepo
        val justFlippedOn = on && !didImport.value
        val species = repo.iconSpecies
        if (justFlippedOn && species != null && assetLoader != null && packPrefs != null) {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val packId = packPrefs.activePackFor(species.lowercase())
                    val bundledAssetFolder = if (packId.startsWith("default-")) {
                        packId.removePrefix("default-")
                    } else {
                        species.lowercase()
                    }
                    val dest = java.nio.file.Paths.get(repo.rootDir)
                        .resolve("stickers/$packId/")
                    assetLoader.copyPackInto(bundledAssetFolder, dest)
                    com.eight87.strictlykeptboy.git.GitRepoRegistry
                        .get(repo.repoId)
                        ?.commitAll("stickers: import $packId pack into repo")
                }
            }
        }
        didImport.value = on
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("$TestTagRepoCard-${repo.repoId}"),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // -------- Header --------
            // Header is split into a clickable left region (avatar + name +
            // expand chevron) and the write-target radio on the right.
            // Keeping the radio OUTSIDE the clickable region ensures a tap
            // on the radio fires `onSelectWriteTarget` without also
            // toggling expansion.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { expanded = !expanded }
                        .testTag("$TestTagRepoCardHeader-${repo.repoId}")
                        .padding(vertical = 4.dp),
                ) {
                    RepoCircle(repo = repo, modifier = Modifier.size(40.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = repo.displayName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (repo.remotes.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = " local only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "collapse" else "expand",
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("$TestTagRepoCardExpandIcon-${repo.repoId}"),
                    )
                }
                RadioButton(
                    selected = isWriteTarget,
                    onClick = onSelectWriteTarget,
                    modifier = Modifier
                        .testTag("$TestTagRepoCardWriteTarget-${repo.repoId}")
                        .semantics { contentDescription = "Set as write target" },
                )
            }

            // -------- Body (switches, indented 16dp from card padding) --------
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    SwitchRow(
                        label = "Show on schedule",
                        checked = repo.showOnSchedule,
                        onCheckedChange = onToggleShowOnSchedule,
                        testTag = "$TestTagRepoCardSwitchShowOnSchedule-${repo.repoId}",
                    )
                    SwitchRow(
                        label = "Show in tasks",
                        checked = repo.drawTasksFrom,
                        onCheckedChange = onToggleDrawTasksFrom,
                        testTag = "$TestTagRepoCardSwitchDrawTasksFrom-${repo.repoId}",
                    )
                    SwitchRow(
                        label = "Auto-sync",
                        checked = repo.autoSyncEnabled,
                        onCheckedChange = onToggleAutoSync,
                        testTag = "$TestTagRepoCardSwitchAutoSync-${repo.repoId}",
                    )
                    SwitchRow(
                        label = "Wi-Fi only",
                        checked = repo.wifiOnly,
                        onCheckedChange = onToggleWifiOnly,
                        testTag = "$TestTagRepoCardSwitchWifiOnly-${repo.repoId}",
                    )
                    SwitchRow(
                        label = "Import stickers into repo",
                        checked = repo.importStickersToRepo,
                        onCheckedChange = onToggleImportStickers,
                        testTag = "$TestTagRepoCardSwitchImportStickers-${repo.repoId}",
                    )
                    SwitchRow(
                        label = "Adjust to local timezone",
                        checked = repo.adjustToLocalTimezone,
                        onCheckedChange = onToggleAdjustToLocalTimezone,
                        testTag = "$TestTagRepoCardSwitchAdjustToLocalTimezone-${repo.repoId}",
                    )

                    // -------- Footer --------
                    TextButton(
                        onClick = onOpenMoreSettings,
                        modifier = Modifier
                            .testTag("$TestTagRepoCardMoreSettings-${repo.repoId}")
                            .padding(top = 4.dp),
                    ) { Text("More settings…") }
                }
            }
        }
    }
}

/**
 * Single inline switch row. Full-width tappable, label on the left
 * (bodyLarge), M3 Switch on the right, min height 56dp so the touch
 * target is comfortable — fixing the user's "checkboxes are too tiny"
 * complaint.
 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
