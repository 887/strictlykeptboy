package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.AccessRow
import com.eight87.strictlykeptboy.share.ShareMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val TestTagCatAccess = "Cat-Access"
const val TestTagCatAccessEmpty = "Cat-Access-Empty"
const val TestTagCatAccessRowPrefix = "Cat-Access-Row-"

/**
 * Round 2.2.D.6 — global "who has access to what" view.
 *
 * Read-only: tapping a row opens the existing [ShareSheet] for that
 * (repoId, recipient). Rows come pre-aggregated from
 * [com.eight87.strictlykeptboy.store.AccessAggregator].
 */
@Composable
fun AccessCategory(
    rows: List<AccessRow>,
    onOpenShareFor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CategorySurface(
        testTag = TestTagCatAccess,
        title = stringResource(R.string.settings_category_access),
        modifier = modifier,
    ) {
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.settings_access_empty),
                modifier = Modifier.testTag(TestTagCatAccessEmpty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CategorySurface
        }
        rows.forEachIndexed { idx, row ->
            AccessRowCard(row = row, onClick = { onOpenShareFor(row.repoId) })
            if (idx < rows.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AccessRowCard(row: AccessRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$TestTagCatAccessRowPrefix${row.repoId}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(row.repoName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (row.mode) {
                        ShareMode.ReadOnly -> stringResource(R.string.settings_access_mode_ro)
                        ShareMode.ReadWrite -> stringResource(R.string.settings_access_mode_rw)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                row.recipientLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (row.singleUse) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(stringResource(R.string.settings_access_single_use)) },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
                Text(
                    text = row.expiresAtMs?.let { ms ->
                        val iso = Instant.ofEpochMilli(ms)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                        stringResource(R.string.settings_access_expires_at, iso)
                    } ?: stringResource(R.string.settings_access_expires_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
