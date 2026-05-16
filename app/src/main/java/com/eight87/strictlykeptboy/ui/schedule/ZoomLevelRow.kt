package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

const val TestTagZoomLevelRow = "ZoomLevelRow"
const val TestTagZoomLevelAuto = "ZoomLevel-Auto"
const val TestTagZoomLevel40 = "ZoomLevel-40"
const val TestTagZoomLevel80 = "ZoomLevel-80"
const val TestTagZoomLevel160 = "ZoomLevel-160"
const val TestTagZoomLevel320 = "ZoomLevel-320"

/**
 * Round 2.23 Phase C (D-2.23.a) — top-of-Day-view zoom row.
 *
 * 5-segmented control: Auto | Compact (40) | Normal (80) | Detail
 * (160) | Spacious (320) — icon + short descriptive label. "Auto"
 * clears the global override; the rest force a specific dp/h.
 *
 * Why descriptive labels and not raw dp/h numbers (Round 2.23.2
 * follow-up): the numbers meant nothing to non-developer users. Icons
 * convey low-dp/h = compact / high-dp/h = spacious at a glance;
 * labels confirm in words; `contentDescription` carries the precise
 * dp/h for screen-reader users who care about the exact value.
 *
 * Pure UI — host wires [onSelect] to
 * `CalendarVisibilityPrefs.setGlobalZoomOverride(...)`.
 */
@Composable
fun ZoomLevelRow(
    selectedOverride: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    data class ZoomOption(
        val label: String,
        val value: Int?,
        val icon: ImageVector,
        val testTag: String,
        val dpPerHour: Int?,
    )

    // Stable display order — Auto first so the affordance reads as
    // "let the picker decide" by default. Compact -> Spacious mirrors
    // the dp/h ladder so the icon progression (UnfoldLess -> GridView
    // -> UnfoldMore -> OpenInFull) tells the same story visually.
    val options = listOf(
        ZoomOption("Auto", null, Icons.Outlined.AutoMode, TestTagZoomLevelAuto, null),
        ZoomOption("Compact", 1, Icons.Outlined.UnfoldLess, TestTagZoomLevel40, 40),
        ZoomOption("Normal", 2, Icons.Outlined.GridView, TestTagZoomLevel80, 80),
        ZoomOption("Detail", 3, Icons.Outlined.UnfoldMore, TestTagZoomLevel160, 160),
        ZoomOption("Spacious", 4, Icons.Outlined.OpenInFull, TestTagZoomLevel320, 320),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(TestTagZoomLevelRow),
    ) {
        options.forEachIndexed { idx, opt ->
            val a11y = if (opt.dpPerHour == null) {
                "Zoom: Auto (picker decides dp per hour)"
            } else {
                "Zoom: ${opt.label} (${opt.dpPerHour} dp per hour)"
            }
            SegmentedButton(
                selected = opt.value == selectedOverride,
                onClick = { onSelect(opt.value) },
                shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                modifier = Modifier
                    .testTag(opt.testTag)
                    .semantics { contentDescription = a11y },
                label = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = opt.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = opt.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                },
            )
        }
    }
}
