package com.eight87.strictlykeptboy.ui.adaptive

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagMasterDetailRow = "MasterDetailRow"
const val TestTagMasterPane = "MasterPane"
const val TestTagDetailPane = "DetailPane"

/**
 * Phase R — hand-rolled `Row(master | divider | detail)` for two-pane
 * surfaces. We avoid `material3-adaptive`'s
 * `NavigableListDetailPaneScaffold` because that lib is alpha and not
 * yet on our BoM (only the navigation-suite slice is). The two-pane
 * intent is simple enough that a flexbox `Row` covers it without
 * pulling in new deps.
 *
 * Pane split per UI-R.1: 38% master / 62% detail. The divider gutter
 * widens on Expanded windows per R.5.
 *
 * Caller is responsible for picking which window classes use this vs.
 * a sheet — this composable only renders the two-pane shape.
 */
@Composable
fun MasterDetailLayout(
    modifier: Modifier = Modifier,
    widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Medium,
    master: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val gutterPad = when (widthClass) {
        WindowWidthSizeClass.Expanded -> 16.dp
        else -> 8.dp
    }
    Row(
        modifier = modifier.fillMaxSize().testTag(TestTagMasterDetailRow),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .testTag(TestTagMasterPane),
        ) {
            master()
        }
        VerticalDivider(modifier = Modifier.padding(horizontal = gutterPad))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
                .testTag(TestTagDetailPane),
        ) {
            detail()
        }
    }
}
