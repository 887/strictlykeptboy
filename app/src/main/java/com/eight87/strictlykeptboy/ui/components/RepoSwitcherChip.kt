package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.theming.RepoIcon
import com.eight87.strictlykeptboy.ui.theming.RepoIconKind

const val TestTagRepoSwitcher = "RepoSwitcherChip"
const val TestTagRepoSwitcherLeading = "RepoSwitcherChip-Leading"

/**
 * UI-B.2 — repo switcher chip.
 *
 * Phase WW: optional [leadingIconKind] renders a 28dp species-sticker
 * (or emoji / initials) on the left, making the chip itself a presence
 * indicator for the active repo's avatar. Omit for surfaces that
 * already render the avatar elsewhere.
 */
@Composable
fun RepoSwitcherChip(
    activeRepoName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIconKind: RepoIconKind? = null,
) {
    val cd = stringResource(R.string.cd_repo_switcher_active, activeRepoName)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 96.dp)
            .testTag(TestTagRepoSwitcher)
            .semantics { contentDescription = cd },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(
                start = if (leadingIconKind != null) 6.dp else 12.dp,
                end = 8.dp,
            ),
        ) {
            if (leadingIconKind != null) {
                RepoIcon(
                    kind = leadingIconKind,
                    sizeDp = 28.dp,
                    modifier = Modifier.testTag(TestTagRepoSwitcherLeading),
                )
            }
            Text(
                text = activeRepoName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
