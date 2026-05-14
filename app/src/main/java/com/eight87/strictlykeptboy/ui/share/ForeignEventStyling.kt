package com.eight87.strictlykeptboy.ui.share

import androidx.compose.foundation.background
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.eight87.strictlykeptboy.R

const val TestTagForeignEventChip = "ForeignEvent-Chip"
const val TestTagForeignEventBg = "ForeignEvent-Bg"

/**
 * Phase O.4 — foreign-event chip + de-saturated background helpers.
 *
 * Narrow data interface (R.X.1): takes just the source label, not the
 * whole RepoConfig. Caller pulls the label from `RepoConfig.sourceRepoLabel`.
 *
 * **Round 2.1.B.5 — generalized.** Originally the chip rendered only on
 * bands originating from a `readOnlyViaShare` repo (Phase O share-import
 * path). Per D-2.1.e + B.5, the chip is now a property of any band whose
 * `repo` differs from the current `defaultWriteRepoName`. The chip itself
 * already takes just a label; [isForeignBand] is the predicate band
 * renderers consult.
 */
/**
 * Round 2.1.B.5 — predicate used by Day/Week/Agenda band renderers to
 * decide whether to overlay [ForeignEventSourceChip].
 *
 * `true` when the band's source [bandRepoId] is non-blank and differs
 * from the current write-target repo. Independent of `readOnlyViaShare`.
 */
fun isForeignBand(bandRepoId: String, defaultWriteRepoId: String): Boolean =
    bandRepoId.isNotBlank() &&
        defaultWriteRepoId.isNotBlank() &&
        bandRepoId != defaultWriteRepoId


@Composable
fun ForeignEventSourceChip(
    sourceLabel: String,
    onOpenSource: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = { onOpenSource?.invoke() },
        label = { Text(stringResource(R.string.foreign_event_chip_from, sourceLabel)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.testTag(TestTagForeignEventChip),
    )
}

/**
 * De-saturated background modifier for foreign events — surfaceContainer
 * instead of primaryContainer (spec O.4).
 */
@Composable
fun Modifier.foreignEventBackground(): Modifier =
    this
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .testTag(TestTagForeignEventBg)

/** Color resolver — exposed for tests that want to assert the swap directly. */
object ForeignEventColors {
    @Composable fun container(): Color = MaterialTheme.colorScheme.surfaceContainer
    @Composable fun normalContainer(): Color = MaterialTheme.colorScheme.primaryContainer
}
