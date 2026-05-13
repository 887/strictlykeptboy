package com.eight87.strictlykeptboy.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagMirrorDivergenceBanner = "MirrorDivergenceBanner"
const val TestTagPartialPushDegradedDot = "PartialPushDegradedDot"

/**
 * Phase ZZ.D / MO-D — soft yellow banner shown when a non-primary remote
 * (mirror) has commits the primary doesn't. NOT an error; the primary
 * remains canonical and the user can opt into a diamond-merge mini-flow
 * via repo settings → Remotes → [mirror] → Advanced.
 *
 * SOLID.S: pure presentation, takes the mirror's display label as the only param.
 */
@Composable
fun MirrorDivergenceBanner(
    mirrorLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF4CC))
            .padding(12.dp)
            .testTag(TestTagMirrorDivergenceBanner),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.mirror_divergence_banner, mirrorLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5A4500),
        )
    }
}

/**
 * Phase ZZ.E / MO-E — small non-red dot indicator for a remote whose
 * push failed after the primary push succeeded. The local commit is shipped
 * (primary has it). Used inline in a per-remote row.
 */
@Composable
fun PartialPushDegradedDot(
    mirrorLabel: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.partial_push_degraded_dot, mirrorLabel),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFFB07A00),
        modifier = modifier.testTag(TestTagPartialPushDegradedDot),
    )
}
