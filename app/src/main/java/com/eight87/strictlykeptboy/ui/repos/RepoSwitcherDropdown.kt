package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.store.ModeTomlCodec
import com.eight87.strictlykeptboy.store.RepoMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val TestTagRepoSwitcherDropdown = "RepoSwitcherDropdown"
const val TestTagRepoSwitcherRow = "RepoSwitcherRow"
const val TestTagRepoSwitcherAdd = "RepoSwitcherAdd"
const val TestTagRepoSwitcherHouseGlyph = "RepoSwitcherHouseGlyph"
const val TestTagRepoSwitcherSettings = "RepoSwitcherSettings"
const val TestTagRepoSwitcherModeBadge = "RepoSwitcherModeBadge"
const val TestTagRepoSwitcherSync = "RepoSwitcherSync"

/**
 * Phase I.1 — Repo switcher dropdown. Lists configured repos with circular
 * icon (emoji or initial), display name, sync status badge. No-origin repos
 * render a house glyph per ZZ.G. Tap a row → switch active repo. Tap a
 * row's settings cog → open repo settings. "+" entry → Add-Repo flow.
 */
@Composable
fun RepoSwitcherDropdown(
    repos: List<RepoConfig>,
    activeRepoId: String?,
    statusFor: (RepoConfig) -> SyncStatus,
    onSelect: (String) -> Unit,
    onAddRepo: () -> Unit,
    onOpenSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Round 2.3.A.3 — per-repo sync trigger. When provided, each repo
     * row renders a 24dp sync IconButton on the right edge (before the
     * settings cog). Null suppresses the icon (previews / tests).
     * Wires to [SyncService.startSyncRepo] in production.
     */
    onSyncRepo: ((String) -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.testTag(TestTagRepoSwitcherDropdown),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            repos.forEach { repo ->
                RepoRow(
                    repo = repo,
                    isActive = repo.repoId == activeRepoId,
                    status = statusFor(repo),
                    onSelect = { onSelect(repo.repoId) },
                    onSettings = { onOpenSettings(repo.repoId) },
                    onSync = onSyncRepo?.let { fn -> { fn(repo.repoId) } },
                )
            }
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onAddRepo)
                    .testTag(TestTagRepoSwitcherAdd)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_repo_switcher_add))
                Text(
                    text = stringResource(R.string.repo_switcher_add),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RepoRow(
    repo: RepoConfig,
    isActive: Boolean,
    status: SyncStatus,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onSync: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("$TestTagRepoSwitcherRow-${repo.repoId}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RepoCircle(repo = repo, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repo.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (repo.remotes.isEmpty()) {
                Text(
                    text = stringResource(R.string.repo_switcher_local_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Round 2.3.A.2 — per-repo mode badge read-only from mode.toml.
        RepoModeBadge(repo = repo)
        StatusBadge(status = status, isLocalOnly = repo.remotes.isEmpty())
        // Round 2.3.A.3 — per-repo sync icon (replaces the global one
        // formerly in ShellTopBar). Hidden for local-only repos and
        // when no sync handler is wired (previews / tests).
        if (onSync != null && repo.remotes.isNotEmpty()) {
            IconButton(
                onClick = onSync,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("$TestTagRepoSwitcherSync-${repo.repoId}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = stringResource(R.string.cd_sync),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.testTag("$TestTagRepoSwitcherSettings-${repo.repoId}"),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_repo_switcher_settings_for, repo.displayName))
        }
    }
}

/**
 * Round 2.3.A.2 — small read-only badge showing this repo's mode.
 *
 * Reads `<repo.rootDir>/mode.toml` on a background dispatcher via
 * `ModeTomlCodec.readOrDefault`; defaults to `RepoMode.Free` until
 * the read returns. Long-press / transition affordance lives in
 * Settings → Mode (per-repo); this badge is read-only chrome.
 */
@Composable
private fun RepoModeBadge(repo: RepoConfig) {
    var mode by remember(repo.repoId, repo.rootDir) {
        mutableStateOf(RepoMode.Free)
    }
    LaunchedEffect(repo.repoId, repo.rootDir) {
        mode = withContext(Dispatchers.IO) {
            runCatching {
                ModeTomlCodec.readOrDefault(Paths.get(repo.rootDir)).mode
            }.getOrDefault(RepoMode.Free)
        }
    }
    val label = when (mode) {
        RepoMode.Free -> "free"
        RepoMode.StrictlyKept -> "kept"
        RepoMode.SelfKeep -> "self"
    }
    val icon = when (mode) {
        RepoMode.Free -> Icons.Filled.LockOpen
        RepoMode.StrictlyKept -> Icons.Filled.Lock
        RepoMode.SelfKeep -> Icons.Filled.Lock
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("$TestTagRepoSwitcherModeBadge-${repo.repoId}"),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "mode: $label",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Phase WW — repo-row circular icon. Delegates to the sealed
 * [com.eight87.strictlykeptboy.ui.theming.RepoIcon] so species-stickers
 * render via the avatar resolver chain (D.66) the same way the top-bar
 * avatar does. Falls back to emoji / initials when iconSpecies is unset.
 */
@Composable
internal fun RepoCircle(repo: RepoConfig, modifier: Modifier = Modifier) {
    val kind = repo.toIconKind()
    com.eight87.strictlykeptboy.ui.theming.RepoIcon(
        kind = kind,
        modifier = modifier,
        sizeDp = 36.dp,
    )
}

@Composable
private fun StatusBadge(status: SyncStatus, isLocalOnly: Boolean) {
    if (isLocalOnly) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = stringResource(R.string.cd_repo_switcher_local_only_repo),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .testTag(TestTagRepoSwitcherHouseGlyph),
        )
        return
    }
    when (status) {
        SyncStatus.Synced -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.cd_repo_switcher_synced),
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(18.dp),
        )
        SyncStatus.Syncing -> CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        SyncStatus.Error -> Icon(
            Icons.Filled.Error,
            contentDescription = stringResource(R.string.cd_repo_switcher_sync_error),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        SyncStatus.LocalOnly -> Icon(
            Icons.Filled.Home,
            contentDescription = stringResource(R.string.cd_repo_switcher_local_only_repo),
            modifier = Modifier
                .size(18.dp)
                .testTag(TestTagRepoSwitcherHouseGlyph),
        )
    }
    // Silence "unused" warning for the import:
    @Suppress("UNUSED_EXPRESSION") Icons.Filled.Sync
}
