package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.components.IdentityAvatar

/**
 * Round 2.21 SOLID split — extracted from `SkbAppShell.kt`. This file
 * owns the top-bar composition (title, destination icon-buttons,
 * overlay-picker button, settings cog, identity avatar). Single
 * reason to change: top-bar layout / chrome.
 *
 * SOLID note: `ShellTopBar` is `internal` so only the shell's other
 * files compose it. The shell's [SkbAppShell.SkbAppShellContent]
 * remains the *only* call-site; this file is pure presentation.
 */
@Composable
internal fun ShellTopBar(
    activeRepoName: String,
    activeIconKind: com.eight87.strictlykeptboy.ui.theming.RepoIconKind,
    title: String,
    selectedDest: TopDestination,
    onSelectDest: (TopDestination) -> Unit,
    onSyncClick: () -> Unit,
    onIdentityClick: () -> Unit,
    onRepoSwitcherClick: () -> Unit,
    /**
     * Round 2.16.F — global app-settings cog moved back into the top-bar
     * action row, immediately before the avatar. The Repos pane's
     * top-bar cog (Round 2.4 migration) is removed; per-repo settings
     * still open via row-tap on a repo inside Repos.
     */
    onSettingsTap: () -> Unit,
    modePrefs: com.eight87.strictlykeptboy.ui.settings.ModePrefs? = null,
) {
    // enableEdgeToEdge() is on in MainActivity — content draws under the
    // status bar by default. Push the top-bar Surface down past the system
    // status + display-cutout inset so the repo chip + destination buttons
    // get the breathing room tonearmboy gets for free via its Scaffold.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .testTag(TestTagShellTopBar),
    ) {
        // Round 2.3.A.1 — single-row top bar. Destination icon-buttons
        // (read surfaces only: Schedule / Tasks / Reviews) are inlined
        // into the action row to the right of the title, alongside the
        // bat avatar. Mode + sync are no longer global concerns — they
        // moved into ReposPane as per-repo state (Round 2.3.A.2 / .A.3).
        // The `modePrefs` + `onSyncClick` params remain on the function
        // signature (null-allowed) to avoid breaking call-sites, but
        // they no longer render anything here.
        // Round 2.16.E — Tasks removed from the top-bar icon row (the
        // destination is gone; todolist UI lives in the expanded
        // NowPlayingScreen sheet now).
        val topBarDestinations = listOf(
            TopDestination.Schedule,
            TopDestination.Reviews,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            topBarDestinations.forEach { dest ->
                DestinationButton(
                    dest = dest,
                    selected = dest == selectedDest,
                    onClick = { onSelectDest(dest) },
                )
            }
            // Round 2.22 / Fix 2 — overlay-picker icon moved to the
            // rail bottom (SkbScheduleRail.RailColumn). The top-bar no
            // longer carries it; previously it disappeared on the
            // Reviews destination, which the user flagged as a bug.
            // Round 2.16.F — global app-settings cog, immediately before
            // the avatar (pre-Round-2.1 location). Tapping selects
            // `TopDestination.Settings`. Per-repo settings still open from
            // inside the Repos pane (row-tap).
            IconButton(
                onClick = onSettingsTap,
                modifier = Modifier.testTag(TestTagShellSettingsCog),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            IdentityAvatar(
                onClick = onRepoSwitcherClick,
                iconKind = activeIconKind,
                sizeDp = 40,
            )
            // Keep params referenced so an accidental removal of either
            // ModePill/SyncButton call-site doesn't silently lose meaning.
            @Suppress("UNUSED_EXPRESSION") modePrefs
            @Suppress("UNUSED_EXPRESSION") onSyncClick
            @Suppress("UNUSED_EXPRESSION") onIdentityClick
            @Suppress("UNUSED_EXPRESSION") activeRepoName
        }
    }
}

/**
 * Round 2.21 SOLID split — destination icon-button. Lifted from
 * `SkbAppShell.kt` along with its companion `RepoSwitcherIconButton`
 * for cohesion with the top-bar's other action-row composables.
 */
@Composable
internal fun DestinationButton(
    dest: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Tonearmboy-shape: standard M3 IconButton (40dp circular hit target +
    // circular ripple). Selected destination renders as FilledTonalIconButton
    // so the active tab reads as a tinted circular pill — same visual
    // language as M3 NavigationBar / NavigationRail selected items.
    val label = dest.labelString()
    val tag = "$TestTagShellDestPrefix${dest.name}"
    val mod = Modifier
        .testTag(tag)
        .semantics { contentDescription = label }
    if (selected) {
        FilledTonalIconButton(onClick = onClick, modifier = mod) {
            Icon(
                imageVector = dest.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        IconButton(onClick = onClick, modifier = mod) {
            Icon(
                imageVector = dest.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Round 2.21 SOLID split — compact icon-only repo switcher. Currently
 * unused from the shell (the IdentityAvatar took over the repo-
 * switcher slot) but kept here for the inline-tooltip variant that a
 * future tablet expansion may want.
 */
@Composable
internal fun RepoSwitcherIconButton(
    activeRepoName: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag("ShellRepoSwitcher")
            .semantics { contentDescription = "Repo: $activeRepoName" }
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
