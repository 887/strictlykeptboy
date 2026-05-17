package com.eight87.strictlykeptboy.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.TimeSlot
import java.time.format.DateTimeFormatter

const val TestTagTogetherResultList = "TogetherResultList"
const val TestTagTogetherResultCardPrefix = "TogetherResultCard-"

private val SlotDayFmt = DateTimeFormatter.ofPattern("EEE MMM d")
private val SlotTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Phase N.2 — ranked free-slot list. Stateless: takes the
 * pre-sorted slots and emits taps via [onSlotTap].
 */
@Composable
fun TogetherResultList(
    slots: List<TimeSlot>,
    onSlotTap: (TimeSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTagTogetherResultList)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.together_results_header, slots.size, slots.size),
            style = MaterialTheme.typography.titleMedium,
        )
        // Plain Column (not LazyColumn) because the parent pane is vertically
        // scrollable; topK is bounded to ~10 slots so list virtualization is
        // unnecessary.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.forEach { slot ->
                TogetherResultCard(slot = slot, onTap = { onSlotTap(slot) })
            }
        }
    }
}

@Composable
private fun TogetherResultCard(slot: TimeSlot, onTap: () -> Unit) {
    // Whole-card click + an inner Create-event button both invoke onTap.
    val totalMin = slot.lengthMillis / 60_000L
    val hours = (totalMin / 60).toInt()
    val mins = (totalMin % 60).toInt()
    val durationText = if (hours > 0) {
        stringResource(R.string.together_result_duration_hm, hours, mins)
    } else {
        stringResource(R.string.together_result_duration_m, mins)
    }
    Card(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$TestTagTogetherResultCardPrefix${slot.from}"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = slot.from.toLocalDate().format(SlotDayFmt),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = slot.from.toLocalTime().format(SlotTimeFmt) +
                    " – " +
                    slot.toExclusive.toLocalTime().format(SlotTimeFmt) +
                    "  ·  " + durationText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onTap) {
                Text(stringResource(R.string.together_result_create_event))
            }
        }
    }
}
