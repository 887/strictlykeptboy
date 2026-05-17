package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
 * Five circular icon buttons: Auto · Compact (40) · Normal (80) ·
 * Detail (160) · Spacious (320). Border-free circles match the
 * schedule-tab affordance in the rail; selected button fills with
 * primary container, unselected uses the bare surface.
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

    val options = listOf(
        ZoomOption("Auto", null, Icons.Outlined.AutoMode, TestTagZoomLevelAuto, null),
        ZoomOption("Compact", 1, Icons.Outlined.UnfoldLess, TestTagZoomLevel40, 40),
        ZoomOption("Normal", 2, Icons.Outlined.GridView, TestTagZoomLevel80, 80),
        ZoomOption("Detail", 3, Icons.Outlined.UnfoldMore, TestTagZoomLevel160, 160),
        ZoomOption("Spacious", 4, Icons.Outlined.OpenInFull, TestTagZoomLevel320, 320),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(TestTagZoomLevelRow),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { opt ->
            val a11y = if (opt.dpPerHour == null) {
                "Zoom: Auto (picker decides dp per hour)"
            } else {
                "Zoom: ${opt.label} (${opt.dpPerHour} dp per hour)"
            }
            val selected = opt.value == selectedOverride
            // Audit pass 2026-05-17 (Fix B) — wrap the whole option in
            // `Modifier.selectable` so (a) `assertIsSelected()` sees the
            // selection state on the testTag node and (b) a tap on the
            // label Text bubbles to the same handler the icon circle
            // would, matching what the schedule-rail circles do.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .testTag(opt.testTag)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(opt.value) },
                    )
                    .semantics { contentDescription = a11y },
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = opt.icon,
                            contentDescription = null,
                        )
                    }
                }
                Text(
                    text = opt.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
