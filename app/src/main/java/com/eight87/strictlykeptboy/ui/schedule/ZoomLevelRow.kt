package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 * 5-segmented control: Auto | 40 | 80 | 160 | 320 (dp/h). "Auto"
 * clears the global override (legacy max-of-visible behaviour);
 * numbers force the override.
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
    // Stable display order — Auto first so the affordance reads as
    // "let the picker decide" by default.
    val options: List<Pair<String, Int?>> = listOf(
        "Auto" to null,
        "40" to 1,
        "80" to 2,
        "160" to 3,
        "320" to 4,
    )
    val tags = listOf(
        TestTagZoomLevelAuto, TestTagZoomLevel40, TestTagZoomLevel80,
        TestTagZoomLevel160, TestTagZoomLevel320,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(TestTagZoomLevelRow),
    ) {
        options.forEachIndexed { idx, (label, value) ->
            SegmentedButton(
                selected = value == selectedOverride,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                modifier = Modifier.testTag(tags[idx]),
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
