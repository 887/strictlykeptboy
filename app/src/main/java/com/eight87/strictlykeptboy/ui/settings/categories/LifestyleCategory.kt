package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.trip.TripFeed
import com.eight87.strictlykeptboy.ui.trip.TripSummary
import com.eight87.strictlykeptboy.ui.wizard.NeutralModePrefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val TestTagCatLifestyle = "Cat-Lifestyle"
const val TestTagCatLifestyleNeutralToggle = "Cat-Lifestyle-Neutral"
const val TestTagCatLifestyleTripUpcoming = "Cat-Lifestyle-Trip-Upcoming"
const val TestTagCatLifestyleTripLast = "Cat-Lifestyle-Trip-Last"
const val TestTagCatLifestyleTripNone = "Cat-Lifestyle-Trip-None"

/**
 * Phase S.8 — Lifestyle category. 2.1.E.4 — Neutral mode toggle
 * relocated from Appearance: it is a content-mode switch (K-1..K-7),
 * not a visual one. Appearance keeps a deeplink chip back here so the
 * keyword search still finds "neutral" / "kink".
 *
 * Sections:
 *  - Re-enter the lifestyle wizard at Screen 5 (Roles) per K.12 / LW-L.
 *  - **Phase CCC.10 / HV-G.1**: "+ Plan a trip" launches the quick-trip wizard.
 *  - 2.1.E.4 — Neutral mode toggle.
 */
@Composable
fun LifestyleCategory(
    onOpenWizardAtRoles: () -> Unit = {},
    onPlanTrip: () -> Unit = {},
    onOpenTrip: (TripSummary) -> Unit = {},
    modifier: Modifier = Modifier,
    neutralPrefs: NeutralModePrefs? = null,
    tripFeed: TripFeed? = null,
    nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    CategorySurface(
        testTag = TestTagCatLifestyle,
        title = stringResource(R.string.settings_lifestyle_title),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.settings_lifestyle_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenWizardAtRoles,
            modifier = Modifier.testTag("$TestTagCatLifestyle-OpenWizard"),
        ) {
            Text(stringResource(R.string.settings_lifestyle_open_wizard))
        }
        Spacer(Modifier.height(16.dp))

        // 2.2.D.13 — trip-summary card (above the "Plan a trip" CTA).
        TripSummaryCard(tripFeed = tripFeed, nowEpochMs = nowEpochMs, onOpenTrip = onOpenTrip)

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_lifestyle_plan_trip_blurb),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPlanTrip,
            modifier = Modifier.testTag("$TestTagCatLifestyle-PlanTrip"),
        ) {
            Text(stringResource(R.string.settings_lifestyle_plan_trip))
        }
        if (neutralPrefs != null) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_lifestyle_neutral_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.settings_lifestyle_neutral_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var neutralOn by remember { mutableStateOf(neutralPrefs.isEnabled()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_appearance_neutral_mode))
                Switch(
                    checked = neutralOn,
                    onCheckedChange = { neutralOn = it; neutralPrefs.setEnabled(it) },
                    modifier = Modifier.testTag(TestTagCatLifestyleNeutralToggle),
                )
            }
        }
    }
}

/**
 * 2.2.D.13 — three rows above the "Plan a trip" CTA: upcoming / last /
 * placeholder. Wired from a [TripFeed] which is empty by default until
 * Phase CCC ships a real trip resolver feed.
 */
@Composable
private fun TripSummaryCard(
    tripFeed: TripFeed?,
    nowEpochMs: () -> Long,
    onOpenTrip: (TripSummary) -> Unit,
) {
    val tripsState = tripFeed?.flow?.collectAsState()
    val trips: List<TripSummary> = tripsState?.value ?: emptyList()
    val now = nowEpochMs()
    val upcoming = tripFeed?.upcoming(now)
    val last = tripFeed?.last(now)
    if (tripFeed == null || trips.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTagCatLifestyleTripNone),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.settings_lifestyle_trip_none),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (upcoming != null) {
        TripRow(
            headerRes = R.string.settings_lifestyle_trip_upcoming,
            summary = upcoming,
            now = now,
            isUpcoming = true,
            onClick = { onOpenTrip(upcoming) },
            testTag = TestTagCatLifestyleTripUpcoming,
        )
        Spacer(Modifier.height(6.dp))
    }
    if (last != null) {
        TripRow(
            headerRes = R.string.settings_lifestyle_trip_last,
            summary = last,
            now = now,
            isUpcoming = false,
            onClick = { onOpenTrip(last) },
            testTag = TestTagCatLifestyleTripLast,
        )
    }
}

@Composable
private fun TripRow(
    headerRes: Int,
    summary: TripSummary,
    now: Long,
    isUpcoming: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(headerRes), style = MaterialTheme.typography.labelMedium)
            Text(summary.displayName, style = MaterialTheme.typography.titleSmall)
            val tail = if (isUpcoming) {
                val days = ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(),
                    Instant.ofEpochMilli(summary.startEpochMs).atZone(ZoneId.systemDefault()).toLocalDate(),
                )
                if (days <= 0L) stringResource(R.string.settings_lifestyle_trip_in_today)
                else stringResource(R.string.settings_lifestyle_trip_in_days, days.toInt())
            } else {
                Instant.ofEpochMilli(summary.endEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
            Text(
                text = tail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

