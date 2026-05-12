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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val TestTagRepoSwitcher = "RepoSwitcherChip"

/** UI-B.2 — repo switcher chip. */
@Composable
fun RepoSwitcherChip(
    activeRepoName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 96.dp)
            .testTag(TestTagRepoSwitcher)
            .semantics { contentDescription = "Switch repo, active: $activeRepoName" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
        ) {
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
