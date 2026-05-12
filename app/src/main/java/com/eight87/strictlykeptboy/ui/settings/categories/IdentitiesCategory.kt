package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagCatIdentities = "Cat-Identities"

/**
 * Phase S.2 — Identities category.
 *
 * Per-repo identity stays the source of truth in `identity.toml` per
 * D.83. This surface is a deep-link tile until signed commits land
 * (Phase GG).
 */
@Composable
fun IdentitiesCategory(
    onOpenIdentity: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CategorySurface(
        testTag = TestTagCatIdentities,
        title = stringResource(R.string.settings_category_identity),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.settings_identities_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_identities_signed_commits_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onOpenIdentity,
            modifier = Modifier.testTag("$TestTagCatIdentities-OpenIdentity"),
        ) {
            Text(stringResource(R.string.settings_identities_open_identity))
        }
    }
}
