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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.eight87.strictlykeptboy.ui.theming.toIconKind
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
/** Round 2.18.C.3 — "System calendars" section header. */
const val TestTagRepoSwitcherSystemHeader = "RepoSwitcherSystemHeader"
/** Round 2.18.C.3 — synthetic non-clickable row, suffix `-<repoId>`. */
const val TestTagRepoSwitcherSystemRow = "RepoSwitcherSystemRow"
/** Round 2.5.A.3 — per-repo "show on schedule" chip toggle. */
const val TestTagRepoSwitcherShowOnSchedule = "RepoSwitcherShowOnSchedule"
/** Round 2.5.A.3 — per-repo "draw tasks from" chip toggle. */
const val TestTagRepoSwitcherDrawTasksFrom = "RepoSwitcherDrawTasksFrom"

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
    /**
     * Round 2.5.A.3 — toggle the per-repo "show on schedule" chip.
     * Null suppresses the chip (back-compat for previews / tests).
     */
    onToggleShowOnSchedule: ((String, Boolean) -> Unit)? = null,
    /**
     * Round 2.5.A.3 — toggle the per-repo "draw tasks from" chip.
     * Null suppresses the chip (back-compat for previews / tests).
     */
    onToggleDrawTasksFrom: ((String, Boolean) -> Unit)? = null,
    /**
     * Round 2.18.C.3 — synthetic repos backing system (CalendarContract)
     * calendars. Rendered under a "System calendars" section header,
     * non-clickable in the active-repo picker (no identity, no writes by
     * default). Each row carries the `repoId` (`system/<accountType>/<accountName>`)
     * and the displayed label (typically `<accountName>`).
     */
    systemRepos: List<SystemRepoRow> = emptyList(),
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
                    onToggleShowOnSchedule = onToggleShowOnSchedule?.let { fn ->
                        { v -> fn(repo.repoId, v) }
                    },
                    onToggleDrawTasksFrom = onToggleDrawTasksFrom?.let { fn ->
                        { v -> fn(repo.repoId, v) }
                    },
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
            // Round 2.18.C.3 — synthetic repos (CalendarContract). Rendered
            // under a "System calendars" header, non-clickable rows so the
            // user can SEE which external accounts feed the schedule but
            // cannot select them as the active write-target.
            if (systemRepos.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.repo_switcher_system_section_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(TestTagRepoSwitcherSystemHeader),
                )
                systemRepos.forEach { sys ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("$TestTagRepoSwitcherSystemRow-${sys.repoId}")
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = sys.displayLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            sys.secondaryLabel?.takeIf { it.isNotBlank() }?.let { sub ->
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Round 2.18.C.3 — display row for a synthetic system-calendar repo. The
 * id is `system/<accountType>/<accountName>` (matches the resolver-level
 * RepoRef); [displayLabel] is the user-facing label (typically the account
 * name); [secondaryLabel] is the account type (`com.google` etc.).
 */
data class SystemRepoRow(
    val repoId: String,
    val displayLabel: String,
    val secondaryLabel: String? = null,
)

@Composable
private fun RepoRow(
    repo: RepoConfig,
    isActive: Boolean,
    status: SyncStatus,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onSync: (() -> Unit)? = null,
    onToggleShowOnSchedule: ((Boolean) -> Unit)? = null,
    onToggleDrawTasksFrom: ((Boolean) -> Unit)? = null,
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
        // Round 2.5.A.3 — per-repo overlay chips (📅 + ✓).
        if (onToggleShowOnSchedule != null) {
            OverlayChip(
                selected = repo.showOnSchedule,
                glyph = "📅", // 📅
                contentDescription = "Show on schedule",
                onClick = { onToggleShowOnSchedule(!repo.showOnSchedule) },
                testTag = "$TestTagRepoSwitcherShowOnSchedule-${repo.repoId}",
            )
        }
        if (onToggleDrawTasksFrom != null) {
            OverlayChip(
                selected = repo.drawTasksFrom,
                glyph = "✓", // ✓
                contentDescription = "Draw tasks from this repo",
                onClick = { onToggleDrawTasksFrom(!repo.drawTasksFrom) },
                testTag = "$TestTagRepoSwitcherDrawTasksFrom-${repo.repoId}",
            )
        }
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
 * Round 2.5.A.3 — small filter-chip-style toggle for the per-repo
 * overlay flags (showOnSchedule + drawTasksFrom). Renders a single
 * glyph inside a rounded surface; tint flips on selection.
 */
@Composable
private fun OverlayChip(
    selected: Boolean,
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = MaterialTheme.typography.labelMedium, color = fg)
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
